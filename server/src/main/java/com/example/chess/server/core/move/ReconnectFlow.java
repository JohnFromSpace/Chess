package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ReconnectService;
import com.example.chess.server.util.Log;

final class ReconnectFlow {

    private static final long GRACE_MS = 60_000L;

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

            ctx.game.setLastUpdate(now);

            // IMPORTANT: persist offlineSince so crashes/restarts can recover correctly
            try { store.save(ctx.game); }
            catch (Exception e) { Log.warn("Failed to persist offlineSince for game " + ctx.game.getId(), e); }

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " disconnected. Waiting 60s for reconnect...");

            scheduleDropLocked(ctx, u.username, isWhite);
        }
    }

    void recoverAfterRestart(GameContext ctx) {
        if (ctx == null || ctx.game == null) return;

        synchronized (ctx) {
            if (ctx.game.getResult() != Result.ONGOING) return;

            // schedule based on persisted offlineSince (remaining grace)
            if (ctx.whiteOfflineAtMs > 0 && ctx.game.getWhiteUser() != null) {
                scheduleDropLocked(ctx, ctx.game.getWhiteUser(), true);
            }
            if (ctx.blackOfflineAtMs > 0 && ctx.game.getBlackUser() != null) {
                scheduleDropLocked(ctx, ctx.game.getBlackUser(), false);
            }
        }
    }

    private void scheduleDropLocked(GameContext ctx, String username, boolean isWhite) {
        String key = ctx.game.getId() + ":" + username;

        long offAt = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
        if (offAt <= 0) return;

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - offAt);
        long remaining = GRACE_MS - elapsed;

        reconnects.scheduleDrop(key, remaining, () -> {
            try {
                synchronized (ctx) {
                    long off = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
                    if (off == 0L) return;
                    if (ctx.game.getResult() != Result.ONGOING) return;

                    boolean whiteOff = ctx.whiteOfflineAtMs != 0L;
                    boolean blackOff = ctx.blackOfflineAtMs != 0L;

                    // FAIRNESS FIX: if BOTH are offline beyond grace -> draw (not random win)
                    if (whiteOff && blackOff) {
                        finisher.finishLocked(ctx, Result.DRAW, "Both players disconnected for more than 60 seconds.");
                        return;
                    }

                    finisher.finishLocked(ctx,
                            isWhite ? Result.BLACK_WIN : Result.WHITE_WIN,
                            "Disconnected for more than 60 seconds.");
                }
            } catch (Exception e) {
                Log.warn("Reconnect drop task failed for game " + ctx.game.getId(), e);
            }
        });
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

            ctx.game.setLastUpdate(System.currentTimeMillis());
            try { store.save(ctx.game); } catch (Exception ignored) {}

            // show current state
            newHandler.pushGameStarted(ctx.game, isWhite);

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " reconnected.");
        }
    }
}