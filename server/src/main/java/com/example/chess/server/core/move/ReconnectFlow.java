package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ReconnectService;

final class ReconnectFlow {

    private final com.example.chess.server.core.move.ActiveGames games;
    private final ReconnectService reconnects;
    private final com.example.chess.server.core.move.GameFinisher finisher;
    private final com.example.chess.server.core.move.GameStore store;

    ReconnectFlow(com.example.chess.server.core.move.ActiveGames games, ReconnectService reconnects, com.example.chess.server.core.move.GameFinisher finisher, com.example.chess.server.core.move.GameStore store) {
        this.games = games;
        this.reconnects = reconnects;
        this.finisher = finisher;
        this.store = store;
    }

    // called by coordinator on disconnect
    void onDisconnect(User u) {
        if (u == null || u.username == null) return;

        com.example.chess.server.core.move.GameContext ctx = games.findCtxByUser(u.username);
        if (ctx == null) return;

        synchronized (ctx) {
            if (ctx.game.result != Result.ONGOING) return;

            boolean isWhite = ctx.isWhiteUser(u.username);
            long now = System.currentTimeMillis();

            if (isWhite) {
                ctx.whiteOfflineAtMs = now;
                ctx.game.whiteOfflineSince = now;
            } else {
                ctx.blackOfflineAtMs = now;
                ctx.game.blackOfflineSince = now;
            }

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " disconnected. Waiting 60s for reconnect...");

            String key = ctx.game.id + ":" + u.username;
            reconnects.scheduleDrop(key, () -> {
                try {
                    synchronized (ctx) {
                        long off = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
                        if (off == 0L) return; // reconnected
                        if (ctx.game.result != Result.ONGOING) return;

                        boolean leaverWhite = isWhite;
                        finisher.finishLocked(ctx,
                                leaverWhite ? Result.BLACK_WIN : Result.WHITE_WIN,
                                "Disconnected for more than 60 seconds.");
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    // called on login (router calls it AFTER loginOk response)
    void tryReconnect(User u, ClientHandler newHandler) {
        if (u == null || u.username == null || newHandler == null) return;

        com.example.chess.server.core.move.GameContext ctx = games.findCtxByUser(u.username);
        if (ctx == null) return;

        synchronized (ctx) {
            if (ctx.game.result != Result.ONGOING) return;

            boolean isWhite = ctx.isWhiteUser(u.username);

            // cancel grace timer + clear offline markers
            reconnects.cancel(ctx.game.id + ":" + u.username);

            if (isWhite) {
                ctx.white = newHandler;
                ctx.whiteOfflineAtMs = 0L;
                ctx.game.whiteOfflineSince = 0L;
            } else {
                ctx.black = newHandler;
                ctx.blackOfflineAtMs = 0L;
                ctx.game.blackOfflineSince = 0L;
            }

            newHandler.pushGameStarted(ctx.game, isWhite);

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " reconnected.");
        }
    }
}