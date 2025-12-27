package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;

import java.io.IOException;

final class GameRegistrationService {

    private final ActiveGames games;
    private final ClockService clocks;
    private final GameStore store;

    GameRegistrationService(ActiveGames games, ClockService clocks, GameStore store) {
        this.games = games;
        this.clocks = clocks;
        this.store = store;
    }

    void registerGame(Game g,
                      String whiteUser,
                      String blackUser,
                      ClientHandler h1,
                      ClientHandler h2,
                      boolean h1IsWhite) throws IOException {

        if (g == null || g.getId() == null || g.getId().isBlank()) return;

        if (g.getWhiteUser() == null || g.getWhiteUser().isBlank()) g.setWhiteUser(whiteUser);
        if (g.getBlackUser() == null || g.getBlackUser().isBlank()) g.setBlackUser(blackUser);

        ClientHandler whiteH = h1IsWhite ? h1 : h2;
        ClientHandler blackH = h1IsWhite ? h2 : h1;

        registerGame(g, whiteH, blackH);
    }

    void registerGame(Game g, ClientHandler whiteH, ClientHandler blackH) throws IOException {
        if (g == null || g.getId() == null || g.getId().isBlank()) return;

        long now = System.currentTimeMillis();
        if (g.getCreatedAt() == 0L) g.setCreatedAt(now);
        g.setLastUpdate(now);
        g.setResult(Result.ONGOING);

        if (g.getBoard() == null) g.setBoard(com.example.chess.common.board.Board.initial());

        GameContext ctx = new GameContext(g, whiteH, blackH);
        games.put(ctx);

        clocks.register(g);
        store.save(g);

        if (whiteH != null) whiteH.pushGameStarted(g, true);
        if (blackH != null) blackH.pushGameStarted(g, false);
    }

    GameContext rehydrateGame(Game g) throws IOException {
        if (g == null || g.getId() == null || g.getId().isBlank()) return null;

        if (g.getBoard() == null) g.setBoard(com.example.chess.common.board.Board.initial());

        // No pushing, no resetting timestamps/result: disk state is source-of-truth.
        GameContext ctx = new GameContext(g, null, null);

        // Rehydrate offline markers from persisted fields
        ctx.whiteOfflineAtMs = g.getWhiteOfflineSince();
        ctx.blackOfflineAtMs = g.getBlackOfflineSince();

        games.put(ctx);
        clocks.register(g);

        return ctx;
    }
}