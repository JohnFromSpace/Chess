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

        if (g == null || g.id == null || g.id.isBlank()) return;

        if (g.whiteUser == null || g.whiteUser.isBlank()) g.whiteUser = whiteUser;
        if (g.blackUser == null || g.blackUser.isBlank()) g.blackUser = blackUser;

        ClientHandler whiteH = h1IsWhite ? h1 : h2;
        ClientHandler blackH = h1IsWhite ? h2 : h1;

        registerGame(g, whiteH, blackH);
    }

    void registerGame(Game g, ClientHandler whiteH, ClientHandler blackH) throws IOException {
        if (g == null || g.id == null || g.id.isBlank()) return;

        long now = System.currentTimeMillis();
        if (g.createdAt == 0L) g.createdAt = now;
        g.lastUpdate = now;
        g.result = Result.ONGOING;
        if (g.board == null) g.board = com.example.chess.common.board.Board.initial();

        GameContext ctx = new GameContext(g, whiteH, blackH);
        games.put(ctx);

        // make sure clocks exist even if matchmaking forgot
        clocks.register(g);

        store.save(g);

        if (whiteH != null) whiteH.pushGameStarted(g, true);
        if (blackH != null) blackH.pushGameStarted(g, false);
    }
}