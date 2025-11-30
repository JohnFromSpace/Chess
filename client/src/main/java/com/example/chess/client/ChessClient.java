package com.example.chess.client;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.common.GameModels;
import com.example.chess.common.GameModels.Board;
import com.example.chess.common.Msg;
import com.example.chess.common.UserModels;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.UUID;

public class ChessClient implements ClientMessageListener {

    private final ConsoleView view;
    private final ClientConnection connection;

    private String currentUsername;
    private String currentGameId;
    private boolean isWhite;
    private Board localBoard;

    public ChessClient(String host, int port) throws IOException {
        this.view = new ConsoleView();
        this.connection = new ClientConnection(host, port, this);
        this.connection.start();
    }

    public void run() {
        view.showWelcome();

        boolean running = true;
        while (running) {
            view.showMainMenu();
            int choice = view.readInt();
            switch (choice) {
                case 1 -> doRegister();
                case 2 -> doLogin();
                case 3 -> doViewStats();
                case 4 -> doRequestGame();
                case 5 -> doOfferDraw();
                case 6 -> doResign();
                case 7 -> doListGames();
                case 8 -> doReplayGame();
                case 0 -> running = false;
                default -> view.showError("Unknown choice.");
            }
        }

        connection.stop();
    }

    private String newCorrId() {
        return UUID.randomUUID().toString();
    }

    private void doRegister() {
        String username = view.prompt("Username");
        String name = view.prompt("Name");
        String password = view.prompt("Password");

        JsonObject msg = Msg.obj("register", newCorrId());
        msg.addProperty("username", username);
        msg.addProperty("name", name);
        msg.addProperty("password", password);
        connection.send(msg);
    }

    private void doLogin() {
        String username = view.prompt("Username");
        String password = view.prompt("Password");

        JsonObject msg = Msg.obj("login", newCorrId());
        msg.addProperty("username", username);
        msg.addProperty("password", password);
        connection.send(msg);
    }

    private void doViewStats() {
        if (currentUsername == null) {
            view.showError("You must be logged in.");
            return;
        }
        JsonObject msg = Msg.obj("getStats", newCorrId());
        msg.addProperty("username", currentUsername);
        connection.send(msg);
    }

    private void doRequestGame() {
        if (currentUsername == null) {
            view.showError("You must be logged in.");
            return;
        }
        JsonObject msg = Msg.obj("requestGame", newCorrId());
        connection.send(msg);
    }

    private void doOfferDraw() {
        if (currentGameId == null) {
            view.showError("No active game.");
            return;
        }
        JsonObject msg = Msg.obj("offerDraw", newCorrId());
        msg.addProperty("gameId", currentGameId);
        connection.send(msg);
    }

    private void doResign() {
        if (currentGameId == null) {
            view.showError("No active game.");
            return;
        }
        JsonObject msg = Msg.obj("resign", newCorrId());
        msg.addProperty("gameId", currentGameId);
        connection.send(msg);
    }

    private void doListGames() {
        if (currentUsername == null) {
            view.showError("You must be logged in.");
            return;
        }
        JsonObject msg = Msg.obj("listGames", newCorrId());
        msg.addProperty("username", currentUsername);
        connection.send(msg);
    }

    private void doReplayGame() {
        if (currentUsername == null) {
            view.showError("You must be logged in.");
            return;
        }
        String id = view.prompt("Game id");
        JsonObject msg = Msg.obj("getGame", newCorrId());
        msg.addProperty("gameId", id);
        connection.send(msg);
    }

    public synchronized void makeMove(String gameId, UserModels.User user, String moveStr){

    }

    @Override
    public void onMessage(JsonObject msg) {
        String type = msg.get("type").getAsString();
        switch (type) {
            case "error" -> {
                String message = msg.has("message")
                        ? msg.get("message").getAsString()
                        : "Unknown server error.";
                view.showError(message);
            }
            case "info" -> {
                String message = msg.has("message")
                        ? msg.get("message").getAsString()
                        : "";
                view.showInfo(message);
            }
            case "registerOk" -> view.showInfo("Registration successful.");
            case "loginOk" -> {
                JsonObject u = msg.getAsJsonObject("user");
                currentUsername = u.get("username").getAsString();
                int played = u.get("played").getAsInt();
                int won = u.get("won").getAsInt();
                int rating = u.get("rating").getAsInt();
                view.showInfo("Logged in as " + currentUsername +
                        " (played=" + played + ", won=" + won + ", rating=" + rating + ").");
            }
            case "stats" -> {
                int played = msg.get("played").getAsInt();
                int won = msg.get("won").getAsInt();
                int drawn = msg.get("drawn").getAsInt();
                int rating = msg.get("rating").getAsInt();
                view.showInfo("Stats: played=" + played + ", won=" + won +
                        ", drawn=" + drawn + ", rating=" + rating);
            }
            case "gameStarted" -> {
                currentGameId = msg.get("gameId").getAsString();
                String color = msg.get("color").getAsString();
                String opponent = msg.get("opponent").getAsString();
                isWhite = "white".equalsIgnoreCase(color);
                // localBoard = Board.createInitial();
                view.showGameStarted(color, opponent);
                if (localBoard != null) {
                    view.printBoard(localBoard);
                }
            }
            case "move" -> {
                String moveStr = msg.get("move").getAsString();
                boolean whiteInCheck = msg.get("whiteInCheck").getAsBoolean();
                boolean blackInCheck = msg.get("blackInCheck").getAsBoolean();

                view.showMove(moveStr, whiteInCheck, blackInCheck);
            }
            case "gameOver" -> {
                String result = msg.get("result").getAsString();
                String reason = msg.has("reason") ? msg.get("reason").getAsString() : "";
                view.showGameOver(result, reason);
                currentGameId = null;
                localBoard = null;
            }
            case "drawOffered" -> {
                String from = msg.get("from").getAsString();
                view.showDrawOffered(from);
            }
            case "drawDeclined" -> {
                String by = msg.get("by").getAsString();
                view.showInfo("Draw offer declined by " + by + ".");
            }
            case "gamesList" -> {
                view.showInfo("Games: " + msg.toString());
            }
            case "gameData" -> {
                view.showInfo("Game: " + msg.toString());
            }
            default -> System.out.println("[SERVER] " + msg.toString());
        }
    }
}

