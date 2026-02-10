package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
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
        if (u == null || u.getUsername() == null) return;
        GameContext ctx = games.findCtxByUser(u.getUsername());
        if (ctx == null) return;

        ClientHandler opp = ctx.opponentHandlerOf(u.getUsername());
        String oppMsg = null;

        synchronized (ctx) {
            if (ctx.game.getResult() != Result.ONGOING) {
                games.remove(ctx);
                return;
            }

            boolean isWhite = ctx.isWhiteUser(u.getUsername());
            long now = System.currentTimeMillis();

            if (isWhite) {
                ctx.whiteOfflineAtMs = now;
                ctx.game.setWhiteOfflineSince(now);
            } else {
                ctx.blackOfflineAtMs = now;
                ctx.game.setBlackOfflineSince(now);
            }

            try {
                store.save(ctx.game);
            } catch (Exception ex) {
                Log.warn("Failed to persist disconnect markers for game: " + ctx.game.getId(), ex);
            }

            if (opp != null)
                oppMsg = u.getUsername() + " disconnected. Waiting " + (reconnects.getGraceMs() / 1000);

            scheduleDropTask(ctx, u.getUsername(), isWhite, reconnects.getGraceMs());
        }

        if(opp != null) opp.sendInfo(oppMsg);
    }

    void tryReconnect(User u, ClientHandler newHandler) {
        if (u == null) throw new IllegalArgumentException("Missing user.");
        if (newHandler == null) throw new IllegalArgumentException("Missing handler.");
        if (u.getUsername() == null || u.getUsername().isBlank()) throw new IllegalArgumentException("Missing username");

        GameContext ctx = games.findCtxByUser(u.getUsername());
        if (ctx == null) throw new IllegalArgumentException("There is no game context for current user.");

        boolean isWhite = true;
        boolean pushGameOver = false;
        boolean persistOk = true;
        Game gameToPush;

        ClientHandler opp = null;
        String oppMsg = null;

        synchronized (ctx) {
            if (ctx.game.getResult() != Result.ONGOING) {
                gameToPush = ctx.game;
                pushGameOver = true;
                games.remove(ctx);
            } else {
                isWhite = ctx.isWhiteUser(u.getUsername());

                reconnects.cancel(key(ctx.game.getId(), u.getUsername()));

                if (isWhite) {
                    ctx.white = newHandler;
                    ctx.whiteOfflineAtMs = 0L;
                    ctx.game.setWhiteOfflineSince(0L);
                } else {
                    ctx.black = newHandler;
                    ctx.blackOfflineAtMs = 0L;
                    ctx.game.setBlackOfflineSince(0L);
                }

                try {
                    store.save(ctx.game);
                } catch (Exception ex) {
                    persistOk = false;
                    Log.warn("Failed to persist reconnect markers for game " + ctx.game.getId(), ex);
                }

                gameToPush = ctx.game;

                opp = ctx.opponentHandlerOf(u.getUsername());
                if (opp != null) oppMsg = u.getUsername() + " reconnected.";
            }
        }

        if(pushGameOver) {
            newHandler.pushGameOver(gameToPush, true, true);
            return;
        }

        newHandler.pushGameStarted(ctx.game, isWhite);
        if (!persistOk) {
            newHandler.sendInfo("Warning: reconnect state could not be persisted.");
        }
        if (opp != null) opp.sendInfo(oppMsg);
    }

    void recoverAfterRestart(GameContext ctx) {
        if (ctx == null || ctx.game == null) throw new IllegalArgumentException("There is no game context.");
        if (ctx.game.getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is no longer ongoing.");

        long now = System.currentTimeMillis();
        long grace = reconnects.getGraceMs();

        long wOff = ctx.game.getWhiteOfflineSince();
        long bOff = ctx.game.getBlackOfflineSince();

        ctx.whiteOfflineAtMs = Math.max(wOff, 0L);
        ctx.blackOfflineAtMs = Math.max(bOff, 0L);

        if (ctx.whiteOfflineAtMs > 0L) {
            long elapsed = now - ctx.whiteOfflineAtMs;
            long remaining = grace - elapsed;
            scheduleDropTask(ctx, ctx.game.getWhiteUser(), true, remaining);
        }

        if (ctx.blackOfflineAtMs > 0L) {
            long elapsed = now - ctx.blackOfflineAtMs;
            long remaining = grace - elapsed;
            scheduleDropTask(ctx, ctx.game.getBlackUser(), false, remaining);
        }
    }

    private void scheduleDropTask(GameContext ctx, String username, boolean isWhite, long delayMs) {
        String k = key(ctx.game.getId(), username);

        reconnects.scheduleDrop(k, () -> {
            Runnable notify = null;
            try {
                synchronized (ctx) {
                    if (ctx.game.getResult() != Result.ONGOING) return;

                    long off = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
                    if (off == 0L) return;

                    boolean noMoves = !ctx.game.hasAnyMoves();
                    boolean bothOffline = (ctx.whiteOfflineAtMs != 0L) && (ctx.blackOfflineAtMs != 0L);

                    if (noMoves || bothOffline) {
                        notify = finisher.finishLocked(
                                ctx,
                                Result.ABORTED,
                                bothOffline ? "Aborted (both disconnected)." : "Aborted (no moves).",
                                false
                        );
                    } else {
                        notify = finisher.finishLocked(
                                ctx,
                                isWhite ? Result.BLACK_WIN : Result.WHITE_WIN,
                                "Disconnected for more than 60 seconds."
                        );
                    }
                }
            } catch (Exception e) {
                Log.warn("Reconnect drop task failed for game " + ctx.game.getId(), e);
            }
            if (notify != null) notify.run();
        }, delayMs);
    }

    private static String key(String gameId, String username) {
        return gameId + ":" + username;
    }
}
