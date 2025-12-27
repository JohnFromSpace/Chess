package com.example.chess.server.core.move;

import com.example.chess.common.model.Result;
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

    void finishLocked(GameContext ctx, Result result, String reason) throws IOException {
        ctx.game.setResult(result);
        ctx.game.setResultReason(reason == null ? "" : reason);
        ctx.game.setLastUpdate(System.currentTimeMillis());

        store.save(ctx.game);

        boolean statsOk = true;
        try {
            if (endHook != null) endHook.onGameFinished(ctx.game);
        } catch (Exception e) {
            statsOk = false;
            Log.warn("Stats/ELO update failed for game " + ctx.game.getId(), e);
        }

        try {
            if (ctx.white != null) ctx.white.pushGameOver(ctx.game, statsOk);
        } catch (Exception e) {
            Log.warn("Failed to push gameOver to WHITE handler for game " + ctx.game.getId(), e);
        }

        try {
            if (ctx.black != null) ctx.black.pushGameOver(ctx.game, statsOk);
        } catch (Exception e) {
            Log.warn("Failed to push gameOver to BLACK handler for game " + ctx.game.getId(), e);
        }

        cleanup(ctx);
    }

    private void cleanup(GameContext ctx) {
        try { clocks.stop(ctx.game.getId()); }
        catch (Exception e) { Log.warn("Failed to stop clocks for game " + ctx.game.getId(), e); }

        try { games.remove(ctx); }
        catch (Exception e) { Log.warn("Failed to remove game from active list " + ctx.game.getId(), e); }
    }
}