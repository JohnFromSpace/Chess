package com.example.chess.server.client;

import com.example.chess.common.UserModels;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.common.model.Game;
import com.example.chess.server.core.GameCoordinator;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GameRequestHandler {
    private final GameCoordinator coordinator;

    GameRequestHandler(GameCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    void requestGame(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        coordinator.requestGame(h, u);
        h.send(ResponseMessage.ok("requestGameOk", req.getCorrId()));
    }

    void makeMove(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = RequestValidator.requireGameId(req);
        String move = RequestValidator.requireMove(req);
        coordinator.makeMove(gameId, u, move);
        h.send(ResponseMessage.ok("makeMoveOk", req.getCorrId()));
    }

    void offerDraw(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = RequestValidator.requireGameId(req);
        coordinator.offerDraw(gameId, u);
        h.send(ResponseMessage.ok("offerDrawOk", req.getCorrId()));
    }

    void respondDraw(RequestMessage req, ClientHandler h, boolean accept) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = RequestValidator.requireGameId(req);
        coordinator.respondDraw(gameId, u, accept);
        h.send(ResponseMessage.ok(accept ? "acceptDrawOk" : "declineDrawOk", req.getCorrId()));
    }

    void resign(RequestMessage req, ClientHandler h) throws IOException {
        UserModels.User u = mustLogin(h);
        String gameId = RequestValidator.requireGameId(req);
        coordinator.resign(gameId, u);
        h.send(ResponseMessage.ok("resignOk", req.getCorrId()));
    }

    void listGames(RequestMessage req, ClientHandler h) {
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

        h.send(ResponseMessage.ok("listGamesOk", req.getCorrId(), payload));
    }

    void getGameDetails(RequestMessage req, ClientHandler h) {
        UserModels.User u = mustLogin(h);
        String gameId = RequestValidator.requireGameId(req);

        Game g = coordinator.getGameForUser(gameId, u.getUsername());
        if (g == null) throw new IllegalArgumentException("No such game (or you are not a participant).");

        Map<String, Object> payload = coordinator.toGameDetailsPayload(g);
        h.send(ResponseMessage.ok("getGameDetailsOk", req.getCorrId(), payload));
    }

    private static UserModels.User mustLogin(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u == null) throw new IllegalArgumentException("You must be logged in.");
        return u;
    }
}
