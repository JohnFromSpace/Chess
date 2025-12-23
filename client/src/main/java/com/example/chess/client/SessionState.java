package com.example.chess.client;

import com.example.chess.common.UserModels.User;

public class SessionState {
    private User user;
    private String activeGameId;
    private boolean inGame;
    private boolean isWhite;

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
    }
}