package com.example.chess.client.controller;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.client.menu.Menu;
import com.example.chess.client.menu.MenuItem;
import com.example.chess.client.model.ClientModel;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;

public class ClientController implements ClientMessageListener {

    private final ClientModel model;
    private final ConsoleView view;
    private final ClientConnection conn;

    private final Menu loggedOutMenu;
    private final Menu lobbyMenu;
    private final Menu inGameMenu;

    public ClientController(ClientModel model, ConsoleView view, ClientConnection conn) {
        this.model = model;
        this.view = view;
        this.conn = conn;

        this.loggedOutMenu = buildLoggedOutMenu();
        this.lobbyMenu = buildLobbyMenu();
        this.inGameMenu = buildInGameMenu();
    }

    private Menu buildLoggedOutMenu() {
        return new Menu("Main",
                List.of(
                        new MenuItem(1, "Register", () -> { doRegister(); return true; }),
                        new MenuItem(2, "Login",    () -> { doLogin();    return true; })
                ));
    }

    private Menu buildLobbyMenu() {
        return new Menu("Lobby",
                List.of(
                        new MenuItem(1, "Request game", () -> { requestGame(); return true; }),
                        new MenuItem(2, "My stats",      () -> { showMyStats(); return true; }),
                        new MenuItem(3, "My games",      () -> { listMyGames(); return true; }),
                        new MenuItem(4, "Replay game",   () -> { replayGame();  return true; }),
                        new MenuItem(5, "Logout",        () -> { logout();      return true; })
                ));
    }

    private Menu buildInGameMenu() {
        return new Menu("Game",
                List.of(
                        new MenuItem(1, "Make move",     () -> { doMove();    return true; }),
                        new MenuItem(2, "Offer draw",    () -> { offerDraw(); return true; }),
                        new MenuItem(3, "Resign",        () -> { resign();    return true; })
                ));
    }

    public void run() {
        view.showMessage("Welcome to Chess client!");

        boolean running = true;
        while (running) {
            if (!model.isLoggedIn()) {
                running = loggedOutMenu.showAndHandle(view);
            } else if (!model.hasActiveGame()) {
                running = lobbyMenu.showAndHandle(view);
            } else {
                running = inGameMenu.showAndHandle(view);
            }
        }

        view.showMessage("Goodbye!");
    }
    private String newCorrId() {
        return UUID.randomUUID().toString();
    }

    private boolean handleLoggedOutMenu(int choice) {
        return switch (choice) {
            case 1 -> { doRegister(); yield true; }
            case 2 -> { doLogin();    yield true; }
            case 0 -> false;
            default -> { view.showError("Invalid choice."); yield true; }
        };
    }

    private boolean handleLobbyMenu(int choice) {
        switch (choice) {
            case 1 -> requestGame();
            case 2 -> showMyStats();
            case 3 -> listMyGames();
            case 4 -> replayGame();
            case 0 -> logout();
            default -> view.showError("Invalid choice.");
        }
        return true;
    }

    private boolean handleInGameMenu(int choice) {
        switch (choice) {
            case 1 -> doMove();
            case 2 -> offerDraw();
            case 3 -> resign();
            case 0 -> leaveGame();
            default -> view.showError("Invalid choice.");
        }
        return true;
    }

