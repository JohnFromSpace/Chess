package com.example.chess.server;

import com.example.chess.common.GameModels.Result;
import com.example.chess.common.GameModels.Game;
import com.example.chess.common.GameModels.Move;
import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.util.*;

public class GameCoordinator {
    private final FileStores fileStores;
    private final Map<String, User> onlineUsers =  new HashMap<>();
    private final Map<String, ClientHandler> onlineHandlers = new HashMap<>();
    private final Deque<ClientHandler> waitingQueue = new ArrayDeque<>();
    private final Map<String, Game> activeGames = new HashMap<>();

    public GameCoordinator(FileStores fileStores) {
        this.fileStores = fileStores;
    }

    public synchronized void onUserOnline(ClientHandler handler, User user) {
        onlineUsers.put(user.username, user);
        onlineHandlers.put(user.username, handler);
    }

    public synchronized void onUserOffline(ClientHandler handler, User user) {
        if (user == null) {
            return;
        }

        onlineHandlers.remove(user.username);
        onlineUsers.remove(user.username);
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

    public synchronized Optional<Game> getGame(String gameId) {
        return Optional.ofNullable(activeGames.get(gameId));
    }

    public synchronized void makeMove(String gameId, User user, String moveStr){
        Game game = activeGames.get(gameId);

        if(game == null) {
            throw new IllegalArgumentException("Game not found.");
        }

        boolean isWhite = game.whiteUser.equals(user.username);
        if(isWhite != game.whiteMove){
            throw new IllegalArgumentException("Not your turn.");
        }

        Move move = Move.parse(moveStr);

        char piece = game.board.get(move.fromRow, move.fromCol);
        if(piece == '.') {
            throw new IllegalArgumentException("No piece on this square.");
        }

        if(isWhite && !Character.isUpperCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }

        if(!isWhite && !Character.isUpperCase(piece)) {
            throw new IllegalArgumentException("That's not your piece.");
        }

        if(!isPseudoLegal(piece, move)) {
            throw new IllegalArgumentException("Illegal move: " + moveStr);
        }

        game.board.set(move.toRow, move.toCol, piece);
        game.board.set(move.fromRow, move.fromCol, piece);

        game.whiteMove = !game.whiteMove;

        fileStores.saveGame(game);

        ClientHandler white = onlineHandlers.get(game.whiteUser);
        ClientHandler black = onlineHandlers.get(game.blackUser);

        if(white != null) {
            white.sendMove(gameId, moveStr);
        }

        if(black != null) {
            black.sendMove(gameId, moveStr);
        }
    }

    private boolean isPseudoLegal(char piece, Move m) {
        piece = Character.toLowerCase(piece);

        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);

        switch (piece) {
            case 'p': return dy == 1 && dx == 0;
            case 'n': return dx * dx + dy * dy == 5;
            case 'b': return dx == dy;
            case 'r': return dx == 0 || dy == 0;
            case 'q': return dx == dy || dx == 0 || dy == 0;
            case 'k': return Math.max(dx, dy) == 1;
            default:
                return false;
        }
    }
}

