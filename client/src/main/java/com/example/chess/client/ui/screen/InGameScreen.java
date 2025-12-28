package com.example.chess.client.ui.screen;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

import java.util.ArrayList;
import java.util.List;

public class InGameScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;
    private final List<MenuItem> menuItems = new ArrayList<>();

    public InGameScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
        initMenu();
    }

    private void initMenu() {
        // We build the list manually so we can handle input in our custom loop
        menuItems.add(new MenuItem("Move", this::move));
        menuItems.add(new MenuItem("Offer draw", this::offerDraw));
        menuItems.add(new MenuItem("Accept draw", this::acceptDraw));
        menuItems.add(new MenuItem("Decline draw", this::declineDraw));
        menuItems.add(new MenuItem("Resign", this::resign));
        menuItems.add(new MenuItem("Print board", this::printBoard));
        menuItems.add(new MenuItem("Toggle auto-board", this::toggleAutoBoard));
        menuItems.add(new MenuItem("Back to lobby", this::backToLobby));
        menuItems.add(new MenuItem("Exit program", () -> System.exit(0)));
    }

    @Override
    public void show() {
        // Initial render
        renderGameStatus();
        printMenuOptions();

        while (state.getUser() != null && state.isInGame()) {
            state.drainUi();

            if (!state.isInGame()) {
                break;
            }
            state.tickClocks();

            String line = view.pollLine(100);

            if (line != null) {
                processInput(line);
                if (state.isInGame()) {
                    renderGameStatus();
                    printMenuOptions();
                }
            }
        }
    }

    private void renderGameStatus() {
        view.showMessage(renderClocksLine());
        view.showMessage("(Auto-board: " + (state.isAutoShowBoard() ? "ON" : "OFF") + ")");
    }

    private void printMenuOptions() {
        view.showMessage("\n=== Game Menu ===");
        for (int i = 0; i < menuItems.size(); i++) {
            view.showMessage((i + 1) + ") " + menuItems.get(i).getLabel());
        }
        view.showMessage("Choose: "); // Prompt
    }

    private void processInput(String line) {
        line = line.trim();
        if (line.isEmpty()) return;

        try {
            int choice = Integer.parseInt(line);
            if (choice < 1 || choice > menuItems.size()) {
                view.showError("Invalid choice.");
            } else {
                // Execute command
                menuItems.get(choice - 1).getCommand().execute();
            }
        } catch (NumberFormatException e) {
            view.showError("Please enter a number.");
        }
    }

    // --- Action Methods (Unchanged) ---

    private void offerDraw() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); return; }
        var status = conn.offerDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw offer sent.");
    }

    private void resign() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); return; }
        var status = conn.resign(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Resigned.");
        state.clearGame();
    }

    private void backToLobby() {
        String gameId = state.getActiveGameId();
        if (gameId != null && !gameId.isBlank()) {
            var status = conn.resign(gameId).join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Left game (counted as resignation). Returning to lobby...");
        }
        state.clearGame();
    }

    private void toggleAutoBoard() {
        state.setAutoShowBoard(!state.isAutoShowBoard());
        view.showMessage("Auto-board is now " + (state.isAutoShowBoard() ? "ON" : "OFF"));
    }

    private void printBoard() {
        String b = state.getLastBoard();
        if (b == null || b.isBlank()) {
            view.showMessage("No board received yet.");
            return;
        }
        var youCap = state.isWhite() ? state.getCapturedByWhite() : state.getCapturedByBlack();
        var oppCap = state.isWhite() ? state.getCapturedByBlack() : state.getCapturedByWhite();
        view.showBoardWithCaptured(b, youCap, oppCap);
    }

    private String renderClocksLine() {
        String w = fmt(state.getWhiteTimeMs());
        String b = fmt(state.getBlackTimeMs());
        String turn = state.isWhiteToMove() ? "WHITE to move" : "BLACK to move";
        return "[Clock] White: " + w + " | Black: " + b + " | " + turn;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long m = s / 60;
        long r = s % 60;
        return String.format("%02d:%02d", m, r);
    }

    private void move() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); return; }

        // Note: askLine here will block until input is given, which is fine
        // because the user has explicitly chosen to "Move".
        String raw = view.askLine("Enter move (e2e4 / e7e8q). Captures: e5e4 or e5xe4: ");
        if (raw == null) { view.showError("Empty move."); return; }

        String move;
        try {
            move = sanitizeMove(raw);
        } catch (IllegalArgumentException ex) {
            view.showError(ex.getMessage());
            return;
        }

        var status = conn.makeMove(gameId, move).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Move sent.");
    }

    private static String sanitizeMove(String raw) {
        String s = raw.trim().toLowerCase();
        if (s.isBlank()) throw new IllegalArgumentException("Empty move.");
        s = s.replaceAll("[\\s\\-x=:+]", "");
        if (s.length() != 4 && s.length() != 5) {
            throw new IllegalArgumentException("Bad move format. Use e2e4 or e7e8q.");
        }
        char f1 = s.charAt(0), r1 = s.charAt(1), f2 = s.charAt(2), r2 = s.charAt(3);
        if (f1 < 'a' || f1 > 'h' || f2 < 'a' || f2 > 'h' || r1 < '1' || r1 > '8' || r2 < '1' || r2 > '8') {
            throw new IllegalArgumentException("Bad squares in move: " + raw);
        }
        if (s.length() == 5) {
            char p = s.charAt(4);
            if (p != 'q' && p != 'r' && p != 'b' && p != 'n') {
                throw new IllegalArgumentException("Bad promotion piece: " + p + " (use q/r/b/n)");
            }
        }
        return s;
    }

    private void acceptDraw() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); return; }
        var status = conn.acceptDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw accepted.");
    }

    private void declineDraw() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); return; }
        var status = conn.declineDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw declined.");
    }
}