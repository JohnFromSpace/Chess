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
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;

    private final ClientRequestRouter router;
    private final ClientNotifier notifier = new ClientNotifier();
    private final RateLimiter inboundLimiter;

    private BufferedReader in;
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
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            int readTimeoutMs =  Integer.parseInt(System.getProperty("chess.socket.readTimeoutMs", "60000"));
            if (readTimeoutMs > 0) socket.setSoTimeout(readTimeoutMs);

            String line;
            while ((line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (Exception e) {
            Log.warn("Client disconnected / handler error", e);
        } finally {
            try { router.onDisconnect(this); }
            catch (Exception e) { Log.warn("onDisconnect failed", e); }
        }
    }

    private void handleLine(String line) throws IOException {
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty()) return;

        Message parsed;
        try {
            parsed = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage(), "Too many requests. PLease, slow down."));
            return;
        }

        if (!(parsed instanceof RequestMessage req)) {
            send(ResponseMessage.error(null, "Client must send request messages.", "Too many requests. PLease, slow down."));
            return;
        }

        if(inboundLimiter != null && !inboundLimiter.tryAcquire()) {
            send(ResponseMessage.error(req.corrId, "rate_limited", "Too many requests. PLease, slow down."));
            return;
        }

        router.handle(req, this);
    }

    public void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            out.write(line);
            out.flush();
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
}
