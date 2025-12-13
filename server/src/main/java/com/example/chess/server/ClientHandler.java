package com.example.chess.server;

import com.example.chess.common.MessageCodec;
import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final GameCoordinator gameCoordinator;

    private BufferedReader in;
    private BufferedWriter out;
    private User currentUser;

    public ClientHandler(Socket socket, AuthService authService, GameCoordinator gameCoordinator) {
        this.socket = socket;
        this.authService = authService;
        this.gameCoordinator = gameCoordinator;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                handleLine(line);
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
        } finally {
            if (currentUser != null) {
                gameCoordinator.onUserOffline(this, currentUser);
            }
        }
    }

    private void handleLine(String line) {
        Message parsed;
        try {
            parsed = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage()));
            return;
        }

        if (!(parsed instanceof RequestMessage msg)) {
            send(ResponseMessage.error(null, "Client must send request messages."));
            return;
        }

        String type = msg.type;
        String corrId = msg.corrId;

        try {
            switch (type) {
                case "ping"        -> handlePing(msg);
                case "register"    -> handleRegister(msg);
                case "login"       -> handleLogin(msg);
                case "requestGame" -> handleRequestGame(msg);
                case "makeMove"    -> handleMakeMove(msg);
                case "offerDraw"   -> handleOfferDraw(msg);
                case "acceptDraw"  -> handleRespondDraw(msg, true);
                case "declineDraw" -> handleRespondDraw(msg, false);
                case "resign"      -> handleResign(msg);
                default -> send(ResponseMessage.error(corrId, "Unknown message type: " + type));
            }
        } catch (IllegalArgumentException ex) {
            send(ResponseMessage.error(corrId, ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            send(ResponseMessage.error(corrId, "Internal server error."));
        }
    }

    private static String reqStr(RequestMessage m, String key) {
        Object v = m.payload.get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Blank field: " + key);
        return s;
    }

    private void handlePing(RequestMessage inMsg) {
        send(ResponseMessage.ok("pong", inMsg.corrId));
    }

    private void handleRegister(RequestMessage inMsg) {
        String username = reqStr(inMsg, "username");
        String name     = reqStr(inMsg, "name");
        String password = reqStr(inMsg, "password");

        User user = authService.register(username, name, password);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        send(ResponseMessage.ok("registerOk", inMsg.corrId, payload));
    }

    private void handleLogin(RequestMessage inMsg) {
        String username = reqStr(inMsg, "username");
        String password = reqStr(inMsg, "password");

        User user = authService.login(username, password);
        this.currentUser = user;

        gameCoordinator.onUserOnline(this, user);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);
        u.put("played", user.stats.played);
        u.put("won", user.stats.won);
        u.put("drawn", user.stats.drawn);
        u.put("rating", user.stats.rating);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        send(ResponseMessage.ok("loginOk", inMsg.corrId, payload));
    }

    private void handleRequestGame(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in to request a game.");
        gameCoordinator.requestGame(this, currentUser);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "queueOrMatched");
        send(ResponseMessage.ok("requestGameOk", inMsg.corrId, payload));
    }

    private void handleMakeMove(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in to make a move.");
        String gameId = reqStr(inMsg, "gameId");
        String move   = reqStr(inMsg, "move");

        gameCoordinator.makeMove(gameId, currentUser, move);
        send(ResponseMessage.ok("makeMoveOk", inMsg.corrId));
    }

    private void handleOfferDraw(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in to offer a draw.");
        String gameId = reqStr(inMsg, "gameId");

        gameCoordinator.offerDraw(gameId, currentUser);
        send(ResponseMessage.ok("offerDrawOk", inMsg.corrId));
    }

    private void handleRespondDraw(RequestMessage inMsg, boolean accept) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");

        gameCoordinator.respondDraw(gameId, currentUser, accept);
        send(ResponseMessage.ok(accept ? "acceptDrawOk" : "declineDrawOk", inMsg.corrId));
    }

    private void handleResign(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");

        gameCoordinator.resign(gameId, currentUser);
        send(ResponseMessage.ok("resignOk", inMsg.corrId));
    }

    void sendGameStarted(Game game, boolean isWhite, boolean resumed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("color", isWhite ? "white" : "black");
        payload.put("opponent", isWhite ? game.blackUser : game.whiteUser);

        payload.put("timeControlMs", game.timeControlMs);
        payload.put("incrementMs", game.incrementMs);

        payload.put("resumed", resumed);
        payload.put("whiteTimeMs", game.whiteTimeMs);
        payload.put("blackTimeMs", game.blackTimeMs);
        payload.put("whiteToMove", game.whiteMove);

        send(ResponseMessage.push("gameStarted", payload));
    }

    void sendMove(Game game, String moveStr, boolean whiteInCheck, boolean blackInCheck) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("move", moveStr);
        payload.put("whiteInCheck", whiteInCheck);
        payload.put("blackInCheck", blackInCheck);
        send(ResponseMessage.push("move", payload));
    }

    void sendGameOver(Game game, boolean statsOk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("result", game.result.name());
        payload.put("reason", game.resultReason != null ? game.resultReason : "");
        payload.put("statsOk", statsOk);
        send(ResponseMessage.push("gameOver", payload));
    }

    void sendDrawOffered(String gameId, String byUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("by", byUser);
        send(ResponseMessage.push("drawOffered", payload));
    }

    void sendDrawDeclined(String gameId, String byUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("by", byUser);
        send(ResponseMessage.push("drawDeclined", payload));
    }

    void sendInfo(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        send(ResponseMessage.push("info", payload));
    }

    private void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            synchronized (out) {
                out.write(line);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }
}