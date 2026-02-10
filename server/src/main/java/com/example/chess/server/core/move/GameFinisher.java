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

    Runnable finishLocked(GameContext ctx, Result result, String reason) throws IOException {
        boolean rated = (result != Result.ABORTED);
        return finishLocked(ctx, result, reason, rated);
    }

    Runnable finishLocked(GameContext ctx, Result result, String reason, boolean rated) throws IOException {
        ctx.game.setResult(result);
        ctx.game.setResultReason(reason == null ? "" : reason);
        ctx.game.setRated(rated);
        ctx.game.setLastUpdate(System.currentTimeMillis());

        store.save(ctx.game);

        boolean statsOk = true;
        try {
            if (endHook != null) endHook.onGameFinished(ctx.game);
        } catch (Exception e) {
            statsOk = false;
            Log.warn("Stats/ELO update failed for game " + ctx.game.getId(), e);
        }

        ClientHandler white = ctx.white;
        ClientHandler black = ctx.black;
        Game game = ctx.game;

        cleanup(ctx);

        return gameOverNotification(game, white, black, statsOk);
    }

    private void cleanup(GameContext ctx) {
        try { clocks.stop(ctx.game.getId()); }
        catch (Exception e) { Log.warn("Failed to stop clocks for game " + ctx.game.getId(), e); }

        try { games.remove(ctx); }
        catch (Exception e) { Log.warn("Failed to remove game from active list " + ctx.game.getId(), e); }
    }

    private static Runnable gameOverNotification(Game game, ClientHandler white, ClientHandler black, boolean statsOk) {
        if (white == null && black == null) return null;

        return () -> {
            try {
                if (white != null) white.pushGameOver(game, statsOk);
            } catch (Exception e) {
                Log.warn("Failed to push gameOver to WHITE handler for game " + game.getId(), e);
            }

            try {
                if (black != null) black.pushGameOver(game, statsOk);
            } catch (Exception e) {
                Log.warn("Failed to push gameOver to BLACK handler for game " + game.getId(), e);
            }
        };
    }
}
