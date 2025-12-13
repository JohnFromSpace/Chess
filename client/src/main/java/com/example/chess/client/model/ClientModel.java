package com.example.chess.client.model;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;

import java.util.ArrayList;
import java.util.List;

public class ClientModel {

    private User currentUser;

    private String activeGameId;

    // extra context for UX
    private boolean playingAsWhite;
    private String opponent;

    private List<Game> myGames = new ArrayList<>();

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public String getActiveGameId() {
        return activeGameId;
    }

    public void setActiveGameId(String gameId) {
        this.activeGameId = gameId;
    }

    public void clearActiveGame() {
        this.activeGameId = null;
        this.playingAsWhite = false;
        this.opponent = null;
    }

    public boolean hasActiveGame() {
        return activeGameId != null;
    }

    public boolean isPlayingAsWhite() {
        return playingAsWhite;
    }

    public String getOpponent() {
        return opponent;
    }

    public void setGameContext(boolean playingAsWhite, String opponent) {
        this.playingAsWhite = playingAsWhite;
        this.opponent = opponent;
    }

    public List<Game> getMyGames() {
        return myGames;
    }

    public void setMyGames(List<Game> myGames) {
        this.myGames = (myGames != null ? myGames : new ArrayList<>());
    }
}