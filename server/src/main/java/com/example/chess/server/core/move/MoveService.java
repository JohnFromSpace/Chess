package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.core.ReconnectService;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final AtomicBoolean ready = new AtomicBoolean(false);

    private final ScheduledExecutorService tickExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "clock-ticker");
                t.setDaemon(true);
                return t;
            });

    public MoveService(GameRepository gameRepo, ClockService clocks, GameEndHook endHook) {
        this.clocks = clocks;
        this.store = new RepositoryGameStore(gameRepo);
        this.finisher = new GameFinisher(store, clocks, games, endHook);

        this.registration = new GameRegistrationService(games, clocks, store);
        this.moves = new MoveFlow(rules, clocks, store, finisher);
        this.draws = new DrawFlow(store, finisher);
        this.reconnectFlow = new ReconnectFlow(games, reconnects, finisher, store);

        tickExec.scheduleAtFixedRate(this::tickAllGames, 200, 200, TimeUnit.MILLISECONDS);
    }

    private void tickAllGames() {
        if (!ready.get()) return;

        for (GameContext ctx : games.snapshot()) {
            try {
                synchronized (ctx) {
                    if (ctx.game.getResult() != com.example.chess.common.model.Result.ONGOING) continue;

                    boolean timeout = clocks.tick(ctx.game);
                    if (!timeout) continue;

                    if (ctx.game.getWhiteTimeMs() <= 0) {
                        finisher.finishLocked(ctx, com.example.chess.common.model.Result.BLACK_WIN, "timeout.");
                    } else if (ctx.game.getBlackTimeMs() <= 0) {
                        finisher.finishLocked(ctx, com.example.chess.common.model.Result.WHITE_WIN, "timeout.");
                    }
                }
            } catch (Exception e) {
                com.example.chess.server.util.Log.warn("tickAllGames failed ", e);
            }
        }
    }

    public void registerGame(Game g,
                             String whiteUser,
                             String blackUser,
                             ClientHandler h1,
                             ClientHandler h2,
                             boolean h1IsWhite) throws IOException {

        registration.registerGame(g, whiteUser, blackUser, h1, h2, h1IsWhite);
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
            if (ctx.game.getResult() != com.example.chess.common.model.Result.ONGOING)
                throw new IllegalArgumentException("Game is already finished.");

            boolean leaverWhite = ctx.isWhiteUser(u.username);
            finisher.finishLocked(ctx,
                    leaverWhite ? com.example.chess.common.model.Result.BLACK_WIN : com.example.chess.common.model.Result.WHITE_WIN,
                    "Resignation.");
        }
    }

    public void onDisconnect(User u) {
        reconnectFlow.onDisconnect(u);
    }

    public void tryReconnect(User u, ClientHandler newHandler) {
        reconnectFlow.tryReconnect(u, newHandler);
    }

    public void recoverOngoingGames(java.util.List<Game> allGamesFromDisk, long serverDownAtMs) {
        try {
            if (allGamesFromDisk == null) allGamesFromDisk = java.util.List.of();

            for (Game g : allGamesFromDisk) {
                if (g == null || g.getId() == null || g.getId().isBlank()) continue;
                if (g.getResult() != com.example.chess.common.model.Result.ONGOING) continue;

                // If offlineSince wasn't persisted earlier, assume BOTH became offline at serverDownAtMs
                if (g.getWhiteOfflineSince() <= 0L) g.setWhiteOfflineSince(serverDownAtMs);
                if (g.getBlackOfflineSince() <= 0L) g.setBlackOfflineSince(serverDownAtMs);

                g.setLastUpdate(Math.max(g.getLastUpdate(), serverDownAtMs));

                // persist these recovery markers so next restart is consistent
                try { store.save(g); } catch (Exception ignored) {}

                GameContext ctx = registration.rehydrateGame(g);
                if (ctx != null) {
                    reconnectFlow.recoverAfterRestart(ctx);
                }
            }
        } catch (Exception e) {
            com.example.chess.server.util.Log.warn("recoverOngoingGames failed", e);
        } finally {
            ready.set(true);
        }
    }
}