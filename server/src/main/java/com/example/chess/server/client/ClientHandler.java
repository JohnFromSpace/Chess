package com.example.chess.server.client;

import com.example.chess.common.MessageCodec;
import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.MoveService;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService auth;
    private final GameCoordinator coordinator;

    private final ClientRequestRouter router;
    private final ClientNotifier notifier = new ClientNotifier();

    private BufferedReader in;
    private BufferedWriter out;

    private volatile UserModels.User currentUser;

    public ClientHandler(Socket socket, AuthService auth, GameCoordinator coordinator, MoveService moves) {
        this.socket = socket;
        this.auth = auth;
        this.coordinator = coordinator;
        this.router = new ClientRequestRouter(auth, coordinator, moves);
    }

    public UserModels.User getCurrentUser() { return currentUser; }
    public void setCurrentUser(UserModels.User u) { this.currentUser = u; }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (Exception ignored) {
            // client disconnected / network error
        } finally {
            try { router.onDisconnect(this); } catch (Exception ignored) {}
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
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage()));
            return;
        }

        if (!(parsed instanceof RequestMessage req)) {
            send(ResponseMessage.error(null, "Client must send request messages."));
            return;
        }

        router.handle(req, this);
    }

    public void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            synchronized (this) {
                out.write(line);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    public void sendInfo(String message) {
        send(ResponseMessage.push("info", java.util.Map.of("message", message)));
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