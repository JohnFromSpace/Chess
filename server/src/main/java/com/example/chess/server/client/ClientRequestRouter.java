package com.example.chess.server.client;

import com.example.chess.common.UserModels;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.common.model.Game;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.move.MoveService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClientRequestRouter {

    private final AuthService auth;
    private final GameCoordinator coordinator;
    private final MoveService moves;

    ClientRequestRouter(AuthService auth, GameCoordinator coordinator, MoveService moves) {
        this.auth = auth;
        this.coordinator = coordinator;
        this.moves = moves;
    }

    void handle(RequestMessage req, ClientHandler h) {
        String t = req.type;
        String corrId = req.corrId;

        try {
            switch (t) {
                case "ping" -> h.send(ResponseMessage.ok("pong", corrId));

                case "register" -> register(req, h);
                case "login" -> login(req, h);
                case "logout" -> logout(req, h);

                case "requestGame" -> requestGame(req, h);
                case "makeMove" -> makeMove(req, h);
                case "offerDraw" -> offerDraw(req, h);
                case "acceptDraw" -> respondDraw(req, h, true);
                case "declineDraw" -> respondDraw(req, h, false);
                case "resign" -> resign(req, h);

                case "listGames" -> listGames(req, h);
                case "getGameDetails" -> getGameDetails(req, h);
                case "getStats" -> getStats(req, h);

                default -> h.send(ResponseMessage.error(corrId, "Unknown message type: " + t));
            }
        } catch (IllegalArgumentException ex) {
            h.send(ResponseMessage.error(corrId, ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            h.send(ResponseMessage.error(corrId, "Internal server error."));
        }
    }

    void onDisconnect(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        coordinator.onUserOffline(h, u);
    }

    private void register(RequestMessage req, ClientHandler h) {
        String username = reqStr(req, "username");
        String name = reqStr(req, "name");
        String password = reqStr(req, "password");

        UserModels.User user = auth.register(username, name, password);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        h.send(ResponseMessage.ok("registerOk", req.corrId, payload));
    }

    private void login(RequestMessage req, ClientHandler h) {
        String username = reqStr(req, "username");
        String password = reqStr(req, "password");

        UserModels.User user = auth.login(username, password);

        coordinator.onUserOnline(h, user);
        h.setCurrentUser(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userMap(user));

        h.send(ResponseMessage.ok("loginOk", req.corrId, payload));

        moves.tryReconnect(user, h);
    }

    private void logout(RequestMessage req, ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u != null) coordinator.onUserLogout(h, u);
        h.setCurrentUser(null);
        h.send(ResponseMessage.ok("logoutOk", req.corrId));
    }

    private void requestGame(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        coordinator.requestGame(h, u);
        h.send(ResponseMessage.ok("requestGameOk", req.corrId));
    }

    private void makeMove(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = reqStr(req, "gameId");
        String move = reqStr(req, "move");
        coordinator.makeMove(gameId, u, move);
        h.send(ResponseMessage.ok("makeMoveOk", req.corrId));
    }

    private void offerDraw(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = reqStr(req, "gameId");
        coordinator.offerDraw(gameId, u);
        h.send(ResponseMessage.ok("offerDrawOk", req.corrId));
    }

    private void respondDraw(RequestMessage req, ClientHandler h, boolean accept) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = reqStr(req, "gameId");
        coordinator.respondDraw(gameId, u, accept);
        h.send(ResponseMessage.ok(accept ? "acceptDrawOk" : "declineDrawOk", req.corrId));
    }

    private void resign(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = reqStr(req, "gameId");
        coordinator.resign(gameId, u);
        h.send(ResponseMessage.ok("resignOk", req.corrId));
    }

    private void listGames(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);

        List<Game> games = coordinator.listGamesForUser(u.username);

        List<Map<String, Object>> out = games.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("whiteUser", g.getWhiteUser());
            m.put("blackUser", g.getBlackUser());
            m.put("result", String.valueOf(g.getResult()));
            m.put("reason", g.getResultReason());
            m.put("createdAt", g.getCreatedAt());
            m.put("lastUpdate", g.getLastUpdate());

            String me = u.username;
            String opponent = me.equals(g.getWhiteUser()) ? g.getBlackUser() : g.getWhiteUser();
            String color = me.equals(g.getWhiteUser()) ? "WHITE" : "BLACK";
            m.put("opponent", opponent);
            m.put("youAre", color);
            return m;
        }).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("games", out);

        h.send(ResponseMessage.ok("listGamesOk", req.corrId, payload));
    }

    private void getGameDetails(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = reqStr(req, "gameId");

        Game g = coordinator.getGameForUser(gameId, u.username);
        if (g == null) throw new IllegalArgumentException("No such game (or you are not a participant).");

        Map<String, Object> payload = coordinator.toGameDetailsPayload(g);
        h.send(ResponseMessage.ok("getGameDetailsOk", req.corrId, payload));
    }

    private void getStats(RequestMessage req, ClientHandler h) {
        UserModels.User u = mustLogin(h);

        UserModels.User fresh = auth.getUser(u.username);
        h.setCurrentUser(fresh);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userMap(fresh));

        h.send(ResponseMessage.ok("getStatsOk", req.corrId, payload));
    }

    private static UserModels.User mustLogin(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u == null) throw new IllegalArgumentException("You must be logged in.");
        return u;
    }

    private static String reqStr(RequestMessage m, String key) {
        Object v = m.payload.get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Blank field: " + key);
        return s;
    }

    private static Map<String, Object> userMap(UserModels.User user) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);

        UserModels.Stats st = user.stats == null ? new UserModels.Stats() : user.stats;
        u.put("played", st.played);
        u.put("won", st.won);
        u.put("lost", st.lost);
        u.put("drawn", st.drawn);
        u.put("rating", st.rating);
        return u;
    }
}