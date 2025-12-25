package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.core.ReconnectService;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;

public class MoveService {

    private final ActiveGames games = new ActiveGames();

    private final ReconnectService reconnects = new ReconnectService(60_000L);
    private final ClockService clocks;
    private final RulesEngine rules = new RulesEngine();

    private final GameStore store;
    private final GameFinisher finisher;

    private final GameRegistrationService registration;
    private final MoveFlow moves;
    private final DrawFlow draws;
    private final ReconnectFlow reconnectFlow;

    public MoveService(GameRepository gameRepo, ClockService clocks) {
        this.clocks = clocks;
        this.store = new RepositoryGameStore(gameRepo);
        this.finisher = new GameFinisher(store, clocks, games);

        this.registration = new GameRegistrationService(games, clocks, store);
        this.moves = new MoveFlow(rules, clocks, store, finisher);
        this.draws = new DrawFlow(store, finisher);
        this.reconnectFlow = new ReconnectFlow(games, reconnects, finisher, store);
    }

    public void registerGame(Game g,
                             String whiteUser,
                             String blackUser,
                             ClientHandler h1,
                             ClientHandler h2,
                             boolean h1IsWhite) throws IOException {

        registration.registerGame(g, whiteUser, blackUser, h1, h2, h1IsWhite);
    }

    public void registerGame(Game g, ClientHandler whiteH, ClientHandler blackH) throws IOException {
        registration.registerGame(g, whiteH, blackH);
    }

    public void makeMove(String gameId, User u, String uci) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = games.mustCtx(gameId);

        synchronized (ctx) {
            moves.makeMoveLocked(ctx, u, uci);
        }
    }

    public void offerDraw(String gameId, User u) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = games.mustCtx(gameId);

        synchronized (ctx) {
            draws.offerDrawLocked(ctx, u);
        }
    }

    public void respondDraw(String gameId, User u, boolean accept) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = games.mustCtx(gameId);

        synchronized (ctx) {
            draws.respondDrawLocked(ctx, u, accept);
        }
    }

    public void resign(String gameId, User u) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = games.mustCtx(gameId);

        synchronized (ctx) {
            if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
            if (ctx.game.result != com.example.chess.common.model.Result.ONGOING)
                throw new IllegalArgumentException("Game is already finished.");

            boolean leaverWhite = ctx.isWhiteUser(u.username);
            finisher.finishLocked(ctx,
                    leaverWhite ? com.example.chess.common.model.Result.BLACK_WIN : com.example.chess.common.model.Result.WHITE_WIN,
                    "Resignation.");
        }
    }

    // called by coordinator on disconnect
    public void onDisconnect(User u) {
        reconnectFlow.onDisconnect(u);
    }

    // called on login (router calls it AFTER loginOk response)
    public void tryReconnect(User u, ClientHandler newHandler) {
        reconnectFlow.tryReconnect(u, newHandler);
    }
}