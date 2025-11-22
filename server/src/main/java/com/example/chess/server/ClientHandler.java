package com.example.chess.server;

import com.example.chess.common.GameModels.Game;
import com.example.chess.common.Msg;
import com.example.chess.common.UserModels.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final GameCoordinator gameCoordinator;
    private final Gson gson = new Gson();

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
            // client disconnected or IO problem
            // just end the thread
        } finally {
            if(currentUser != null) {
                gameCoordinator.onUserOffline(this, currentUser);
            }
        }
    }

    private void handleLine(String line) {
        JsonObject msg;
        try {
            msg = gson.fromJson(line, JsonObject.class);
        } catch (Exception e) {
            sendError(null, "Invalid JSON.");
            return;
        }
        if (msg == null || !msg.has("type")) {
            sendError(null, "Missing 'type' field.");
            return;
        }

        String type = msg.get("type").getAsString();
        String corrId = msg.has("corrId") ? msg.get("corrId").getAsString() : null;

        try {
            switch (type) {
                case "ping" -> handlePing(corrId);
                case "register" -> handleRegister(corrId, msg);
                case "login" -> handleLogin(corrId, msg);
                case "requestGame" -> handleRequestGame(corrId, msg);
                case "move" -> handleMove(corrId, msg);
                case "offerDraw" -> handleOfferDraw(corrId, msg);
                case "respondDraw" -> handleRespondDraw(corrId, msg);
                case "resign" -> handleResign(corrId, msg);
                case "getStats" -> handleGetStats(corrId);
                case "listGames" -> handleListGames(corrId);
                case "getGameDetails" -> handleGetGameDetails(corrId, msg);
                default -> sendError(corrId, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException ex) {
            // business validation errors
            sendError(corrId, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(corrId, "Internal server error.");
        }
    }

    private void handlePing(String corrId) {
        JsonObject o = Msg.obj("pong", corrId);
        send(o);
    }

    private void handleRegister(String corrId, JsonObject msg) {
        String username = getRequiredString(msg, "username");
        String name = getRequiredString(msg, "name");
        String password = getRequiredString(msg, "password");

        User user = authService.register(username, name, password);

        JsonObject o = Msg.obj("registerOk", corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        o.add("user", u);
        send(o);
    }

    private void handleLogin(String corrId, JsonObject msg) {
        String username = getRequiredString(msg, "username");
        String password = getRequiredString(msg, "password");

        User user = authService.login(username, password);
        this.currentUser = user;

        gameCoordinator.onUserOnline(this, user);

        JsonObject o = Msg.obj("loginOk", corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        u.addProperty("played", user.stats.played);
        u.addProperty("won", user.stats.won);
        u.addProperty("rating", user.stats.rating);
        o.add("user", u);
        send(o);
    }

    void handleRequestGame(String corrId, JsonObject msg) {
        if(currentUser == null) {
            throw new IllegalArgumentException("You must be logged in to request a game.");
        }

        gameCoordinator.requestGame(this, currentUser);

        JsonObject o = Msg.obj("requestGameOk", corrId);
        o.addProperty("status", "queueOrMatched");
        send(o);
    }

    private void handleMove(String corrId, JsonObject msg) {
        String gameId = getRequiredString(msg, "gameId");
        String moveStr = getRequiredString(msg, "move");

        if(currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        gameCoordinator.makeMove(gameId, currentUser, moveStr);

        JsonObject o = Msg.obj("moveOk", corrId);
        send(o);
    }

    private String getRequiredString(JsonObject obj, String field) {
        if (!obj.has(field)) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return obj.get(field).getAsString();
    }

    private void sendError(String corrId, String message) {
        JsonObject o = Msg.obj("error", corrId);
        o.addProperty("message", message);
        send(o);
    }

    private synchronized void send(JsonObject o) {
        try {
            String line = Msg.jsonLine(o);
            out.write(line);
            out.flush();
        } catch (IOException e) {
            // client is disconnected
        }
    }

    void onGameStarted(com.example.chess.common.GameModels.Game game, boolean isWhite) {
        JsonObject o = Msg.obj("gameStarted", null);
        o.addProperty("gameId", game.id);
        o.addProperty("color", isWhite ? "white" : "black");
        o.addProperty("opponent", isWhite ? game.blackUser : game.whiteUser);
        o.addProperty("timeControlMs", game.timeControlMs);
        o.addProperty("incrementMs", game.incrementMs);
        send(o);
    }

    void sendInfo(String message) {
        JsonObject o = Msg.obj("info", null);
        o.addProperty("message", message);
        send(o);
    }

    void sendMove(Game game, String move, boolean whiteInCheck, boolean blackInCheck) {
        JsonObject o = Msg.obj("move", null);
        o.addProperty("gameId", game.id);
        o.addProperty("move", move);
        o.addProperty("whiteToMove", game.whiteUser);
        o.addProperty("whiteTimeMs", game.whiteTimeMs);
        o.addProperty("blackTimeMs", game.blackTimeMs);
        o.addProperty("whiteInCheck", whiteInCheck);
        o.addProperty("blackInCheck", blackInCheck);

        send(o);
    }

    private void handleOfferDraw(String corrId, JsonObject msg) {
        String gameId = getRequiredString(msg, "gameId");
        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        gameCoordinator.offerDraw(gameId, currentUser);

        JsonObject o = Msg.obj("offerDrawOk", corrId);
        send(o);
    }

    private void handleRespondDraw(String corrId, JsonObject msg) {
        String gameId = getRequiredString(msg, "gameId");
        boolean accepted = msg.has("accepted") && msg.get("accepted").getAsBoolean();

        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        gameCoordinator.respondDraw(gameId, currentUser, accepted);

        JsonObject o = Msg.obj("respondDrawOk", corrId);
        send(o);
    }

    private void handleResign(String corrId, JsonObject msg) {
        String gameId = getRequiredString(msg, "gameId");
        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        gameCoordinator.resign(gameId, currentUser);

        JsonObject o = Msg.obj("resignOk", corrId);
        send(o);
    }

    void sendDrawOffered(String gameId, String fromUser) {
        JsonObject o = Msg.obj("drawOffered", null);
        o.addProperty("gameId", gameId);
        o.addProperty("from", fromUser);

        send(o);
    }

    void sendDrawDeclined(String gameId, String byUser) {
        JsonObject o = Msg.obj("drawDeclined", null);
        o.addProperty("gameId", gameId);
        o.addProperty("by", byUser);

        send(o);
    }

    void sendGameOver(Game game) {
        JsonObject o = Msg.obj("gameOver", null);
        o.addProperty("gameId", game.id);
        o.addProperty("result", game.result.toString());
        o.addProperty("reason", game.resultReason);
        o.addProperty("whiteTimeMs", game.whiteTimeMs);
        o.addProperty("blackTimeMs", game.blackTimeMs);

        send(o);
    }

    private void handleGetStats(String corrId) {
        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        JsonObject o = Msg.obj("getStatsOk", corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", currentUser.username);
        u.addProperty("name", currentUser.name);
        u.addProperty("played", currentUser.stats.played);
        u.addProperty("won", currentUser.stats.won);
        u.addProperty("drawn", currentUser.stats.drawn);
        u.addProperty("rating", currentUser.stats.rating);
        o.add("user", u);

        send(o);
    }

    private void handleListGames(String corrId) {
        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        java.util.List<Game> games = gameCoordinator.listGamesForUser(currentUser.username);

        JsonObject o = Msg.obj("listGamesOk", corrId);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();

        for (Game g : games) {
            JsonObject jg = new JsonObject();
            jg.addProperty("id", g.id);
            jg.addProperty("createdAt", g.createdAt);
            String opponent =
                    currentUser.username.equals(g.whiteUser) ? g.blackUser : g.whiteUser;
            jg.addProperty("opponent", opponent);
            String color =
                    currentUser.username.equals(g.whiteUser) ? "white" : "black";
            jg.addProperty("color", color);
            jg.addProperty("result", g.result.toString());
            jg.addProperty("reason", g.resultReason);
            arr.add(jg);
        }

        o.add("games", arr);
        send(o);
    }

    private void handleGetGameDetails(String corrId, JsonObject msg) {
        if (currentUser == null) {
            throw new IllegalArgumentException("Not logged in.");
        }

        String gameId = getRequiredString(msg, "gameId");
        Game g = gameCoordinator.loadGamesById(gameId);
        if (g == null) {
            throw new IllegalArgumentException("Game not found.");
        }

        if (!currentUser.username.equals(g.whiteUser) && !currentUser.username.equals(g.blackUser)) {
            throw new IllegalArgumentException("You are not part of this game.");
        }

        JsonObject o = Msg.obj("gameDetailsOk", corrId);
        JsonObject gg = new JsonObject();
        gg.addProperty("id", g.id);
        gg.addProperty("whiteUser", g.whiteUser);
        gg.addProperty("blackUser", g.blackUser);
        gg.addProperty("createdAt", g.createdAt);
        gg.addProperty("result", g.result.toString());
        gg.addProperty("reason", g.resultReason == null ? "" : g.resultReason);

        com.google.gson.JsonArray movesArr = new com.google.gson.JsonArray();
        if (g.moves != null) {
            for (String m : g.moves) {
                movesArr.add(m);
            }
        }
        gg.add("moves", movesArr);

        o.add("game", gg);
        send(o);
    }
}

