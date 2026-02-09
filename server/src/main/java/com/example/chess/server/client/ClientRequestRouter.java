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
import java.util.regex.Pattern;

final class ClientRequestRouter {

    private static final int MAX_TYPE_LENGTH = 32;
    private static final int MAX_CORR_ID_LENGTH = 128;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MIN_PASSWORD_LENGTH =
            Integer.parseInt(System.getProperty("chess.validation.password.min", "6"));
    private static final int MAX_PASSWORD_LENGTH =
            Integer.parseInt(System.getProperty("chess.validation.password.max", "128"));
    private static final int MAX_GAME_ID_LENGTH = 64;
    private static final int MAX_MOVE_LENGTH = 16;

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]*$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private final AuthService auth;
    private final GameCoordinator coordinator;
    private final MoveService moves;

    ClientRequestRouter(AuthService auth, GameCoordinator coordinator, MoveService moves) {
        this.auth = auth;
        this.coordinator = coordinator;
        this.moves = moves;
    }

    void handle(RequestMessage req, ClientHandler h) {
        if (!isValidType(req.type)) {
            h.send(ResponseMessage.error(req.corrId, "Invalid message type."));
            return;
        }
        if (!isValidCorrId(req.corrId)) {
            h.send(ResponseMessage.error(null, "Invalid corrId."));
            return;
        }

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
            h.send(ResponseMessage.error(corrId, "Internal server error."));
        }
    }

    void onDisconnect(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u == null) return;
        coordinator.onUserOffline(h, u);
    }

    private void register(RequestMessage req, ClientHandler h) {
        String username = requireUsername(req);
        String name = requireName(req);
        String password = requirePassword(req, true);

        UserModels.User user = auth.register(username, name, password);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.getUsername());
        u.put("name", user.getName());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        h.send(ResponseMessage.ok("registerOk", req.corrId, payload));
    }

    private void login(RequestMessage req, ClientHandler h) {
        String username = requireUsername(req);
        String password = requirePassword(req, false);

        UserModels.User user = auth.login(username, password);

        coordinator.onUserOnline(h, user);
        h.setCurrentUser(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userMap(user));

        h.send(ResponseMessage.ok("loginOk", req.corrId, payload));

        try {
            moves.tryReconnect(user, h);
        } catch (Exception e) {
            // don't fail login because of reconnect problems
            h.sendInfo("Logged in, but reconnect failed: " + e.getMessage());
        }
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
        String gameId = requireGameId(req);
        String move = requireMove(req);
        coordinator.makeMove(gameId, u, move);
        h.send(ResponseMessage.ok("makeMoveOk", req.corrId));
    }

    private void offerDraw(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = requireGameId(req);
        coordinator.offerDraw(gameId, u);
        h.send(ResponseMessage.ok("offerDrawOk", req.corrId));
    }

    private void respondDraw(RequestMessage req, ClientHandler h, boolean accept) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = requireGameId(req);
        coordinator.respondDraw(gameId, u, accept);
        h.send(ResponseMessage.ok(accept ? "acceptDrawOk" : "declineDrawOk", req.corrId));
    }

    private void resign(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = requireGameId(req);
        coordinator.resign(gameId, u);
        h.send(ResponseMessage.ok("resignOk", req.corrId));
    }

    private void listGames(RequestMessage req, ClientHandler h) {
        UserModels.User u = mustLogin(h);

        List<Game> games = coordinator.listGamesForUser(u.getUsername());

        List<Map<String, Object>> out = games.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("whiteUser", g.getWhiteUser());
            m.put("blackUser", g.getBlackUser());
            m.put("result", String.valueOf(g.getResult()));
            m.put("reason", g.getResultReason());
            m.put("createdAt", g.getCreatedAt());
            m.put("lastUpdate", g.getLastUpdate());

            String me = u.getUsername();
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

    private void getGameDetails(RequestMessage req, ClientHandler h) {
        UserModels.User u = mustLogin(h);
        String gameId = requireGameId(req);

        Game g = coordinator.getGameForUser(gameId, u.getUsername());
        if (g == null) throw new IllegalArgumentException("No such game (or you are not a participant).");

        Map<String, Object> payload = coordinator.toGameDetailsPayload(g);
        h.send(ResponseMessage.ok("getGameDetailsOk", req.corrId, payload));
    }

    private void getStats(RequestMessage req, ClientHandler h) {
        UserModels.User u = mustLogin(h);

        UserModels.User fresh = auth.getUser(u.getUsername());
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
        if (m == null || m.payload == null) throw new IllegalArgumentException("Missing payload.");
        Object v = m.payload.get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        if (!(v instanceof String)) throw new IllegalArgumentException("Expected string field: " + key);
        String s = ((String) v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Blank field: " + key);
        return s;
    }

    private static String reqStr(RequestMessage m, String key, int maxLen) {
        String s = reqStr(m, key);
        if (maxLen > 0 && s.length() > maxLen) throw new IllegalArgumentException("Field too long: " + key);
        return s;
    }

    private static String requireUsername(RequestMessage req) {
        String username = reqStr(req, "username", MAX_USERNAME_LENGTH);
        if (username.length() < MIN_USERNAME_LENGTH) throw new IllegalArgumentException("Username too short.");
        if (!USERNAME_PATTERN.matcher(username).matches())
            throw new IllegalArgumentException("Username has invalid characters.");
        return username;
    }

    private static String requireName(RequestMessage req) {
        String name = reqStr(req, "name", MAX_NAME_LENGTH);
        if (containsControl(name)) throw new IllegalArgumentException("Name contains invalid characters.");
        return name;
    }

    private static String requirePassword(RequestMessage req, boolean enforceMin) {
        String password = reqStr(req, "password", MAX_PASSWORD_LENGTH);
        if (enforceMin && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password too short.");
        }
        if (containsControl(password)) throw new IllegalArgumentException("Password contains invalid characters.");
        return password;
    }

    private static String requireGameId(RequestMessage req) {
        String gameId = reqStr(req, "gameId", MAX_GAME_ID_LENGTH);
        if (!GAME_ID_PATTERN.matcher(gameId).matches())
            throw new IllegalArgumentException("Invalid gameId.");
        return gameId;
    }

    private static String requireMove(RequestMessage req) {
        String move = reqStr(req, "move", MAX_MOVE_LENGTH);
        if (containsControl(move)) throw new IllegalArgumentException("Invalid move.");
        return move;
    }

    private static boolean isValidType(String type) {
        if (type == null) return false;
        String t = type.trim();
        if (t.isEmpty() || t.length() > MAX_TYPE_LENGTH) return false;
        if (!TYPE_PATTERN.matcher(t).matches()) return false;
        return !containsControl(t);
    }

    private static boolean isValidCorrId(String corrId) {
        if (corrId == null) return true;
        String c = corrId.trim();
        if (c.isEmpty() || c.length() > MAX_CORR_ID_LENGTH) return false;
        return !containsControl(c);
    }

    private static boolean containsControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) return true;
        }
        return false;
    }

    private static Map<String, Object> userMap(UserModels.User user) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", user.getUsername());
        u.put("name", user.getName());

        UserModels.Stats st = user.stats == null ? new UserModels.Stats() : user.stats;

        int derivedLost = st.getPlayed() - st.getWon() - st.getDrawn();
        if(derivedLost >= 0 && derivedLost != st.getLost()) {
            st.setLost(derivedLost); // repair lost counts
        }

        u.put("played", st.getPlayed());
        u.put("won", st.getWon());
        u.put("lost", st.getLost());
        u.put("drawn", st.getDrawn());
        u.put("rating", st.getRating());

        return u;
    }
}
