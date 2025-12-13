package com.example.chess.server;

import com.example.chess.common.UserModels.Stats;
import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameCoordinator {
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    private final Map<String, ClientHandler> onlineHandlers = new ConcurrentHashMap<>();

    private final RulesEngine rulesEngine = new RulesEngine();

    private static final long OFFLINE_MAX_MS = 60_000L; // 1 minute

    private final Deque<ClientHandler> waitingQueue = new ArrayDeque<>();

    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();

    public final ScheduledExecutorService scheduler;

    public GameCoordinator(UserRepository userRepository, GameRepository gameRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                tickClocks();
            } catch (IOException e) {
                System.err.println("tickClocks failed: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("tickClocks unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void requestGame(ClientHandler handler, User user) throws IOException {
        if (waitingQueue.isEmpty()) {
            waitingQueue.addLast(handler);
            handler.sendInfo("Waiting for opponent's response.");
            return;
        }

        ClientHandler opponentHandler = waitingQueue.pollFirst();
        if (opponentHandler == handler || opponentHandler == null || opponentHandler.getCurrentUser() == null) {
            waitingQueue.addLast(handler);
            handler.sendInfo("Waiting for opponent's response.");
            return;
        }

        User opponentUser = opponentHandler.getCurrentUser();

        Game game = new Game();
        game.id = UUID.randomUUID().toString();

        boolean thisIsWhite = new Random().nextBoolean();
        if (thisIsWhite) {
            game.whiteUser = user.username;
            game.blackUser = opponentUser.username;
        } else {
            game.whiteUser = opponentUser.username;
            game.blackUser = user.username;
        }

        long now = System.currentTimeMillis();
        game.createdAt = now;
        game.lastUpdate = now;
        game.result = Result.ONGOING;

        activeGames.put(game.id, game);
        gameRepository.saveGame(game);

        handler.sendGameStarted(game, game.whiteUser.equals(user.username), false);
        opponentHandler.sendGameStarted(game, game.whiteUser.equals(opponentUser.username), false);
    }

    private void tickClocks() throws IOException {
        long now = System.currentTimeMillis();
        List<Game> gamesSnapshot = new ArrayList<>(activeGames.values());

        for (Game game : gamesSnapshot) {
            synchronized (game) {
                if (game.result != Result.ONGOING) continue;

                long elapsed = now - game.lastUpdate;
                if (elapsed > 0) {
                    if (game.whiteMove) {
                        game.whiteTimeMs -= elapsed;
                        if (game.whiteTimeMs <= 0) {
                            game.whiteTimeMs = 0;
                            finishGame(game, Result.BLACK_WIN, "timeout");
                            continue;
                        }
                    } else {
                        game.blackTimeMs -= elapsed;
                        if (game.blackTimeMs <= 0) {
                            game.blackTimeMs = 0;
                            finishGame(game, Result.WHITE_WIN, "timeout");
                            continue;
                        }
                    }
                    game.lastUpdate = now;
                }

                boolean whiteExpired = game.whiteOfflineSince > 0 && (now - game.whiteOfflineSince > OFFLINE_MAX_MS);
                boolean blackExpired = game.blackOfflineSince > 0 && (now - game.blackOfflineSince > OFFLINE_MAX_MS);

                if (whiteExpired && blackExpired) {
                    finishGame(game, Result.DRAW, "bothDisconnected");
                    continue;
                }
                if (whiteExpired) {
                    finishGame(game, Result.BLACK_WIN, "disconnected");
                    continue;
                }
                if (blackExpired) {
                    finishGame(game, Result.WHITE_WIN, "disconnected");
                }
            }
        }
    }

    private void finishGame(Game game, Result result, String reason) throws IOException {
        if (game.result != Result.ONGOING) return;

        game.result = result;
        game.resultReason = reason;

        boolean statsOk = updateStatsAndRatings(game);
        gameRepository.saveGame(game);

        ClientHandler whiteHandler = onlineHandlers.get(game.whiteUser);
        ClientHandler blackHandler = onlineHandlers.get(game.blackUser);

        if (whiteHandler != null) whiteHandler.sendGameOver(game, statsOk);
        if (blackHandler != null) blackHandler.sendGameOver(game, statsOk);

        activeGames.remove(game.id);
    }

    public synchronized void onUserOnline(ClientHandler handler, User user) {
        // Replace session if user logs from another client
        onlineHandlers.put(user.username, handler);

        Game game = findActiveGameForUser(user.username);
        if (game == null) return;

        synchronized (game) {
            boolean isWhite = game.whiteUser.equals(user.username);
            if (isWhite) game.whiteOfflineSince = 0L;
            else game.blackOfflineSince = 0L;

            handler.sendGameStarted(game, isWhite, true);

            // Optional UX: notify opponent
            String opponent = isWhite ? game.blackUser : game.whiteUser;
            ClientHandler oppHandler = onlineHandlers.get(opponent);
            if (oppHandler != null) {
                oppHandler.sendInfo(user.username + " reconnected.");
            }
        }
    }

    private Game findActiveGameForUser(String username) {
        for (Game g : activeGames.values()) {
            if (g.result == Result.ONGOING &&
                    (username.equals(g.whiteUser) || username.equals(g.blackUser))) {
                return g;
            }
        }
        return null;
    }

    public synchronized void onUserOffline(ClientHandler handler, User user) {
        if (user == null) return;

        onlineHandlers.remove(user.username);
        waitingQueue.remove(handler);

        Game game = findActiveGameForUser(user.username);
        if (game == null) return;

        synchronized (game) {
            long now = System.currentTimeMillis();
            if (user.username.equals(game.whiteUser)) game.whiteOfflineSince = now;
            else if (user.username.equals(game.blackUser)) game.blackOfflineSince = now;
        }
    }

    private boolean updateStatsAndRatings(Game game) {
        try {
            Optional<User> white = userRepository.findByUsername(game.whiteUser);
            Optional<User> black = userRepository.findByUsername(game.blackUser);
            if (white.isEmpty() || black.isEmpty()) return false;

            Stats ws = white.get().stats;
            Stats bs = black.get().stats;

            ws.played++;
            bs.played++;

            double sw, sb;
            switch (game.result) {
                case WHITE_WIN -> { ws.won++; sw = 1.0; sb = 0.0; }
                case BLACK_WIN -> { bs.won++; sw = 0.0; sb = 1.0; }
                case DRAW -> { ws.drawn++; bs.drawn++; sw = 0.5; sb = 0.5; }
                default -> { return false; }
            }

            double rw = ws.rating;
            double rb = bs.rating;

            double expectedW = 1.0 / (1.0 + Math.pow(10.0, (rb - rw) / 400.0));
            double expectedB = 1.0 - expectedW;

            double K = 32.0;
            rw = rw + K * (sw - expectedW);
            rb = rb + K * (sb - expectedB);

            ws.rating = (int) Math.round(rw);
            bs.rating = (int) Math.round(rb);

            userRepository.saveUser(white.get());
            userRepository.saveUser(black.get());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to update stats/ratings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void makeMove(String gameId, User user, String moveStr) throws IOException {
        if (user == null) throw new IllegalArgumentException("You must be logged in.");
        Game game = activeGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Unknown or finished game.");

        synchronized (game) {
            if (game.result != Result.ONGOING) throw new IllegalArgumentException("Game already finished.");

            boolean isWhite = user.username.equals(game.whiteUser);
            boolean isBlack = user.username.equals(game.blackUser);
            if (!isWhite && !isBlack) throw new IllegalArgumentException("You are not a player in this game.");

            // Turn check
            if (game.whiteMove != isWhite) throw new IllegalArgumentException("Not your turn.");

            // Update clock since lastUpdate (tickClocks may not have accounted the last fraction)
            long now = System.currentTimeMillis();
            long elapsed = now - game.lastUpdate;
            if (elapsed < 0) elapsed = 0;

            if (game.whiteMove) {
                game.whiteTimeMs -= elapsed;
                if (game.whiteTimeMs <= 0) {
                    game.whiteTimeMs = 0;
                    finishGame(game, Result.BLACK_WIN, "timeout");
                    return;
                }
            } else {
                game.blackTimeMs -= elapsed;
                if (game.blackTimeMs <= 0) {
                    game.blackTimeMs = 0;
                    finishGame(game, Result.WHITE_WIN, "timeout");
                    return;
                }
            }
            game.lastUpdate = now;

            Move move = Move.parse(moveStr);

            if (move.fromRow == move.toRow && move.fromCol == move.toCol) {
                throw new IllegalArgumentException("Invalid move: from == to.");
            }

            Board board = game.board;
            if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) {
                throw new IllegalArgumentException("Move out of bounds.");
            }

            char piece = board.get(move.fromRow, move.fromCol);
            if (piece == '.' || piece == 0) throw new IllegalArgumentException("No piece at source square.");

            boolean pieceIsWhite = Character.isUpperCase(piece);
            if (pieceIsWhite != isWhite) throw new IllegalArgumentException("You don't own that piece.");

            char dst = board.get(move.toRow, move.toCol);
            if ((dst != '.' && dst != 0) && rulesEngine.sameColor(piece, dst)) {
                throw new IllegalArgumentException("Destination is occupied by your piece.");
            }

            if (!rulesEngine.isLegalMove(board, move)) {
                throw new IllegalArgumentException("Illegal move.");
            }

            // Simulate -> must not leave own king in check
            Board test = rulesEngine.copyBoard(board);
            test.set(move.toRow, move.toCol, piece);
            test.set(move.fromRow, move.fromCol, '.');

            if (rulesEngine.isKingInCheck(test, isWhite)) {
                throw new IllegalArgumentException("Move leaves your king in check.");
            }

            // Apply on real board
            board.set(move.toRow, move.toCol, piece);
            board.set(move.fromRow, move.fromCol, '.');

            // Increment after move
            if (isWhite) game.whiteTimeMs += game.incrementMs;
            else game.blackTimeMs += game.incrementMs;

            // Any move cancels pending draw offer
            game.drawOfferedBy = null;

            String normalized = move.toString();
            game.moves.add(normalized);

            // Switch turn
            game.whiteMove = !game.whiteMove;

            // Persist state
            gameRepository.saveGame(game);

            boolean whiteInCheck = rulesEngine.isKingInCheck(game.board, true);
            boolean blackInCheck = rulesEngine.isKingInCheck(game.board, false);

            ClientHandler whiteH = onlineHandlers.get(game.whiteUser);
            ClientHandler blackH = onlineHandlers.get(game.blackUser);

            if (whiteH != null) whiteH.sendMove(game, normalized, whiteInCheck, blackInCheck);
            if (blackH != null) blackH.sendMove(game, normalized, whiteInCheck, blackInCheck);

            // Checkmate / stalemate for side to move
            boolean sideToMoveIsWhite = game.whiteMove;
            boolean inCheck = sideToMoveIsWhite ? whiteInCheck : blackInCheck;
            boolean hasMoves = rulesEngine.hasAnyLegalMove(game.board, sideToMoveIsWhite);

            if (!hasMoves) {
                if (inCheck) {
                    finishGame(game, sideToMoveIsWhite ? Result.BLACK_WIN : Result.WHITE_WIN, "checkmate");
                } else {
                    finishGame(game, Result.DRAW, "stalemate");
                }
            }
        }
    }

    public void offerDraw(String gameId, User user) throws IOException {
        if (user == null) throw new IllegalArgumentException("You must be logged in.");
        Game game = activeGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Unknown or finished game.");

        synchronized (game) {
            if (game.result != Result.ONGOING) throw new IllegalArgumentException("Game already finished.");

            String u = user.username;
            if (!u.equals(game.whiteUser) && !u.equals(game.blackUser)) {
                throw new IllegalArgumentException("You are not a player in this game.");
            }

            if (game.drawOfferedBy != null && !game.drawOfferedBy.isBlank()) {
                throw new IllegalArgumentException("A draw is already offered.");
            }

            game.drawOfferedBy = u;
            gameRepository.saveGame(game);

            String opp = game.opponentOf(u);
            ClientHandler oppH = onlineHandlers.get(opp);
            if (oppH != null) oppH.sendDrawOffered(game.id, u);
        }
    }

    public void respondDraw(String gameId, User user, boolean accept) throws IOException {
        if (user == null) throw new IllegalArgumentException("You must be logged in.");
        Game game = activeGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Unknown or finished game.");

        synchronized (game) {
            if (game.result != Result.ONGOING) throw new IllegalArgumentException("Game already finished.");

            String u = user.username;
            if (!u.equals(game.whiteUser) && !u.equals(game.blackUser)) {
                throw new IllegalArgumentException("You are not a player in this game.");
            }

            if (game.drawOfferedBy == null || game.drawOfferedBy.isBlank()) {
                throw new IllegalArgumentException("No draw offer to respond to.");
            }

            String offeredBy = game.drawOfferedBy;

            if (accept) {
                finishGame(game, Result.DRAW, "draw agreed");
                return;
            }

            // decline
            game.drawOfferedBy = null;
            gameRepository.saveGame(game);

            ClientHandler offerer = onlineHandlers.get(offeredBy);
            if (offerer != null) offerer.sendDrawDeclined(game.id, u);
        }
    }

    public void resign(String gameId, User user) throws IOException {
        if (user == null) throw new IllegalArgumentException("You must be logged in.");
        Game game = activeGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Unknown or finished game.");

        synchronized (game) {
            if (game.result != Result.ONGOING) throw new IllegalArgumentException("Game already finished.");

            String u = user.username;
            if (!u.equals(game.whiteUser) && !u.equals(game.blackUser)) {
                throw new IllegalArgumentException("You are not a player in this game.");
            }

            boolean resigningWhite = u.equals(game.whiteUser);
            finishGame(game, resigningWhite ? Result.BLACK_WIN : Result.WHITE_WIN, "resign");
        }
    }
}