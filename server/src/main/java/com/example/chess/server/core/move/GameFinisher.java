package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.util.Log;

import java.io.IOException;

final class GameFinisher {

    private final GameStore store;
    private final ClockService clocks;
    private final ActiveGames games;
    private final GameEndHook endHook;

    GameFinisher(GameStore store, ClockService clocks, ActiveGames games, GameEndHook endHook) {
        this.store = store;
        this.clocks = clocks;
        this.games = games;
        this.endHook = endHook;
    }

    Runnable finishLocked(GameContext ctx, Result result, String reason) {
        boolean rated = (result != Result.ABORTED);
        return finishLocked(ctx, result, reason, rated);
    }

    Runnable finishLocked(GameContext ctx, Result result, String reason, boolean rated) {
        ctx.getGame().setResult(result);
        ctx.getGame().setResultReason(reason == null ? "" : reason);
        ctx.getGame().setRated(rated);
        ctx.getGame().setLastUpdate(System.currentTimeMillis());

        boolean persistOk = true;
        try {
            store.save(ctx.getGame());
        } catch (IOException e) {
            persistOk = false;
            Log.warn("Failed to persist finished game " + ctx.getGame().getId(), e);
        }

        boolean statsOk = true;
        try {
            if (endHook != null) endHook.onGameFinished(ctx.getGame());
        } catch (Exception e) {
            statsOk = false;
            Log.warn("Stats/ELO update failed for game " + ctx.getGame().getId(), e);
        }

        ClientHandler white = ctx.getWhiteHandler();
        ClientHandler black = ctx.getBlackHandler();
        Game game = ctx.getGame();

        cleanup(ctx);

        return gameOverNotification(game, white, black, statsOk, persistOk);
    }

    private void cleanup(GameContext ctx) {
        try { clocks.stop(ctx.getGame().getId()); }
        catch (Exception e) { Log.warn("Failed to stop clocks for game " + ctx.getGame().getId(), e); }

        try { games.remove(ctx); }
        catch (Exception e) { Log.warn("Failed to remove game from active list " + ctx.getGame().getId(), e); }
    }

    private static Runnable gameOverNotification(Game game,
                                                 ClientHandler white,
                                                 ClientHandler black,
                                                 boolean statsOk,
                                                 boolean persistOk) {
        if (white == null && black == null) return null;

        return () -> {
            try {
                if (white != null) white.pushGameOver(game, statsOk, persistOk);
            } catch (Exception e) {
                Log.warn("Failed to push gameOver to WHITE handler for game " + game.getId(), e);
            }

            try {
                if (black != null) black.pushGameOver(game, statsOk, persistOk);
            } catch (Exception e) {
                Log.warn("Failed to push gameOver to BLACK handler for game " + game.getId(), e);
            }

            if (!persistOk) {
                try {
                    if (white != null) white.sendInfo("Warning: game result could not be persisted.");
                } catch (Exception e) {
                    Log.warn("Failed to warn WHITE about persistence for game " + game.getId(), e);
                }
                try {
                    if (black != null) black.sendInfo("Warning: game result could not be persisted.");
                } catch (Exception e) {
                    Log.warn("Failed to warn BLACK about persistence for game " + game.getId(), e);
                }
            }
        };
    }
}
