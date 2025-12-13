package com.example.chess.client.controller;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.client.menu.Menu;
import com.example.chess.client.menu.MenuItem;
import com.example.chess.client.model.ClientModel;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;
import com.example.chess.common.proto.Payload;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.StatusMessage;

import java.util.List;
import java.util.Map;

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

    private static String asString(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static long asLong(Map<String, Object> p, String k, long def) {
        Object v = p.get(k);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static boolean asBool(Map<String, Object> p, String k, boolean def) {
        Object v = p.get(k);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private void doRegister() {
        String username = view.askLine("Username: ");
        String name     = view.askLine("Name: ");
        String password = view.askLine("Password: ");

        RequestMessage req = RequestMessage.of("register")
                .with("username", username)
                .with("name", name)
                .with("password", password);

        try {
            StatusMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.getMessage());
                return;
            }
            if (!"registerOk".equals(resp.type)) {
                view.showError("Unexpected response: " + resp.type);
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

        RequestMessage req = RequestMessage.of("login")
                .with("username", username)
                .with("password", password);

        try {
            StatusMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.getMessage());
                return;
            }
            if (!"loginOk".equals(resp.type)) {
                view.showError("Unexpected response: " + resp.type);
                return;
            }

            Map<String, Object> u = Payload.map(resp.payload.get("user"));
            if (u == null) {
                view.showError("Malformed response: missing user.");
                return;
            }

            UserModels.User user = new UserModels.User();
            user.username = Payload.str(u.get("username"));
            user.name     = Payload.str(u.get("name"));
            user.stats.played = Payload.intVal(u.get("played"));
            user.stats.won    = Payload.intVal(u.get("won"));
            user.stats.drawn  = Payload.intVal(u.getOrDefault("drawn", 0));
            user.stats.rating = Payload.intVal(u.getOrDefault("rating", 1200));

            model.setCurrentUser(user);
            view.showMessage("Login successful. Welcome, " + user.name + "!");
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
        conn.sendAndWait(req); // gameStarted will arrive async
        view.showMessage("Requested a game. Waiting for pairing...");
    }

    private void doMove() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }

        String move = view.askLine("Enter move (e2e4), empty to cancel: ").trim();
        if (move.isEmpty()) return;

        RequestMessage msg = RequestMessage.of("makeMove")
                .with("gameId", model.getActiveGameId())
                .with("move", move);

        StatusMessage resp = conn.sendAndWait(msg).join();
        if (resp.isError()) view.showError(resp.getMessage());
        else view.showMessage("Move sent.");
    }

    private void offerDraw() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }
        RequestMessage req = RequestMessage.of("offerDraw").with("gameId", model.getActiveGameId());
        StatusMessage resp = conn.sendAndWait(req).join();
        if (resp.isError()) view.showError(resp.getMessage());
        else view.showMessage("Draw offer sent.");
    }

    private void resign() {
        if (!model.hasActiveGame()) {
            view.showError("You have no active game.");
            return;
        }
        RequestMessage req = RequestMessage.of("resign").with("gameId", model.getActiveGameId());
        StatusMessage resp = conn.sendAndWait(req).join();
        if (resp.isError()) view.showError(resp.getMessage());
        else {
            view.showMessage("You resigned.");
            model.clearActiveGame();
        }
    }

    private void logout() {
        model.clearActiveGame();
        model.setCurrentUser(null);
        view.showMessage("Logged out.");
    }

    private void showMyStats() {
        if (!model.isLoggedIn()) {
            view.showError("You must be logged in.");
            return;
        }

        RequestMessage req = RequestMessage.of("getStats");

        try {
            StatusMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.getMessage());
                return;
            }

            int played = Payload.intVal(resp.payload.get("played"));
            int won    = Payload.intVal(resp.payload.get("won"));
            int drawn  = Payload.intVal(resp.payload.get("drawn"));
            int rating = Payload.intVal(resp.payload.get("rating"));

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
            StatusMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.getMessage());
                return;
            }

            var games = Payload.list(resp.payload.get("games"));
            if (games == null || games.isEmpty()) {
                view.showMessage("You have no recorded games.");
                return;
            }

            view.showMessage("Your games:");
            for (int i = 0; i < games.size(); i++) {
                var g = Payload.map(games.get(i));
                String id = Payload.str(g.get("id"));
                String opp = Payload.str(g.get("opponent"));
                String color = Payload.str(g.get("color"));
                String result = Payload.str(g.get("result"));
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
            StatusMessage resp = conn.sendAndWait(req).join();
            if (resp.isError()) {
                view.showError(resp.getMessage());
                return;
            }

            var g = Payload.map(resp.payload.get("game"));
            if (g == null) {
                view.showError("Malformed server response: missing game.");
                return;
            }

            view.showMessage("Replay: " + Payload.str(g.get("whiteUser"))
                    + " vs " + Payload.str(g.get("blackUser"))
                    + " | result=" + Payload.str(g.get("result")));

            var moves = Payload.list(g.get("moves"));
            if (moves == null || moves.isEmpty()) {
                view.showMessage("No moves recorded for this game.");
                return;
            }
            for (int i = 0; i < moves.size(); i++) {
                view.showMessage((i + 1) + ". " + Payload.str(moves.get(i)));
            }
        } catch (Exception e) {
            view.showError("Failed to replay game: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(StatusMessage msg) {
        String type = msg.type;
        Map<String, Object> p = msg.payload;

        switch (type) {
            case "gameStarted" -> {
                String gameId = asString(p, "gameId");
                boolean isWhite = "white".equalsIgnoreCase(asString(p, "color"));
                String opponent = asString(p, "opponent");
                boolean resumed = asBool(p, "resumed", false);

                long wMs = asLong(p, "whiteTimeMs", -1);
                long bMs = asLong(p, "blackTimeMs", -1);
                boolean whiteToMove = asBool(p, "whiteToMove", true);

                model.setActiveGameId(gameId);
                model.setGameContext(isWhite, opponent);

                String head = resumed ? "Resumed game" : "Game started";
                view.showMessage(head + " vs " + opponent + ". You are " + (isWhite ? "WHITE" : "BLACK") + ".");
                if (wMs >= 0 && bMs >= 0) {
                    view.showMessage("Clocks: White=" + (wMs/1000) + "s, Black=" + (bMs/1000) + "s. To move: " + (whiteToMove ? "White" : "Black"));
                }
            }

            case "move" -> {
                String moveStr = asString(p, "move");
                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);
                view.showMove(moveStr, whiteInCheck, blackInCheck);
            }

            case "gameOver" -> {
                view.showGameOver(asString(p, "result"), asString(p, "reason"));
                model.clearActiveGame();
            }

            case "info" -> view.showMessage(asString(p, "message"));
            case "error" -> view.showError(asString(p, "message"));

            default -> view.showMessage("[DEBUG] Unhandled async message: " + type);
        }
    }
}