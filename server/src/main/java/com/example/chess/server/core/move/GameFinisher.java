package com.example.chess.server.core.move;

import com.example.chess.common.model.Result;
import com.example.chess.server.core.ClockService;

import java.io.IOException;

final class GameFinisher {

    private final GameStore store;
    private final ClockService clocks;
    private final ActiveGames games;

    GameFinisher(GameStore store, ClockService clocks, ActiveGames games) {
        this.store = store;
        this.clocks = clocks;
        this.games = games;
    }

    void finishLocked(GameContext ctx, Result result, String reason) throws IOException {
        ctx.game.result = result;
        ctx.game.resultReason = reason == null ? "" : reason;
        ctx.game.lastUpdate = System.currentTimeMillis();

        store.save(ctx.game);

        // notify connected handlers
        if (ctx.white != null) ctx.white.pushGameOver(ctx.game, true);
        if (ctx.black != null) ctx.black.pushGameOver(ctx.game, true);

        cleanup(ctx);
    }

    private void cleanup(GameContext ctx) {
        try { clocks.stop(ctx.game.id); } catch (Exception ignored) {}

        games.remove(ctx);
    }
}