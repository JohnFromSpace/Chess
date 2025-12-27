package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;

final class GameContext {
    final Game game;

    volatile ClientHandler white;
    volatile ClientHandler black;

    volatile long whiteOfflineAtMs = 0L;
    volatile long blackOfflineAtMs = 0L;

    GameContext(Game game, ClientHandler white, ClientHandler black) {
        this.game = game;
        this.white = white;
        this.black = black;
    }

    boolean isWhiteUser(String username) {
        return username != null && username.equals(game.getWhiteUser());
    }

    boolean isParticipant(String username) {
        return username != null && (username.equals(game.getWhiteUser()) || username.equals(game.getBlackUser()));
    }

    ClientHandler handlerOf(String username) {
        return isWhiteUser(username) ? white : black;
    }

    ClientHandler opponentHandlerOf(String username) {
        return isWhiteUser(username) ? black : white;
    }
}