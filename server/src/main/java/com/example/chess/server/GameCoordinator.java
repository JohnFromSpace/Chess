package com.example.chess.server;

import com.example.chess.common.GameModels.Game;
import com.example.chess.common.GameModels.Result;
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
}