    private void doRegister() {
        String username = view.askLine("Username: ");
        String name     = view.askLine("Name: ");
        String password = view.askLine("Password: ");

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "register");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("username", username);
        msg.addProperty("name", name);
        msg.addProperty("password", password);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();
            if ("registerOk".equals(type)) {
                view.showMessage("Registered successfully.");
            } else if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
            } else {
                view.showError("Unexpected server response: " + type);
            }
        } catch (Exception e) {
            view.showError("Failed to register: " + e.getMessage());
        }
    }

    private void doLogin() {
        String username = view.askLine("Username: ");
        String password = view.askLine("Password: ");

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "login");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("username", username);
        msg.addProperty("password", password);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();
            if ("loginOk".equals(type)) {
                JsonObject u = resp.getAsJsonObject("user");
                com.example.chess.common.UserModels.User user =
                        new com.example.chess.common.UserModels.User();
                user.username = u.get("username").getAsString();
                user.name = u.get("name").getAsString();
                user.stats.played = u.get("played").getAsInt();
                user.stats.won = u.get("won").getAsInt();
                user.stats.rating = u.get("rating").getAsInt();
                model.setCurrentUser(user);
                view.showMessage("Login successful. Welcome, " + user.name + "!");
            } else if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
            } else {
                view.showError("Unexpected server response: " + type);
            }
        } catch (Exception e) {
            view.showError("Failed to login: " + e.getMessage());
        }
    }

    private void requestGame() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in to request a game.");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "requestGame");
        msg.addProperty("corrId", newCorrId());

        conn.sendAndWait(msg);
        view.showMessage("Requested a game. Waiting for pairing...");
    }

    private void logout() {
        if (!model.isLoggedIn()) {
            view.showError("You are not logged in.");
            return;
        }
        model.clearActiveGame();
        model.setCurrentUser(null);
        view.showMessage("Logged out.");
    }

    private void leaveGame() {
        if (!model.hasActiveGame()) {
            view.showError("No active game to leave.");
            return;
        }
        model.clearActiveGame();
        view.showMessage("Left the game locally. The server game continues until finished.");
    }

    private void showMyStats() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in.");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "getStats");
        msg.addProperty("corrId", newCorrId());

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();
            if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
                return;
            }
            if (!"stats".equals(type)) {
                view.showError("Unexpected server response: " + type);
                return;
            }
            int played = resp.get("played").getAsInt();
            int won    = resp.get("won").getAsInt();
            int drawn  = resp.get("drawn").getAsInt();
            int rating = resp.get("rating").getAsInt();
            view.showMessage(
                    "Stats: played=" + played +
                            ", won=" + won +
                            ", drawn=" + drawn +
                            ", rating=" + rating
            );
        } catch (Exception e) {
            view.showError("Failed to load stats: " + e.getMessage());
        }
    }

    private void listMyGames() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in.");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "listGames");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("username", model.getCurrentUser().username);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();

            if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
                return;
            }
            if (!"gamesList".equals(type)) {
                view.showError("Unexpected server response: " + type);
                return;
            }

            JsonArray games = resp.getAsJsonArray("games");
            if (games == null || games.isEmpty()) {
                view.showMessage("You have no recorded games.");
                return;
            }

            view.showMessage("Your games:");
            for (int i = 0; i < games.size(); i++) {
                JsonObject g = games.get(i).getAsJsonObject();
                String id       = g.get("id").getAsString();
                String opponent = g.get("opponent").getAsString();
                String color    = g.get("color").getAsString();
                String result   = g.get("result").getAsString();

                String line = String.format(
                        "[%d] %s vs %s (%s), result=%s, id=%s",
                        i + 1,
                        model.getCurrentUser().username,
                        opponent,
                        color,
                        result,
                        id
                );
                view.showMessage(line);
            }
        } catch (Exception e) {
            view.showError("Failed to load games: " + e.getMessage());
        }
    }

    private void replayGame() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in to replay a game.");
            return;
        }

        String gameId = view.askLine("Enter game id to replay (empty to cancel): ").trim();
        if (gameId.isEmpty()) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "getGameDetails");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("gameId", gameId);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();

            if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
                return;
            }
            if (!"gameData".equals(type)) {
                view.showError("Unexpected server response: " + type);
                return;
            }

            JsonObject g = resp.getAsJsonObject("game");
            if (g == null) {
                view.showError("Malformed server response: missing game.");
                return;
            }

            String white   = g.get("whiteUser").getAsString();
            String black   = g.get("blackUser").getAsString();
            String result  = g.get("result").getAsString();
            String created = g.get("createdAt").getAsString();

            view.showMessage(String.format(
                    "Replay %s vs %s | result=%s | created=%s",
                    white, black, result, created
            ));

            JsonArray moves = g.getAsJsonArray("moves");
            if (moves == null || moves.isEmpty()) {
                view.showMessage("No moves recorded for this game.");
                return;
            }

            for (int i = 0; i < moves.size(); i++) {
                String m = moves.get(i).getAsString();
                view.showMessage(String.format("%d. %s", i + 1, m));
            }

        } catch (Exception e) {
            view.showError("Failed to replay game: " + e.getMessage());
        }
    }

    private void doMove() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }

        String move = view.askLine("Enter move in long algebraic notation (e2e4), empty to cancel: ").trim();
        if (move.isEmpty()) {
            return;
        }

        String gameId = model.getActiveGameId();
        if (gameId == null) {
            view.showError("Internal error: no active game id.");
            return;
        }

        RequestMessage msg = RequestMessage
                .of("move")
                .with("gameId", gameId)
                .with("move", move);

        ResponseMessage resp = conn.sendAndWait(msg).join();
        if (resp.isError()) {
            view.showError(resp.message);
        } else {
            view.showMessage("Move sent.");
        }
    }

    private void offerDraw() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }

        String gameId = model.getActiveGameId();
        if (gameId == null) {
            view.showError("Internal error: no active game id.");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "offerDraw");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("gameId", gameId);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();

            if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
                return;
            }

            view.showMessage("Draw offer sent. Waiting for opponent response...");

        } catch (Exception e) {
            view.showError("Failed to offer draw: " + e.getMessage());
        }
    }

    private void resign() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }

        String gameId = model.getActiveGameId();
        if (gameId == null) {
            view.showError("Internal error: no active game id.");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "resign");
        msg.addProperty("corrId", newCorrId());
        msg.addProperty("gameId", gameId);

        try {
            JsonObject resp = conn.sendAndWait(msg).join();
            String type = resp.get("type").getAsString();

            if ("error".equals(type)) {
                view.showError(resp.get("message").getAsString());
                return;
            }

            view.showMessage("You resigned.\n");
            model.clearActiveGame();

        } catch (Exception e) {
            view.showError("Failed to resign: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(ResponseMessage msg) {
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
                if (!message.isEmpty()) {
                    view.showMessage(message);
                }
            }
            case "registerOk" -> view.showMessage("Registration successful.");
            case "loginOk" -> view.showMessage("Login successful.");
            case "gameStarted" -> {
                String gameId = msg.get("gameId").getAsString();
                String color = msg.get("color").getAsString();
                String opponent = msg.get("opponent").getAsString();
                model.setActiveGameId(gameId);
                view.showGameStarted(color, opponent);
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
                model.clearActiveGame();
            }
            case "drawOffered" -> {
                String from = msg.get("from").getAsString();
                view.showDrawOffered(from);
            }
            case "drawDeclined" -> {
                String by = msg.get("by").getAsString();
                view.showMessage("Draw offer declined by " + by + ".");
            }
            case "gamesList", "gameData", "stats" -> {
            }
            default -> view.showMessage("[SERVER] " + msg);
        }
    }
}


