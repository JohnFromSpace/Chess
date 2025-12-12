package com.example.chess.client.controller;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.client.menu.Menu;
import com.example.chess.client.menu.MenuItem;
import com.example.chess.client.model.ClientModel;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;
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

    private void doRegister() {
        String username = view.askLine("Username: ");
        String name     = view.askLine("Name: ");
        String password = view.askLine("Password: ");

        RequestMessage req = RequestMessage
                .of("register")
                .with("username", username)
                .with("name", name)
                .with("password", password);

        try {
            ResponseMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.message);
                return;
            }
            view.showMessage("Registered successfully.");
        } catch (Exception e) {
            view.showError("Failed to register: " + e.getMessage());
        }
    }

    private void doLogin() {
        String username = view.askLine("Username: ");
        String password = view.askLine("Password: ");

        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);

        RequestMessage req = new RequestMessage("login", null, payload);

        try {
            ResponseMessage resp = conn.sendAndWait(req).join();
            if ("loginOk".equals(resp.type)) {
                JsonObject u = resp.payload.getAsJsonObject("user");
                UserModels.User user = new UserModels.User();
                user.username = u.get("username").getAsString();
                user.name = u.get("name").getAsString();
                user.stats.played = u.get("played").getAsInt();
                user.stats.won = u.get("won").getAsInt();
                user.stats.rating = u.get("rating").getAsInt();

                model.setCurrentUser(user);
                view.showMessage("Login successful. Welcome, " + user.name + "!");
            } else if ("error".equals(resp.type)) {
                view.showError(resp.payload.get("message").toString());
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

        RequestMessage req = RequestMessage.of("requestGame");

        conn.sendAndWait(req); // fire-and-forget is fine (gameStarted comes async)
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

        RequestMessage req = RequestMessage.of("getStats");

        try {
            ResponseMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.message);
                return;
            }

            int played = resp.payload.get("played").getAsInt();
            int won    = resp.payload.get("won").getAsInt();
            int drawn  = resp.payload.get("drawn").getAsInt();
            int rating = resp.payload.get("rating").getAsInt();

            view.showMessage("Stats: played=" + played + ", won=" + won + ", drawn=" + drawn + ", rating=" + rating);
        } catch (Exception e) {
            view.showError("Failed to load stats: " + e.getMessage());
        }
    }

    private void listMyGames() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in.");
            return;
        }

        RequestMessage req = RequestMessage.of("listGames");

        try {
            ResponseMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.message);
                return;
            }

            JsonArray games = resp.payload.getAsJsonArray("games");
            if (games == null || games.isEmpty()) {
                view.showMessage("You have no recorded games.");
                return;
            }

            view.showMessage("Your games:");
            for (int i = 0; i < games.size(); i++) {
                JsonObject g = games.get(i).getAsJsonObject();
                String id     = g.get("id").getAsString();
                String opp    = g.get("opponent").getAsString();
                String color  = g.get("color").getAsString();
                String result = g.get("result").getAsString();

                view.showMessage("[" + (i + 1) + "] vs " + opp + " (" + color + "), result=" + result + ", id=" + id);
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
        if (gameId.isEmpty()) return;

        RequestMessage req = RequestMessage.of("getGameDetails").with("gameId", gameId);

        try {
            ResponseMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.message);
                return;
            }

            JsonObject g = resp.payload.getAsJsonObject("game");
            if (g == null) {
                view.showError("Malformed server response: missing game.");
                return;
            }

            view.showMessage("Replay: " + g.get("whiteUser").getAsString()
                    + " vs " + g.get("blackUser").getAsString()
                    + " | result=" + g.get("result").getAsString());

            JsonArray moves = g.getAsJsonArray("moves");
            if (moves == null || moves.isEmpty()) {
                view.showMessage("No moves recorded for this game.");
                return;
            }
            for (int i = 0; i < moves.size(); i++) {
                view.showMessage((i + 1) + ". " + moves.get(i).getAsString());
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
        RequestMessage req = RequestMessage.of("offerDraw").with("gameId", model.getActiveGameId());
        ResponseMessage resp = conn.sendAndWait(req).join();
        if (resp.isError()) view.showError(resp.message);
        else view.showMessage("Draw offer sent.");
    }

    private void resign() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }
        RequestMessage req = RequestMessage.of("resign").with("gameId", model.getActiveGameId());
        ResponseMessage resp = conn.sendAndWait(req).join();
        if (resp.isError()) view.showError(resp.message);
        else {
            view.showMessage("You resigned.");
            model.clearActiveGame();
        }
    }

    @Override
    public void onMessage(ResponseMessage msg) {
        String type = msg.type;
        JsonObject p = msg.payload;

        switch (type) {
            case "gameStarted" -> {
                String gameId = p.get("gameId").getAsString();
                boolean isWhite = "white".equalsIgnoreCase(p.get("color").getAsString());
                String opponent = p.get("opponent").getAsString();

                model.setActiveGameId(gameId);
                model.setWhite(isWhite);
                model.setHasActiveGame(true);

                view.showMessage("Game started vs " + opponent + ". You are " + (isWhite ? "WHITE" : "BLACK") + ".");
                // Optional: if server sends board, then print it. Otherwise client prints local board after moves.
            }

            case "move" -> {
                String moveStr = p.get("move").getAsString();
                boolean whiteInCheck = p.get("whiteInCheck").getAsBoolean();
                boolean blackInCheck = p.get("blackInCheck").getAsBoolean();

                view.showMove(moveStr, whiteInCheck, blackInCheck); // use a signature that exists
            }

            case "gameOver" -> {
                view.showGameOver(p);
                model.clearActiveGame();
            }

            case "info" -> view.showMessage(p.get("message").getAsString());
            case "error" -> view.showError(p.get("message").getAsString());

            default -> System.out.println("[DEBUG] Unhandled async message: " + type);
        }
    }
}


