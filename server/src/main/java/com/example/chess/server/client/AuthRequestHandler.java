package com.example.chess.server.client;

import com.example.chess.common.UserModels;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.util.Log;

import java.util.HashMap;
import java.util.Map;

final class AuthRequestHandler {
    private final AuthService auth;
    private final GameCoordinator coordinator;
    private final MoveService moves;

    AuthRequestHandler(AuthService auth, GameCoordinator coordinator, MoveService moves) {
        this.auth = auth;
        this.coordinator = coordinator;
        this.moves = moves;
    }

    void register(RequestMessage req, ClientHandler h) {
        String username = RequestValidator.requireUsername(req);
        String name = RequestValidator.requireName(req);
        String password = RequestValidator.requirePassword(req, true);

        UserModels.User user = auth.register(username, name, password);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.getUsername());
        u.put("name", user.getName());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        h.send(ResponseMessage.ok("registerOk", req.getCorrId(), payload));
    }

    void login(RequestMessage req, ClientHandler h) {
        String username = RequestValidator.requireUsername(req);
        String password = RequestValidator.requirePassword(req, false);

        UserModels.User user = auth.login(username, password);

        coordinator.onUserOnline(h, user);
        h.setCurrentUser(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userMap(user));

        h.send(ResponseMessage.ok("loginOk", req.getCorrId(), payload));

        try {
            moves.tryReconnect(user, h);
        } catch (Exception e) {
            // don't fail login because of reconnect problems
            Log.warn("Reconnect attempt failed for user " + user.getUsername(), e);
            h.sendInfo("Logged in, but reconnect failed: " + e.getMessage());
        }
    }

    void logout(RequestMessage req, ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u != null) coordinator.onUserLogout(h, u);
        h.setCurrentUser(null);
        h.send(ResponseMessage.ok("logoutOk", req.getCorrId()));
    }

    void getStats(RequestMessage req, ClientHandler h) {
        UserModels.User u = requireUser(h);

        UserModels.User fresh = auth.getUser(u.getUsername());
        h.setCurrentUser(fresh);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userMap(fresh));

        h.send(ResponseMessage.ok("getStatsOk", req.getCorrId(), payload));
    }

    private static UserModels.User requireUser(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u == null) throw new IllegalArgumentException("You must be logged in.");
        return u;
    }

    private static Map<String, Object> userMap(UserModels.User user) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", user.getUsername());
        u.put("name", user.getName());

        UserModels.Stats st = user.getStats();
        if (st == null) {
            st = new UserModels.Stats();
            user.setStats(st);
        }

        int derivedLost = st.getPlayed() - st.getWon() - st.getDrawn();
        if (derivedLost >= 0 && derivedLost != st.getLost()) {
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
