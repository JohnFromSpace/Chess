package com.example.chess.server;

import com.example.chess.common.MessageCodec;
import com.example.chess.common.UserModels.User;
import com.example.chess.common.proto.Message;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;

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
            if(currentUser != null) {
                gameCoordinator.onUserOffline(this, currentUser);
            }
        }
    }

    private void handleLine(String line) {
        Message msg;
        try {
            msg = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            sendError(null, "Invalid message: " + e.getMessage());
            return;
        }

        String type = msg.type;
        String corrId = msg.corrId;

        try {
            switch (type) {
                case "ping" -> handlePing(msg);
                case "register" -> handleRegister(msg);
                case "login" -> handleLogin(msg);
                case "requestGame" -> handleRequestGame(msg);
                case "makeMove" -> handleMakeMove(msg);
                case "offerDraw" -> handleOfferDraw(msg);
                case "acceptDraw" -> handleRespondDraw(msg, true);
                case "declineDraw" -> handleRespondDraw(msg, false);
                case "resign" -> handleResign(msg);
                default -> sendError(corrId, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException ex) {
            // business validation errors
            sendError(corrId, ex.getMessage());
        } catch (Exception ex) {
            sendError(corrId, "Internal server error.");
        }
    }

    private void handlePing(Message inMsg) {
        Message outMsg = Message.of("pong", inMsg.corrId);
    }

    private void handleRegister(Message inMsg) {
        String username = inMsg.getRequiredString("username");
        String name     = inMsg.getRequiredString("name");
        String password = inMsg.getRequiredString("password");

        User user = authService.register(username, name, password);

        Message out = Message.of("registerOk", inMsg.corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        out.put("user", u);
        send(out);
    }

    private void handleLogin(Message inMsg) {
        String username = inMsg.getRequiredString("username");
        String password = inMsg.getRequiredString("password");

        User user = authService.login(username, password);
        this.currentUser = user;

        gameCoordinator.onUserOnline(this, user);

        Message out = Message.of("loginOk", inMsg.corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        u.addProperty("played", user.stats.played);
        u.addProperty("won", user.stats.won);
        u.addProperty("rating", user.stats.rating);
        out.put("user", u);
        send(out);
    }

    private void handleRequestGame(Message inMsg) {
        if (currentUser == null) {
            throw new IllegalArgumentException("You must be logged in to request a game.");
        }

        gameCoordinator.requestGame(this, currentUser);

        Message out = Message.of("requestGameOk", inMsg.corrId)
                .put("status", "queueOrMatched");
        send(out);
    }

    private void handleMakeMove(Message inMsg) {
        if (currentUser == null) {
            throw new IllegalArgumentException("You must be logged in to make a move.");
        }

        String gameId = inMsg.getRequiredString("gameId");
        String move   = inMsg.getRequiredString("move");

        gameCoordinator.makeMove(gameId, currentUser, move);

        Message out = Message.of("makeMoveOk", inMsg.corrId);
        send(out);
    }

    private void handleOfferDraw(Message inMsg) {
        if (currentUser == null) {
            throw new IllegalArgumentException("You must be logged in to offer a draw.");
        }

        String gameId = inMsg.getRequiredString("gameId");
        gameCoordinator.offerDraw(gameId, currentUser);

        Message out = Message.of("offerDrawOk", inMsg.corrId);
        send(out);
    }

    private void handleRespondDraw(Message inMsg, boolean accept) {
        if (currentUser == null) {
            throw new IllegalArgumentException("You must be logged in.");
        }

        String gameId = inMsg.getRequiredString("gameId");
        gameCoordinator.respondDraw(gameId, currentUser, accept);

        Message out = Message.of(accept ? "acceptDrawOk" : "declineDrawOk",
                inMsg.corrId);
        send(out);
    }

    private void handleResign(Message inMsg) {
        if (currentUser == null) {
            throw new IllegalArgumentException("You must be logged in.");
        }

        String gameId = inMsg.getRequiredString("gameId");
        gameCoordinator.resign(gameId, currentUser);

        Message out = Message.of("resignOk", inMsg.corrId);
        send(out);
    }

    private void sendError(String corrId, String message) {
        Message m = Message.of("error", corrId)
                .put("message", message);
        send(m);
    }

    private synchronized void send(Message m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            out.write(line);
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    void onGameStarted(com.example.chess.common.GameModels.Game game, boolean isWhite) {
        Message m = Message.of("gameStarted", null)
                .put("gameId", game.id)
                .put("color", isWhite ? "white" : "black")
                .put("opponent", isWhite ? game.blackUser : game.whiteUser)
                .put("timeControlMs", (int) game.timeControlMs)
                .put("incrementMs", (int) game.incrementMs);
        send(m);
    }

    void sendMove(Game game, String moveStr, boolean whiteInCheck, boolean blackInCheck) {
        Message m = Message.of("move", null)
                .put("gameId", game.id)
                .put("move", moveStr)
                .put("whiteInCheck", whiteInCheck)
                .put("blackInCheck", blackInCheck);
        send(m);
    }

    void sendGameOver(Game game, boolean statsOk) {
        Message m = Message.of("gameOver", null)
                .put("gameId", game.id)
                .put("result", game.result.name())
                .put("reason", game.resultReason != null ? game.resultReason : "");
        send(m);

        if(!statsOk) {
            System.err.println("Game finished, but stats could not be updated on the server.");
        }
    }

    void sendDrawOffered(String gameId, String byUser) {
        Message m = Message.of("drawOffered", null)
                .put("gameId", gameId)
                .put("by", byUser);
        send(m);
    }

    void sendDrawDeclined(String gameId, String byUser) {
        Message m = Message.of("drawDeclined", null)
                .put("gameId", gameId)
                .put("by", byUser);
        send(m);
    }

    void sendInfo(String message) {
        Message m = Message.of("info", null)
                .put("message", message);
        send(m);
    }
}

