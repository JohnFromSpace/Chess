package com.example.chess.client.model;

import com.example.chess.common.UserModels.User;

import java.util.ArrayList;
import java.util.List;

public class ClientModel {

    private User currentUser;

    private String activeGameId;
    private Game activeGame;

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

    public Game getActiveGame() {
        return activeGame;
    }

    public void setActiveGame(Game game) {
        this.activeGame = game;
        this.activeGameId = (game != null ? game.id : null);
    }

    public void setActiveGameId(String gameId) {
        this.activeGameId = gameId;
    }

    public void clearActiveGame() {
        this.activeGame = null;
        this.activeGameId = null;
    }

    public boolean hasActiveGame() {
        return activeGameId != null;
    }

    public List<Game> getMyGames() {
        return myGames;
    }

    public void setMyGames(List<Game> myGames) {
        this.myGames = (myGames != null ? myGames : new ArrayList<>());
    }
}

