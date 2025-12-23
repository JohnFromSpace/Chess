package com.example.chess.client;

import com.example.chess.common.UserModels.User;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SessionState {
    private User user;
    private String activeGameId;
    private boolean inGame;
    private boolean isWhite;

    private String lastBoard;

    private final Queue<Runnable> uiActions = new ConcurrentLinkedQueue<>();

    public String getLastBoard() { return lastBoard; }
    public void setLastBoard(String lastBoard) { this.lastBoard = lastBoard; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getActiveGameId() { return activeGameId; }
    public void setActiveGameId(String activeGameId) { this.activeGameId = activeGameId; }

    public boolean isInGame() { return inGame; }
    public void setInGame(boolean inGame) { this.inGame = inGame; }

    public boolean isWhite() { return isWhite; }
    public void setWhite(boolean white) { isWhite = white; }

    public void clearGame() {
        this.activeGameId = null;
        this.inGame = false;
        this.isWhite = false;
        this.lastBoard = null;
    }

    public void postUi(Runnable r) {
        if (r != null) uiActions.add(r);
    }

    public void drainUi() {
        Runnable r;
        while ((r = uiActions.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }
}