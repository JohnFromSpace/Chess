package com.example.chess.server.tools;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.common.pieces.Piece;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LoadSoakTool {

    private LoadSoakTool() {}

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Load/soak error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Config cfg = Config.parse(args);
        GameRepository repo = cfg.dataDir == null
                ? new InMemoryRepo()
                : new FileStores(Path.of(cfg.dataDir));

        List<GameWork> work = new ArrayList<>();

        try (MoveService service = new MoveService(repo, new ClockService(), g -> { })) {
            for (int i = 0; i < cfg.games; i++) {
                String whiteName = "w" + i;
                String blackName = "b" + i;

                Game g = new Game();
                g.setId(UUID.randomUUID().toString());
                g.setWhiteUser(whiteName);
                g.setBlackUser(blackName);

                User white = new User();
                white.setUsername(whiteName);
                User black = new User();
                black.setUsername(blackName);

                service.registerGame(g, whiteName, blackName, null, null, true);
                work.add(new GameWork(g, white, black));
            }

            ExecutorService pool = Executors.newFixedThreadPool(cfg.threads);
            AtomicLong movesDone = new AtomicLong();
            AtomicLong errors = new AtomicLong();
            CountDownLatch done = new CountDownLatch(work.size());

            long startMs = System.currentTimeMillis();
            long endAtMs = cfg.durationMs > 0 ? startMs + cfg.durationMs : Long.MAX_VALUE;

            for (GameWork w : work) {
                pool.execute(() -> {
                    try {
                        long count = runMoves(service, w, cfg.moves, endAtMs, cfg.reconnectEvery);
                        movesDone.addAndGet(count);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            long elapsedMs = System.currentTimeMillis() - startMs;
            long totalMoves = movesDone.get();
            long eps = elapsedMs > 0 ? (totalMoves * 1000L) / elapsedMs : totalMoves;

            System.out.println("Load/soak done: games=" + cfg.games
                    + " moves=" + totalMoves
                    + " errors=" + errors.get()
                    + " elapsedMs=" + elapsedMs
                    + " movesPerSec=" + eps);
        }
    }

    private static long runMoves(MoveService service,
                                 GameWork w,
                                 int maxMoves,
                                 long endAtMs,
                                 int reconnectEvery) throws IOException {
        RulesEngine rules = new RulesEngine();
        long moves = 0;
        int limit = maxMoves <= 0 ? Integer.MAX_VALUE : maxMoves;

        while (moves < limit && System.currentTimeMillis() < endAtMs) {
            if (w.game.getResult() != Result.ONGOING) break;

            Move move = findLegalMove(w.game, rules);
            if (move == null) break;

            User mover = w.game.isWhiteMove() ? w.white : w.black;
            service.makeMove(w.game.getId(), mover, move.toString());
            moves++;

            if (w.game.getResult() == Result.ONGOING && reconnectEvery > 0 && moves % reconnectEvery == 0) {
                service.onDisconnect(mover);
                service.tryReconnect(mover, new ClientHandler(null, null, service, null, null));
            }
        }

        return moves;
    }

    private static Move findLegalMove(Game game, RulesEngine rules) {
        if (game == null) return null;
        Board board = game.getBoard();
        if (board == null) return null;

        boolean whiteToMove = game.isWhiteMove();

        for (int fr = 0; fr < 8; fr++) {
            for (int fc = 0; fc < 8; fc++) {
                Piece p = board.getPieceAt(fr, fc);
                if (p == null || p.isWhite() != whiteToMove) continue;

                for (int tr = 0; tr < 8; tr++) {
                    for (int tc = 0; tc < 8; tc++) {
                        if (fr == tr && fc == tc) continue;

                        Move m = new Move(fr, fc, tr, tc, null);
                        if (!rules.isLegalMove(game, board, m)) continue;

                        Board copy = board.copy();
                        rules.applyMove(copy, game, m, false);
                        if (!rules.isKingInCheck(copy, whiteToMove)) {
                            return m;
                        }
                    }
                }
            }
        }

        return null;
    }

    private record GameWork(Game game, User white, User black) { }

    private static final class InMemoryRepo implements GameRepository {
        private final ConcurrentMap<String, Game> games = new ConcurrentHashMap<>();

        @Override
        public void saveGame(Game game) {
            if (game == null || game.getId() == null) return;
            games.put(game.getId(), game);
        }

        @Override
        public Optional<Game> findGameById(String id) {
            return Optional.ofNullable(games.get(id));
        }

        @Override
        public ConcurrentMap<String, Game> findGamesForUser(String username) {
            ConcurrentMap<String, Game> out = new ConcurrentHashMap<>();
            if (username == null) return out;
            for (Game g : games.values()) {
                if (username.equals(g.getWhiteUser()) || username.equals(g.getBlackUser())) {
                    out.put(g.getId(), g);
                }
            }
            return out;
        }

        @Override
        public List<Game> loadAllGames() {
            return List.copyOf(games.values());
        }
    }

    private static final class Config {
        private int games = 10;
        private int moves = 40;
        private int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        private long durationMs = 0L;
        private String dataDir = null;
        private int reconnectEvery = 0;

        private static Config parse(String[] args) {
            Config cfg = new Config();
            if (args == null) return cfg;

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--games" -> cfg.games = intArg(args, ++i, "games");
                    case "--moves" -> cfg.moves = intArg(args, ++i, "moves");
                    case "--threads" -> cfg.threads = intArg(args, ++i, "threads");
                    case "--durationMs" -> cfg.durationMs = longArg(args, ++i, "durationMs");
                    case "--dataDir" -> cfg.dataDir = strArg(args, ++i, "dataDir");
                    case "--reconnectEvery" -> cfg.reconnectEvery = intArg(args, ++i, "reconnectEvery");
                    case "--help", "-h" -> {
                        usage();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown arg: " + a);
                }
            }
            return cfg;
        }

        private static int intArg(String[] args, int i, String name) {
            String raw = strArg(args, i, name);
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad " + name + ": " + raw);
            }
        }

        private static long longArg(String[] args, int i, String name) {
            String raw = strArg(args, i, name);
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad " + name + ": " + raw);
            }
        }

        private static String strArg(String[] args, int i, String name) {
            if (args == null || i < 0 || i >= args.length) {
                throw new IllegalArgumentException("Missing " + name + " value.");
            }
            return args[i];
        }

        private static void usage() {
            System.out.println("Usage: LoadSoakTool [--games N] [--moves N] [--threads N] [--durationMs MS] [--dataDir PATH] [--reconnectEvery N]");
            System.out.println("Defaults: games=10 moves=40 threads=min(4,cpu) durationMs=0 (disabled) reconnectEvery=0 (disabled)");
            System.out.println("If --dataDir is not provided, an in-memory repository is used.");
        }
    }
}
