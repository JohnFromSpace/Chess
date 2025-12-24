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
import com.example.chess.common.board.Color;
import com.example.chess.common.pieces.Piece;

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
        User opponentUser = opponentHandler.getCurrentUser();
        if (opponentUser.username.equals(user.username)) {
            waitingQueue.addFirst(opponentHandler);
            waitingQueue.addLast(handler);
            handler.sendInfo("Waiting for opponent's response.");
            return;
        }

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
        // Disallow multiple concurrent logins with same username
        ClientHandler existing = onlineHandlers.get(user.username);
        if (existing != null && existing != handler) {
            throw new IllegalArgumentException(
                    "User '" + user.username + "' is already logged in from another client."
            );
        }

        onlineHandlers.put(user.username, handler);

        Game game = findActiveGameForUser(user.username);
        if (game == null) return;

        synchronized (game) {
            boolean isWhite = game.whiteUser.equals(user.username);
            if (isWhite) game.whiteOfflineSince = 0L;
            else game.blackOfflineSince = 0L;

            handler.sendGameStarted(game, isWhite, true);

            String opponent = isWhite ? game.blackUser : game.whiteUser;
            ClientHandler oppHandler = onlineHandlers.get(opponent);
            if (oppHandler != null) {
                oppHandler.sendInfo(user.username + " reconnected.");
            }
        }
    }

    public synchronized void onUserOffline(ClientHandler handler, User user) {
        if (user == null) return;

        // Remove only if this handler is the currently registered one
        onlineHandlers.remove(user.username, handler);

        waitingQueue.remove(handler);

        Game game = findActiveGameForUser(user.username);
        if (game == null) return;

        synchronized (game) {
            long now = System.currentTimeMillis();
            if (user.username.equals(game.whiteUser)) game.whiteOfflineSince = now;
            else if (user.username.equals(game.blackUser)) game.blackOfflineSince = now;
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

    private boolean updateStatsAndRatings(Game game) {
        try {
            return userRepository.updateUsersAtomically(game.whiteUser, game.blackUser, (white, black) -> {
                Stats ws = white.stats;
                Stats bs = black.stats;

                ws.played++;
                bs.played++;

                double sw, sb;
                switch (game.result) {
                    case WHITE_WIN -> { ws.won++; sw = 1.0; sb = 0.0; }
                    case BLACK_WIN -> { bs.won++; sw = 0.0; sb = 1.0; }
                    case DRAW      -> { ws.drawn++; bs.drawn++; sw = 0.5; sb = 0.5; }
                    default        -> { sw = 0.0; sb = 0.0; }
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
            });
        } catch (Exception e) {
            System.err.println("Failed to update stats/ratings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    void makeMove(String gameId, User user, String moveStr) throws IOException {
        if (user == null) throw new IllegalArgumentException("You must be logged in.");

        Game game = requireActiveGame(gameId);

        synchronized (game) {
            ensureOngoing(game);

            boolean isWhite = requirePlayerSide(game, user);
            ensureTurn(game, isWhite);

            if (updateClockAndFinishOnTimeout(game)) return;

            Move move = parseMove(moveStr);
            Board board = game.board;

            Piece piece = ensureMoveBasicsAndOwnership(board, move, isWhite);

            ensureLegalByRulesEngine(game, board, move, isWhite);

            rulesEngine.applyMove(board, game, move, true);

            afterSuccessfulMove(game, isWhite, move);

            checkAndFinishIfNoMoves(game);
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

            ClientHandler offererH = onlineHandlers.get(u);
            ClientHandler oppH = onlineHandlers.get(opp);

            if (offererH != null) offererH.sendInfo("You offered a draw to " + opp + ".");
            if (oppH != null) {
                oppH.sendDrawOffered(game.id, u);
                oppH.sendInfo(u + " offered you a draw.");
            }
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
            String opp = game.opponentOf(u);

            ClientHandler uH = onlineHandlers.get(u);
            ClientHandler oppH = onlineHandlers.get(opp);
            ClientHandler offererH = onlineHandlers.get(offeredBy);

            if (accept) {
                if (uH != null) uH.sendInfo("You accepted the draw.");
                if (oppH != null) oppH.sendInfo(u + " accepted the draw.");
                finishGame(game, Result.DRAW, "drawAgreed");
                return;
            }

            // decline
            game.drawOfferedBy = null;
            gameRepository.saveGame(game);

            if (uH != null) uH.sendInfo("You declined the draw.");
            if (offererH != null) offererH.sendDrawDeclined(game.id, u);
            if (oppH != null) oppH.sendInfo(u + " declined the draw.");
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

    private Game requireActiveGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Unknown or finished game.");
        return game;
    }

    private void ensureOngoing(Game game) {
        if (game.result != Result.ONGOING) throw new IllegalArgumentException("Game already finished.");
    }

    private boolean requirePlayerSide(Game game, User user) {
        boolean isWhite = user.username.equals(game.whiteUser);
        boolean isBlack = user.username.equals(game.blackUser);
        if (!isWhite && !isBlack) throw new IllegalArgumentException("You are not a player in this game.");
        return isWhite;
    }

    private void ensureTurn(Game game, boolean isWhite) {
        if (game.whiteMove != isWhite) throw new IllegalArgumentException("Not your turn.");
    }

    private boolean updateClockAndFinishOnTimeout(Game game) throws IOException {
        long now = System.currentTimeMillis();
        long elapsed = now - game.lastUpdate;
        if (elapsed < 0) elapsed = 0;

        if (game.whiteMove) {
            game.whiteTimeMs -= elapsed;
            if (game.whiteTimeMs <= 0) {
                game.whiteTimeMs = 0;
                finishGame(game, Result.BLACK_WIN, "timeout");
                return true;
            }
        } else {
            game.blackTimeMs -= elapsed;
            if (game.blackTimeMs <= 0) {
                game.blackTimeMs = 0;
                finishGame(game, Result.WHITE_WIN, "timeout");
                return true;
            }
        }

        game.lastUpdate = now;
        return false;
    }

    private Move parseMove(String moveStr) {
        Move move = Move.parse(moveStr);

        if (move.fromRow == move.toRow && move.fromCol == move.toCol) {
            throw new IllegalArgumentException("Invalid move: from == to.");
        }
        return move;
    }

    private Piece ensureMoveBasicsAndOwnership(Board board, Move move, boolean isWhite) {
        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) {
            throw new IllegalArgumentException("Move out of bounds.");
        }

        Piece piece = board.getPieceAt(move.fromSquare());
        if (piece == null) throw new IllegalArgumentException("No piece at source square.");

        boolean pieceIsWhite = (piece.getColor() == Color.WHITE);
        if (pieceIsWhite != isWhite) throw new IllegalArgumentException("You don't own that piece.");

        Piece dst = board.getPieceAt(move.toSquare());
        if (dst != null && dst.getColor() == piece.getColor()) {
            throw new IllegalArgumentException("Destination is occupied by your piece.");
        }

        return piece;
    }

    private void ensureLegalByRulesEngine(Game game, Board board, Move move, boolean isWhite) {
        if (!rulesEngine.isLegalMove(game, board, move)) {
            throw new IllegalArgumentException("Illegal move.");
        }

        Board test = board.copy();
        rulesEngine.applyMove(test, game, move, false);

        if (rulesEngine.isKingInCheck(test, isWhite)) {
            throw new IllegalArgumentException("Move leaves your king in check.");
        }
    }

    private void afterSuccessfulMove(Game game, boolean moverIsWhite, Move move) throws IOException {
        // Increment after move
        if (moverIsWhite) game.whiteTimeMs += game.incrementMs;
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

        String byUser = moverIsWhite ? game.whiteUser : game.blackUser;

        if (whiteH != null) whiteH.sendMove(game, normalized, byUser, whiteInCheck, blackInCheck);
        if (blackH != null) blackH.sendMove(game, normalized, byUser, whiteInCheck, blackInCheck);
    }

    private void checkAndFinishIfNoMoves(Game game) throws IOException {
        boolean whiteInCheck = rulesEngine.isKingInCheck(game.board, true);
        boolean blackInCheck = rulesEngine.isKingInCheck(game.board, false);

        boolean sideToMoveIsWhite = game.whiteMove;
        boolean inCheck = sideToMoveIsWhite ? whiteInCheck : blackInCheck;

        boolean hasMoves = rulesEngine.hasAnyLegalMove(game, game.board, sideToMoveIsWhite);
        if (!hasMoves) {
            if (inCheck) finishGame(game, sideToMoveIsWhite ? Result.BLACK_WIN : Result.WHITE_WIN, "checkmate");
            else finishGame(game, Result.DRAW, "stalemate");
        }
    }
}