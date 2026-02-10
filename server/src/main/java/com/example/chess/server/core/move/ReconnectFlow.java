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
            if (ctx.getGame().getResult() != Result.ONGOING) {
                games.remove(ctx);
                return;
            }

            boolean isWhite = ctx.isWhiteUser(u.getUsername());
            long now = System.currentTimeMillis();

            if (isWhite) {
                ctx.setWhiteOfflineAtMs(now);
                ctx.getGame().setWhiteOfflineSince(now);
            } else {
                ctx.setBlackOfflineAtMs(now);
                ctx.getGame().setBlackOfflineSince(now);
            }

            try {
                store.save(ctx.getGame());
            } catch (Exception ex) {
                Log.warn("Failed to persist disconnect markers for game: " + ctx.getGame().getId(), ex);
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
        if (u.getUsername() == null || u.getUsername().isBlank()) throw new IllegalArgumentException("Missing username.");

        GameContext ctx = games.findCtxByUser(u.getUsername());
        if (ctx == null) throw new IllegalArgumentException("Missing game context for user.");

        boolean isWhite = true;
        boolean pushGameOver = false;
        boolean persistOk = true;
        Game gameToPush;

        ClientHandler opp = null;
        String oppMsg = null;

        synchronized (ctx) {
            if (ctx.getGame().getResult() != Result.ONGOING) {
                gameToPush = ctx.getGame();
                pushGameOver = true;
                games.remove(ctx);
            } else {
                isWhite = ctx.isWhiteUser(u.getUsername());

                reconnects.cancel(key(ctx.getGame().getId(), u.getUsername()));

                if (isWhite) {
                    ctx.setWhiteHandler(newHandler);
                    ctx.setWhiteOfflineAtMs(0L);
                    ctx.getGame().setWhiteOfflineSince(0L);
                } else {
                    ctx.setBlackHandler(newHandler);
                    ctx.setBlackOfflineAtMs(0L);
                    ctx.getGame().setBlackOfflineSince(0L);
                }

                try {
                    store.save(ctx.getGame());
                } catch (Exception ex) {
                    persistOk = false;
                    Log.warn("Failed to persist reconnect markers for game " + ctx.getGame().getId(), ex);
                }

                gameToPush = ctx.getGame();

                opp = ctx.opponentHandlerOf(u.getUsername());
                if (opp != null) oppMsg = u.getUsername() + " reconnected.";
            }
        }

        if(pushGameOver) {
            newHandler.pushGameOver(gameToPush, true, true);
            return;
        }

        newHandler.pushGameStarted(ctx.getGame(), isWhite);
        if (!persistOk) {
            newHandler.sendInfo("Warning: reconnect state could not be persisted.");
        }
        if (opp != null) opp.sendInfo(oppMsg);
    }

    void recoverAfterRestart(GameContext ctx) {
        if (ctx == null || ctx.getGame() == null) throw new IllegalArgumentException("Missing game context.");
        if (ctx.getGame().getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is no longer ongoing.");

        long now = System.currentTimeMillis();
        long grace = reconnects.getGraceMs();

        long wOff = ctx.getGame().getWhiteOfflineSince();
        long bOff = ctx.getGame().getBlackOfflineSince();

        ctx.setWhiteOfflineAtMs(Math.max(wOff, 0L));
        ctx.setBlackOfflineAtMs(Math.max(bOff, 0L));

        if (ctx.getWhiteOfflineAtMs() > 0L) {
            long elapsed = now - ctx.getWhiteOfflineAtMs();
            long remaining = grace - elapsed;
            scheduleDropTask(ctx, ctx.getGame().getWhiteUser(), true, remaining);
        }

        if (ctx.getBlackOfflineAtMs() > 0L) {
            long elapsed = now - ctx.getBlackOfflineAtMs();
            long remaining = grace - elapsed;
            scheduleDropTask(ctx, ctx.getGame().getBlackUser(), false, remaining);
        }
    }

    private void scheduleDropTask(GameContext ctx, String username, boolean isWhite, long delayMs) {
        String k = key(ctx.getGame().getId(), username);

        reconnects.scheduleDrop(k, () -> {
            Runnable notify = null;
            try {
                synchronized (ctx) {
                    if (ctx.getGame().getResult() != Result.ONGOING) return;

                    long off = isWhite ? ctx.getWhiteOfflineAtMs() : ctx.getBlackOfflineAtMs();
                    if (off == 0L) return;

                    boolean noMoves = !ctx.getGame().hasAnyMoves();
                    boolean bothOffline = (ctx.getWhiteOfflineAtMs() != 0L) && (ctx.getBlackOfflineAtMs() != 0L);

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
                Log.warn("Reconnect drop task failed for game " + ctx.getGame().getId(), e);
            }
            if (notify != null) notify.run();
        }, delayMs);
    }

    private static String key(String gameId, String username) {
        return gameId + ":" + username;
    }
}
