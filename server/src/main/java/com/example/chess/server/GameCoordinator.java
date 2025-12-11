package com.example.chess.server;

import com.example.chess.common.GameModels.Result;
import com.example.chess.common.GameModels.Game;
import com.example.chess.common.GameModels.Move;
import com.example.chess.common.GameModels.Board;
import com.example.chess.common.UserModels.User;
import com.example.chess.common.UserModels.Stats;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.example.chess.server.logic.RulesEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GameCoordinator {
    // online states
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final Map<String, ClientHandler> onlineHandlers = new HashMap<>();
    private final RulesEngine rulesEngine = new RulesEngine();

    private static final long OFFLINE_MAX_MS = 60_000L; // 1 minute

    // simple FIFO queue for pairing
    private final Deque<ClientHandler> waitingQueue = new ArrayDeque<>();

    // active games
    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();

    public final ScheduledExecutorService scheduler;

    public GameCoordinator(UserRepository userRepository, GameRepository gameRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::tickClocks, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void requestGame(ClientHandler handler, User user) {
        // If no one is waiting, enqueue this player and stop.
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

        handler.onGameStarted(game, game.whiteUser.equals(user.username));
        opponentHandler.onGameStarted(game, game.whiteUser.equals(opponentUser.username));
    }

    private void tickClocks() {
        long now = System.currentTimeMillis();

        for (Game game : activeGames.values()) {
            synchronized (game) {
                if (game.result != Result.ONGOING) {
                    continue;
                }

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

                // if someone is offline for > 1 min, they lose
                if (game.whiteOfflineSince > 0 &&
                        now - game.whiteOfflineSince > OFFLINE_MAX_MS) {
                    finishGame(game, Result.BLACK_WIN, "disconnected");
                    continue;
                }
                if (game.blackOfflineSince > 0 &&
                        now - game.blackOfflineSince > OFFLINE_MAX_MS) {
                    finishGame(game, Result.WHITE_WIN, "disconnected");
                }
            }
        }
    }


    private void finishGame(Game game, Result result, String reason) {
        if(game.result != Result.ONGOING) {
            return;
        }

        game.result = result;
        game.resultReason = reason;

        boolean statsOk = updateStatsAndRatings(game);
        gameRepository.saveGame(game);

        ClientHandler whiteHandler = onlineHandlers.get(game.whiteUser);
        ClientHandler blackHandler = onlineHandlers.get(game.blackUser);

        if(whiteHandler != null) {
            whiteHandler.sendGameOver(game, statsOk);
        }

        if(blackHandler != null) {
            blackHandler.sendGameOver(game, statsOk);
        }

        activeGames.remove(game.id);
    }

    public void offerDraw(String gameId, User user) {
        Game game = requireActiveGame(gameId);
        synchronized (game) {
            if (game == null || game.result != Result.ONGOING) {
                throw new IllegalArgumentException("Game not active.");
            }

            if (!user.username.equals(game.whiteUser) && !user.username.equals(game.blackUser)) {
                throw new IllegalArgumentException("You are not part of this game.");
            }

            if (game.drawOfferedBy != null) {
                throw new IllegalArgumentException("There is already a pending draw.");
            }

            game.drawOfferedBy = user.username;

            ClientHandler opponent = getOpponentHandler(game, user.username);
            if (opponent != null) {
                opponent.sendDrawOffered(gameId, user.username);
            }
        }
    }

    public void respondDraw(String gameId, User user, boolean accepted) {
        Game game = requireActiveGame(gameId);
        synchronized (game) {
            if (game == null || game.result != Result.ONGOING) {
                throw new IllegalArgumentException("Game not found or already finished.");
            }

            if (game.drawOfferedBy == null) {
                throw new IllegalArgumentException("No pending draw offered.");
            }

            String offerer = game.drawOfferedBy;
            if (offerer.equals(user.username)) {
                throw new IllegalArgumentException("You cannot respond to your own draw offer.");
            }

            ClientHandler offererHandler = onlineHandlers.get(offerer);
            ClientHandler responderHandler = onlineHandlers.get(user.username);

            if (!accepted) {
                game.drawOfferedBy = null;
                if (offererHandler != null) {
                    offererHandler.sendDrawDeclined(gameId, user.username);
                }
                if (responderHandler != null) {
                    responderHandler.sendDrawDeclined(gameId, user.username);
                }

                return;
            }

            game.drawOfferedBy = null;
            finishGame(game, Result.DRAW, "drawAgreed");
        }
    }

    public void resign(String gameId, User user) {
        Game game = requireActiveGame(gameId);

        synchronized (game) {
            if (game == null || game.result != Result.ONGOING) {
                throw new IllegalArgumentException("Game is not active.");
            }

            if (user.username.equals(game.whiteUser)) {
                finishGame(game, Result.BLACK_WIN, "resign");
            } else if (user.username.equals(game.blackUser)) {
                finishGame(game, Result.WHITE_WIN, "resign");
            } else {
                throw new IllegalArgumentException("You are not part of this game.");
            }
        }
    }

    private ClientHandler getOpponentHandler(Game game, String username) {
        String opponent =
                username.equals(game.whiteUser) ? game.blackUser :
                        username.equals(game.blackUser) ? game.whiteUser :
                                null;

        return opponent == null ? null : onlineHandlers.get(opponent);
    }

    private boolean updateStatsAndRatings(Game game) {
        try {
            Optional<User> white = userRepository.findByUsername(game.whiteUser);
            Optional<User> black = userRepository.findByUsername(game.blackUser);

            if(white.isEmpty() || black.isEmpty()) {
                return false;
            }

            Stats ws = white.get().stats;
            Stats bs = black.get().stats;

            ws.played++;
            bs.played++;

            double sw, sb;
            switch(game.result) {
                case WHITE_WIN -> {
                    ws.won++;
                    sw = 1.0;
                    sb = 0.0;
                }
                case BLACK_WIN -> {
                    bs.won++;
                    sw = 0.0;
                    sb = 1.0;
                }
                case DRAW -> {
                    ws.drawn++;
                    bs.drawn++;
                    sw = 0.5;
                    sb = 0.5;
                }
                default -> {
                    return false;
                }
            }

            double rw = ws.rating;
            double rb = bs.rating;

            double expectedW = 1.0 / (1.0 + Math.pow(10.0, (rb - rw) / 400.0));
            double expectedB = 1.0 - expectedW;

            double K = 32.0;
            rw = rw + K * (sw - expectedW);
            rb = rb + K * (sb - expectedB);

            ws.rating = (int)Math.round(rw);
            bs.rating = (int)Math.round(rb);

            userRepository.saveUser(white.get());
            userRepository.saveUser(black.get());
        } catch (Exception e) {
            System.err.println("Failed to update stats/ratings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void makeMove(String gameId, User user, String moveStr) {
        Game game = requireActiveGame(gameId);

        synchronized (game) {
            ensurePlayerInGame(game, user);
            boolean isWhite = game.whiteUser.equals(user.username);

            Move move = parseMove(moveStr);
            ensureDifferentSquares(move);
            char piece = getPieceAtSource(game, move);
            ensurePieceBelongsToPlayer(piece, isWhite);
            ensureTargetNotOwnPiece(game, piece, move);
            ensureGeometricLegality(game, piece, move, isWhite);
            ensureKingNotInCheckAfterMove(game, piece, move, isWhite);

            applyMoveAndClock(game, piece, move, isWhite);
            String sanOrLan = moveStr; // keep your current string; later you can derive SAN if you want
            addMoveToHistory(game, sanOrLan);

            // evaluate check / mate / stalemate
            postMoveStatusAndMaybeFinish(game, sanOrLan);
        }
    }

    private Game requireActiveGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found.");
        }
        if (game.result != Result.ONGOING) {
            throw new IllegalArgumentException("Game is already finished.");
        }
        return game;
    }

    private void ensurePlayerInGame(Game game, User user) {
        if (!game.whiteUser.equals(user.username) && !game.blackUser.equals(user.username)) {
            throw new IllegalArgumentException("You are not part of this game.");
        }
    }

    private Move parseMove(String moveStr) {
        Move move = Move.parse(moveStr);
        if (move == null) {
            throw new IllegalArgumentException("Invalid move format: " + moveStr);
        }
        return move;
    }

    private void ensureDifferentSquares(Move move) {
        if (move.fromRow == move.toRow && move.fromCol == move.toCol) {
            throw new IllegalArgumentException("Target is the same square.");
        }
    }

    private char getPieceAtSource(Game game, Move move) {
        char piece = game.board.get(move.fromRow, move.fromCol);
        if (piece == '.' || piece == 0) {
            throw new IllegalArgumentException("No piece on this square.");
        }
        return piece;
    }

    private void ensurePieceBelongsToPlayer(char piece, boolean isWhite) {
        if (isWhite && !Character.isUpperCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }
        if (!isWhite && !Character.isLowerCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }
    }

    private void ensureTargetNotOwnPiece(Game game, char piece, Move move) {
        char dest = game.board.get(move.toRow, move.toCol);
        if (dest != '.' && dest != 0 && rulesEngine.sameColor(piece, dest)) {
            throw new IllegalArgumentException("Cannot capture your own piece.");
        }
    }

    private void ensureGeometricLegality(Game game, char piece, Move move, boolean isWhite) {
        if (!rulesEngine.isLegalMoveForPiece(game.board, piece, move, isWhite)) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }
    }

    private void ensureKingNotInCheckAfterMove(Game game, char piece, Move move, boolean isWhite) {
        Board test = rulesEngine.copyBoard(game.board);
        test.set(move.toRow, move.toCol, piece);
        test.set(move.fromRow, move.fromCol, '.');

        if (rulesEngine.isKingInCheck(test, isWhite)) {
            throw new IllegalArgumentException("Illegal move: king is in check.");
        }
    }

    private void applyMoveAndClock(Game game, char piece, Move move, boolean isWhite) {
        long now = System.currentTimeMillis();

        if (isWhite) {
            game.whiteTimeMs += game.incrementMs;
        } else {
            game.blackTimeMs += game.incrementMs;
        }

        game.board.set(move.toRow, move.toCol, piece);
        game.board.set(move.fromRow, move.fromCol, '.');

        game.whiteMove = !game.whiteMove;
        game.lastUpdate = now;
    }

    private void addMoveToHistory(Game game, String moveStr) {
        if (game.moves == null) {
            game.moves = new java.util.ArrayList<>();
        }
        game.moves.add(moveStr);
        gameRepository.saveGame(game);
    }

    private void postMoveStatusAndMaybeFinish(Game game, String moveStr) {
        boolean whiteInCheckNow = rulesEngine.isKingInCheck(game.board, true);
        boolean blackInCheckNow = rulesEngine.isKingInCheck(game.board, false);

        boolean sideToMoveIsWhite = game.whiteMove;
        boolean sideToMoveInCheck = sideToMoveIsWhite ? whiteInCheckNow : blackInCheckNow;
        boolean sideToMoveHasMoves = rulesEngine.hasAnyLegalMove(game.board, sideToMoveIsWhite);

        ClientHandler white = onlineHandlers.get(game.whiteUser);
        ClientHandler black = onlineHandlers.get(game.blackUser);

        if (white != null) {
            white.sendMove(game, moveStr, whiteInCheckNow, blackInCheckNow);
        }
        if (black != null) {
            black.sendMove(game, moveStr, whiteInCheckNow, blackInCheckNow);
        }

        if (sideToMoveInCheck && !sideToMoveHasMoves) {
            Result result = sideToMoveIsWhite ? Result.BLACK_WIN : Result.WHITE_WIN;
            finishGame(game, result, "checkmate");
        } else if (!sideToMoveInCheck && !sideToMoveHasMoves) {
            finishGame(game, Result.DRAW, "stalemate");
        }
    }


    public void onUserOnline(ClientHandler handler, User user) {
        onlineHandlers.put(user.username, handler);

        // resume any active game
        Game game = findActiveGameForUser(user.username);
        if (game != null) {
            synchronized (game) {
                boolean isWhite = game.whiteUser.equals(user.username);
                if (isWhite) {
                    game.whiteOfflineSince = 0L;
                } else {
                    game.blackOfflineSince = 0L;
                }
                handler.onGameStarted(game, isWhite); // same as fresh start
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


    public void onUserOffline(ClientHandler handler, User user) {
        if (user == null) return;

        onlineHandlers.remove(user.username);
        waitingQueue.remove(handler);

        Game game = findActiveGameForUser(user.username);
        if (game != null) {
            synchronized (game) {
                long now = System.currentTimeMillis();
                if (user.username.equals(game.whiteUser)) {
                    game.whiteOfflineSince = now;
                } else if (user.username.equals(game.blackUser)) {
                    game.blackOfflineSince = now;
                }
            }
        }
    }
}