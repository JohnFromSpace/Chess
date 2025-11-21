package com.example.chess.server;

import com.example.chess.common.GameModels.Result;
import com.example.chess.common.GameModels.Game;
import com.example.chess.common.GameModels.Move;
import com.example.chess.common.GameModels.Board;
import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GameCoordinator {
    private final FileStores fileStores;
    private final Map<String, ClientHandler> onlineHandlers = new HashMap<>();
    private final Deque<ClientHandler> waitingQueue = new ArrayDeque<>();
    private final Map<String, Game> activeGames = new HashMap<>();

    public final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public GameCoordinator(FileStores fileStores) {
        this.fileStores = fileStores;
        scheduler.scheduleAtFixedRate(this::tickClocks, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void onUserOnline(ClientHandler handler, User user) {
        onlineHandlers.put(user.username, handler);
    }

    public synchronized void onUserOffline(ClientHandler handler, User user) {
        if (user == null) {
            return;
        }

        onlineHandlers.remove(user.username);
        waitingQueue.remove(handler);
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
        fileStores.saveGame(game);

        handler.onGameStarted(game, game.whiteUser.equals(user.username));
        opponentHandler.onGameStarted(game, game.whiteUser.equals(opponentUser.username));
    }

    public synchronized void makeMove(String gameId, User user, String moveStr){
        Game game = activeGames.get(gameId);

        if(game == null) {
            throw new IllegalArgumentException("Game not found.");
        }

        if(game.result != Result.ONGOING) {
            throw new IllegalArgumentException("Game is already finished");
        }

        boolean isWhite = game.whiteUser.equals(user.username);
        if(!isWhite && !game.blackUser.equals(user.username)) {
            throw new IllegalArgumentException("You are not part of this game.");
        }

        Move move = Move.parse(moveStr);

        if(move.fromRow == move.toRow && move.fromCol == move.toCol) {
            throw new IllegalArgumentException("Target is the same square.");
        }

        char piece = game.board.get(move.fromRow, move.fromCol);
        if(piece == '.' || piece == 0) {
            throw new IllegalArgumentException("No piece on this square.");
        }

        if(isWhite && !Character.isUpperCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }

        if(!isWhite && !Character.isUpperCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }

        char dest = game.board.get(move.toRow, move.toCol);
        if(dest != '.' && sameColor(piece, dest)) {
            throw new IllegalArgumentException("Cannot capture your own piece.");
        }

        if(!isLegalMoveForPiece(game.board, piece, move, isWhite)) {
            throw new IllegalArgumentException("Illegal move: " + moveStr);
        }

        if(isWhite) {
            game.whiteTimeMs += game.incrementMs;
        } else {
            game.blackTimeMs += game.incrementMs;
        }

        game.board.set(move.toRow, move.toCol, piece);
        game.board.set(move.fromRow, move.fromCol, piece);

        game.whiteMove = !game.whiteMove;
        game.lastUpdate = System.currentTimeMillis();

        fileStores.saveGame(game);

        ClientHandler white = onlineHandlers.get(game.whiteUser);
        ClientHandler black = onlineHandlers.get(game.blackUser);

        if(white != null) {
            white.sendMove(game, moveStr);
        }

        if(black != null) {
            black.sendMove(game, moveStr);
        }
    }

    private boolean isLegalMoveForPiece(Board board, char piece, Move m, boolean isWhite) {
        char p = Character.toLowerCase(piece);

        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);

        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        return switch (p) {
            case 'p' -> isLegalPawnMove(board, m, isWhite);
            case 'n' -> adx * adx + ady * ady == 5;
            case 'b' -> {
                if (adx != ady) {
                    yield false;
                }
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'r' -> {
                if (!(dx == 0 || dy == 0)) {
                    yield false;
                }
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'q' -> {
                if (!(dx == 0 || dy == 0 || adx == ady)) {
                    yield false;
                }
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'k' -> adx <= 1 && ady <= 1;
            default -> false;
        };
    }

    private boolean isLegalPawnMove(Board board, Move m, boolean isWhite) {
        int dir = isWhite ? -1 : 1;
        int startRow = isWhite ? 6 : 1;

        int dx = m.toCol - m.fromCol;
        int dy = m.toRow - m.fromRow;

        char dest = board.get(m.toRow, m.toCol);

        if(dx == 0) {
            if(dy == dir && dest == '.') {
                return true;
            }

            if(m.toRow == startRow && dy == 2 * dir) {
                int midRow = m.fromRow + dir;
                return board.get(midRow, m.fromCol) == '.' && dest == '.';
            }

            return false;
        }

        if(Math.abs(dx) == 1 && dy == dir) {
            return dest != '.' && !sameColor(board.get(m.fromRow, m.fromCol), dest);
        }

        return false;
    }

    private boolean isPathClear(Board board, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int r = fromRow + dRow;
        int c = fromCol + dCol;

        while(r != toRow || c != toCol) {
            char p = board.get(r, c);
            if(p != '.' && p != 0) {
                return false;
            }

            r += dRow;
            c += dCol;
        }

        return true;
    }

    private boolean sameColor(char a, char b) {
        if(a == '.' || b == '.' || a == 0 || b == 0) {
            return false;
        }

        boolean whiteA = Character.isUpperCase(a);
        boolean whiteB = Character.isUpperCase(b);

        return whiteA == whiteB;
    }

    private Board copyBoard(Board b) {
        Board nb = new Board();
        for(int row = 0; row < 8; row++) {
            for(int col = 0; col < 8; col++) {
                nb.squares[row][col] = b.squares[row][col];
            }
        }

        return nb;
    }

    private boolean isSquareAttacked(Board board, int row, int col, boolean byWhite) {
        // check all directions for rooks & queens
        int[][] rookDirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
        for (int[] d : rookDirs) {
            int r = row + d[0], c = col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (p != '.' && p != 0) {
                    if (byWhite && Character.isUpperCase(p) &&
                            (p == 'R' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) &&
                            (p == 'r' || p == 'q')) return true;
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }

        // bishop & queen diagonals
        int[][] bishopDirs = { {1,1}, {1,-1}, {-1,1}, {-1,-1} };
        for (int[] d : bishopDirs) {
            int r = row + d[0], c = col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (p != '.' && p != 0) {
                    if (byWhite && Character.isUpperCase(p) &&
                            (p == 'B' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) &&
                            (p == 'b' || p == 'q')) return true;
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }

        // knights
        int[][] knightMoves = {
                {2,1},{2,-1},{-2,1},{-2,-1},
                {1,2},{1,-2},{-1,2},{-1,-2}
        };
        for (int[] m : knightMoves) {
            int r = row + m[0], c = col + m[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (byWhite && p == 'N') return true;
                if (!byWhite && p == 'n') return true;
            }
        }

        // pawns
        int dir = byWhite ? -1 : 1;
        int pawnRow = row + dir;

        if (pawnRow >= 0 && pawnRow < 8) {
            if (col - 1 >= 0) {
                char p = board.get(pawnRow, col - 1);
                if (byWhite && p == 'P') return true;
                if (!byWhite && p == 'p') return true;
            }
            if (col + 1 < 8) {
                char p = board.get(pawnRow, col + 1);
                if (byWhite && p == 'P') return true;
                if (!byWhite && p == 'p') return true;
            }
        }

        // adjacent king
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                    char p = board.get(r, c);
                    if (byWhite && p == 'K') return true;
                    if (!byWhite && p == 'k') return true;
                }
            }
        }

        return false;
    }

    private boolean isKingInCheck(Board board, boolean isWhite) {
        char king = isWhite ? 'K' : 'k';
        int kr = -1;
        int kc = -1;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if(board.get(row, col) == king) {
                    kr = row;
                    kc = col;
                }
            }
        }

        if(kr == -1) {
            return true;
        }

        return isSquareAttacked(board, kr, kc, isWhite);
    }

    private synchronized void tickClocks() {
        long now = System.currentTimeMillis();

        for (Game game : activeGames.values()) {
            if (game.result != Result.ONGOING) {
                continue;
            }

            long elapsed = now - game.lastUpdate;
            if (elapsed <= 0) {
                continue;
            }

            if (game.whiteMove) {
                game.whiteTimeMs -= elapsed;
                if (game.whiteTimeMs <= 0) {
                    game.whiteTimeMs = 0;
                    finishGame(game, Result.BLACK_WIN, "timeout");
                }
            } else {
                game.blackTimeMs -= elapsed;
                if (game.blackTimeMs <= 0) {
                    game.blackTimeMs = 0;
                    finishGame(game, Result.ONGOING, "timeout");
                }
            }

            game.lastUpdate = now;
        }
    }

    private synchronized void finishGame(Game game, Result result, String reason) {
        if(game.result != Result.ONGOING) {
            return;
        }

        game.result = result;
        game.resultReason = reason;
        fileStores.saveGame(game);

        ClientHandler whiteHandler = onlineHandlers.get(game.whiteUser);
        ClientHandler blackHandler = onlineHandlers.get(game.blackUser);

        if(whiteHandler != null) {
            whiteHandler.sendGameOver(game);
        }

        if(blackHandler != null) {
            blackHandler.sendGameOver(game);
        }
    }

    public synchronized void offerDraw(String gameId, User user) {
        Game game = activeGames.get(gameId);
        if(game == null || game.result != Result.ONGOING) {
            throw new IllegalArgumentException("Game not active.");
        }

        if(!user.username.equals(game.whiteUser) && !user.username.equals(game.blackUser)) {
            throw new IllegalArgumentException("You are not part of this game.");
        }

        if(game.drawOfferedBy != null) {
            throw new IllegalArgumentException("There is already a pending draw.");
        }

        game.drawOfferedBy = user.username;

        ClientHandler opponent = getOpponentHandler(game, user.username);
        if(opponent != null) {
            opponent.sendDrawOffered(gameId, user.username);
        }
    }

    public synchronized void respondDraw(String gameId, User user, boolean accepted) {
        Game game = activeGames.get(gameId);
        if(game == null || game.result != Result.ONGOING) {
            throw new IllegalArgumentException("Game not found or already finished.");
        }

        if(game.drawOfferedBy == null) {
            throw new IllegalArgumentException("No pending draw offered.");
        }

        String offerer = game.drawOfferedBy;
        if(offerer.equals(user.username)) {
            throw new IllegalArgumentException("You cannot respond to your own draw offer.");
        }

        ClientHandler offererHandler = onlineHandlers.get(offerer);
        ClientHandler responderHandler = onlineHandlers.get(user.username);

        if(!accepted) {
            game.drawOfferedBy = null;
            if(offererHandler != null) {
                offererHandler.sendDrawDeclined(gameId, user.username);
            }
            if(responderHandler != null) {
                responderHandler.sendDrawDeclined(gameId, user.username);
            }
        }

        game.drawOfferedBy = null;
        finishGame(game, Result.DRAW, "drawAgreed");
    }

    public synchronized void resign(String gameId, User user) {
        Game game = activeGames.get(gameId);
        if(game == null || game.result != Result.ONGOING) {
            throw new IllegalArgumentException("Game is not active.");
        }

        if(user.username.equals(game.whiteUser)) {
            finishGame(game, Result.BLACK_WIN, "resign");
        } else if (user.username.equals(game.blackUser)) {
            finishGame(game, Result.ONGOING, "resign");
        } else {
            throw new IllegalArgumentException("You are not part of this game.");
        }
    }

    private ClientHandler getOpponentHandler(Game game, String username) {
        String opponent =
                username.equals(game.whiteUser) ? game.blackUser :
                        username.equals(game.blackUser) ? game.whiteUser :
                                null;

        return opponent == null ? null : onlineHandlers.get(opponent);
    }
}