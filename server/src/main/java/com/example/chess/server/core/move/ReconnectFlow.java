package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ReconnectService;
import com.example.chess.server.util.Log;

final class ReconnectFlow {

    private final ActiveGames games;
    private final ReconnectService reconnects;
    private final GameFinisher finisher;
    private final GameStore store;

    ReconnectFlow(ActiveGames games, ReconnectService reconnects, GameFinisher finisher, GameStore store) {
        this.games = games;
        this.reconnects = reconnects;
        this.finisher = finisher;
        this.store = store;
    }

    void onDisconnect(User u) {
        if (u == null || u.username == null) return;

        GameContext ctx = games.findCtxByUser(u.username);
        if (ctx == null) return;

        synchronized (ctx) {
            if (ctx.game.getResult() != Result.ONGOING) return;

            boolean isWhite = ctx.isWhiteUser(u.username);
            long now = System.currentTimeMillis();

            if (isWhite) {
                ctx.whiteOfflineAtMs = now;
                ctx.game.setWhiteOfflineSince(now);
            } else {
                ctx.blackOfflineAtMs = now;
                ctx.game.setBlackOfflineSince(now);
            }

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " disconnected. Waiting 60s for reconnect...");

            String key = ctx.game.getId() + ":" + u.username;
            reconnects.scheduleDrop(key, () -> {
                try {
                    synchronized (ctx) {
                        long off = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
                        if (off == 0L) return;
                        if (ctx.game.getResult() != Result.ONGOING) return;

                        finisher.finishLocked(ctx,
                                isWhite ? Result.BLACK_WIN : Result.WHITE_WIN,
                                "Disconnected for more than 60 seconds.");
                    }
                } catch (Exception e) {
                    Log.warn("Reconnect drop task failed for game " + ctx.game.getId(), e);
                }
            });
        }
    }

    void tryReconnect(User u, ClientHandler newHandler) {
        if (u == null || u.username == null || newHandler == null) return;

        GameContext ctx = games.findCtxByUser(u.username);
        if (ctx == null) return;

        synchronized (ctx) {
            if (ctx.game.getResult() != Result.ONGOING) return;

            boolean isWhite = ctx.isWhiteUser(u.username);

            reconnects.cancel(ctx.game.getId() + ":" + u.username);

            if (isWhite) {
                ctx.white = newHandler;
                ctx.whiteOfflineAtMs = 0L;
                ctx.game.setWhiteOfflineSince(0L);
            } else {
                ctx.black = newHandler;
                ctx.blackOfflineAtMs = 0L;
                ctx.game.setBlackOfflineSince(0L);
            }

            newHandler.pushGameStarted(ctx.game, isWhite);

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " reconnected.");
        }
    }
}