package com.example.chess.server.client;

import com.example.chess.common.message.MessageCodec;
import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.message.Message;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.util.Log;
import com.example.chess.server.security.RateLimiter;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;

    private final ClientRequestRouter router;
    private final ClientNotifier notifier = new ClientNotifier();
    private final RateLimiter inboundLimiter;
    private final Object writeLock = new Object();

    private BufferedWriter out;

    private volatile UserModels.User currentUser;

    public ClientHandler(Socket socket, AuthService auth, GameCoordinator coordinator, MoveService moves) {
        this.socket = socket;
        this.router = new ClientRequestRouter(auth, coordinator, moves);

        boolean rlEnabled = Boolean.parseBoolean(System.getProperty("chess.ratelimit.enabled", "true"));
        long rlCapacity = Long.getLong("chess.ratelimit.capacity", 30L);
        long rlRefillSeconds = Long.getLong("chess.ratelimit.refillPerSecond", 15L);
        this.inboundLimiter = rlEnabled ? new RateLimiter((int) rlCapacity, rlRefillSeconds) : null;
    }

    public UserModels.User getCurrentUser() { return currentUser; }
    public void setCurrentUser(UserModels.User u) { this.currentUser = u; }

    @Override
    public void run() {
        try (socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            int readTimeoutMs =  Integer.parseInt(System.getProperty("chess.socket.readTimeoutMs", "60000"));
            if (readTimeoutMs > 0) socket.setSoTimeout(readTimeoutMs);

            int maxLineChars = Integer.parseInt(System.getProperty("chess.socket.maxLineChars", "16384"));

            String line;
            try {
                while ((line = readLineLimited(in, maxLineChars)) != null) {
                    handleLine(line);
                }
            } catch (LineTooLongException e) {
                send(ResponseMessage.error(null, "Request too large."));
            }
        } catch (Exception e) {
            Log.warn("Client disconnected / handler error", e);
        } finally {
            try { router.onDisconnect(this); }
            catch (Exception e) { Log.warn("onDisconnect failed", e); }
        }
    }

    private void handleLine(String line) {
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty()) return;

        Message parsed;
        try {
            parsed = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage()));
            return;
        }

        if (!(parsed instanceof RequestMessage req)) {
            send(ResponseMessage.error(null, "Client must send request messages."));
            return;
        }

        if(inboundLimiter != null && !inboundLimiter.tryAcquire()) {
            send(ResponseMessage.error(req.corrId, "rate_limited"));
            return;
        }

        router.handle(req, this);
    }

    public void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            synchronized (writeLock) {
                if (out == null) return;
                out.write(line);
                out.flush();
            }
        } catch (Exception e) {
            Log.warn("Failed to send response to client", e);
        }
    }

    public void sendInfo(String message) {
        send(ResponseMessage.push("info", Map.of("message", message)));
    }

    public void pushGameStarted(Game g, boolean isWhite) {
        notifier.gameStarted(this, g, isWhite);
    }

    public void pushMove(Game g, String by, String move, boolean wChk, boolean bChk) {
        notifier.move(this, g, by, move, wChk, bChk);
    }

    public void pushGameOver(Game g, boolean statsOk) {
        notifier.gameOver(this, g, statsOk);
    }

    public void pushDrawOffered(String gameId, String by) {
        notifier.drawOffered(this, gameId, by);
    }

    public void pushDrawDeclined(String gameId, String by) {
        notifier.drawDeclined(this, gameId, by);
    }

    private static String readLineLimited(BufferedReader in, int maxChars) throws IOException {
        if (in == null) return null;
        int limit = Math.max(1, maxChars);
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = in.read();
            if (ch == -1) {
                return sb.isEmpty() ? null : sb.toString();
            }
            if (ch == '\n') {
                return sb.toString();
            }
            if (ch == '\r') {
                if (in.markSupported()) {
                    in.mark(1);
                    int next = in.read();
                    if (next != '\n' && next != -1) {
                        in.reset();
                    }
                }
                return sb.toString();
            }
            sb.append((char) ch);
            if (sb.length() > limit) {
                throw new LineTooLongException("Incoming line exceeds limit: " + limit);
            }
        }
    }

    private static final class LineTooLongException extends IOException {
        private LineTooLongException(String message) {
            super(message);
        }
    }
}
