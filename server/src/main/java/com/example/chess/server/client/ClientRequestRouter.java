package com.example.chess.server.client;

import com.example.chess.common.UserModels;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.util.Log;
import com.example.chess.server.util.ServerMetrics;

import java.util.HashMap;
import java.util.Map;

final class ClientRequestRouter {

    private final GameCoordinator coordinator;
    private final ServerMetrics metrics;
    private final AuthRequestHandler authHandler;
    private final GameRequestHandler gameHandler;

    ClientRequestRouter(AuthService auth, GameCoordinator coordinator, MoveService moves, ServerMetrics metrics) {
        this.coordinator = coordinator;
        this.metrics = metrics;
        this.authHandler = new AuthRequestHandler(auth, coordinator, moves);
        this.gameHandler = new GameRequestHandler(coordinator);
    }

    void handle(RequestMessage req, ClientHandler h) {
        if (!RequestValidator.isValidType(req.getType())) {
            if (metrics != null) metrics.onInvalidRequest();
            h.send(ResponseMessage.error(req.getCorrId(), "Invalid message type."));
            return;
        }
        if (!RequestValidator.isValidCorrId(req.getCorrId())) {
            if (metrics != null) metrics.onInvalidRequest();
            h.send(ResponseMessage.error(null, "Invalid corrId."));
            return;
        }

        String t = req.getType();
        String corrId = req.getCorrId();

        try {
            switch (t) {
                case "ping" -> h.send(ResponseMessage.ok("pong", corrId));
                case "health" -> health(req, h);

                case "register" -> authHandler.register(req, h);
                case "login" -> authHandler.login(req, h);
                case "logout" -> authHandler.logout(req, h);

                case "requestGame" -> gameHandler.requestGame(req, h);
                case "makeMove" -> gameHandler.makeMove(req, h);
                case "offerDraw" -> gameHandler.offerDraw(req, h);
                case "acceptDraw" -> gameHandler.respondDraw(req, h, true);
                case "declineDraw" -> gameHandler.respondDraw(req, h, false);
                case "resign" -> gameHandler.resign(req, h);

                case "listGames" -> gameHandler.listGames(req, h);
                case "getGameDetails" -> gameHandler.getGameDetails(req, h);
                case "getStats" -> authHandler.getStats(req, h);

                default -> {
                    if (metrics != null) metrics.onError(req.getType());
                    h.send(ResponseMessage.error(corrId, "Unknown message type: " + t));
                }
            }
        } catch (IllegalArgumentException ex) {
            if (metrics != null) metrics.onError(req.getType());
            h.send(ResponseMessage.error(corrId, ex.getMessage()));
        } catch (Exception ex) {
            if (metrics != null) metrics.onError(req.getType());
            Log.warn("Unhandled request failure type=" + t + " corrId=" + corrId, ex);
            h.send(ResponseMessage.error(corrId, "Internal server error."));
        }
    }

    void onDisconnect(ClientHandler h) {
        UserModels.User u = h.getCurrentUser();
        if (u == null) return;
        coordinator.onUserOffline(h, u);
    }

    private void health(RequestMessage req, ClientHandler h) {
        Map<String, Object> payload = metrics == null ? new HashMap<>() : metrics.snapshot();
        if (metrics == null) payload.put("status", "unknown");
        h.send(ResponseMessage.ok("healthOk", req.getCorrId(), payload));
    }
}
