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

    void registerGame(Game game,
                      String whiteUser,
                      String blackUser,
                      ClientHandler h1,
                      ClientHandler h2,
                      boolean h1IsWhite) throws IOException {

        if (game == null || game.getId() == null || game.getId().isBlank()) throw new IllegalArgumentException("There is no current game.");

        if (game.getWhiteUser() == null || game.getWhiteUser().isBlank()) game.setWhiteUser(whiteUser);
        if (game.getBlackUser() == null || game.getBlackUser().isBlank()) game.setBlackUser(blackUser);

        ClientHandler whiteH = h1IsWhite ? h1 : h2;
        ClientHandler blackH = h1IsWhite ? h2 : h1;

        registerGame(game, whiteH, blackH);
    }

    void registerGame(Game game, ClientHandler whiteH, ClientHandler blackH) throws IOException {
        if (game == null || game.getId() == null || game.getId().isBlank()) throw new IllegalArgumentException("There is no game/id.");

        long now = System.currentTimeMillis();
        if (game.getCreatedAt() == 0L) game.setCreatedAt(now);
        game.setLastUpdate(now);
        game.setResult(Result.ONGOING);

        if (game.getBoard() == null) game.setBoard(com.example.chess.common.board.Board.initial());

        GameContext ctx = new GameContext(game, whiteH, blackH);
        games.put(ctx);

        clocks.register(game);
        store.save(game);

        if (whiteH != null) whiteH.pushGameStarted(game, true);
        if (blackH != null) blackH.pushGameStarted(game, false);
    }

    GameContext rehydrateGame(Game game) {
        if (game == null || game.getId() == null || game.getId().isBlank()) throw new IllegalArgumentException("There is no current game.");

        if (game.getBoard() == null) game.setBoard(com.example.chess.common.board.Board.initial());

        // No pushing, no resetting timestamps/result: disk state is source-of-truth.
        GameContext ctx = new GameContext(game, null, null);

        // Rehydrate offline markers from persisted fields
        ctx.whiteOfflineAtMs = game.getWhiteOfflineSince();
        ctx.blackOfflineAtMs = game.getBlackOfflineSince();

        games.put(ctx);
        clocks.register(game);

        return ctx;
    }
}