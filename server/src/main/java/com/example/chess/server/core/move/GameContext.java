package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;

final class GameContext {
    private final Game game;

    private volatile ClientHandler white;
    private volatile ClientHandler black;

    private volatile long whiteOfflineAtMs = 0L;
    private volatile long blackOfflineAtMs = 0L;

    GameContext(Game game, ClientHandler white, ClientHandler black) {
        this.game = game;
        this.white = white;
        this.black = black;
    }

    Game getGame() {
        return game;
    }

    ClientHandler getWhiteHandler() {
        return white;
    }

    void setWhiteHandler(ClientHandler white) {
        this.white = white;
    }

    ClientHandler getBlackHandler() {
        return black;
    }

    void setBlackHandler(ClientHandler black) {
        this.black = black;
    }

    long getWhiteOfflineAtMs() {
        return whiteOfflineAtMs;
    }

    void setWhiteOfflineAtMs(long whiteOfflineAtMs) {
        this.whiteOfflineAtMs = whiteOfflineAtMs;
    }

    long getBlackOfflineAtMs() {
        return blackOfflineAtMs;
    }

    void setBlackOfflineAtMs(long blackOfflineAtMs) {
        this.blackOfflineAtMs = blackOfflineAtMs;
    }

    boolean isWhiteUser(String username) {
        return username != null && username.equals(game.getWhiteUser());
    }

    boolean isParticipant(String username) {
        if (username == null) return false;
        return username.equals(game.getWhiteUser()) || username.equals(game.getBlackUser());
    }

    ClientHandler handlerOf(String username) {
        return isWhiteUser(username) ? white : black;
    }

    ClientHandler opponentHandlerOf(String username) {
        return isWhiteUser(username) ? black : white;
    }
}
