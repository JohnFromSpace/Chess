package com.example.chess.client.ui.screen;

import com.example.chess.client.SessionState;
import com.example.chess.client.controller.GameUIOrchestrator;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

public class InGameScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public InGameScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Game");
        menu.add(new MenuItem("Move", this::move));
        menu.add(new MenuItem("Offer draw", this::offerDraw));
        menu.add(new MenuItem("Resign", this::resign));
        menu.add(new MenuItem("Print board", this::printBoard));
        menu.add(new MenuItem("Toggle auto-board", this::toggleAutoBoard));
        menu.add(new MenuItem("Back to lobby", this::backToLobby));
        menu.add(new MenuItem("Exit program", () -> System.exit(0)));

        while (state.getUser() != null && state.isInGame()) {
            state.drainUi();

            // keep local ticking so it doesn't look frozen if no push arrives
            state.tickClocks();

            menu.render(view);
            view.showMessage(renderClocksLine());
            view.showMessage("(Auto-board: " + (state.isAutoShowBoard() ? "ON" : "OFF") + ")");
            menu.readAndExecute(view);

            state.drainUi();
        }
    }

    private void offerDraw() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) {
            view.showError("No active game.");
            return;
        }

        var status = conn.offerDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw offer sent.");
    }

    private void resign() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) {
            view.showError("No active game.");
            return;
        }

        var status = conn.resign(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Resigned.");

        // server will push gameOver, but we can exit immediately
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

        String oriented = GameUIOrchestrator.orient(b, state.isWhite());

        var youCap = state.isWhite() ? state.getCapturedByWhite() : state.getCapturedByBlack();
        var oppCap = state.isWhite() ? state.getCapturedByBlack() : state.getCapturedByWhite();

        view.showBoardWithCaptured(oriented, youCap, oppCap);
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
        if (gameId == null || gameId.isBlank()) {
            view.showError("No active game.");
            return;
        }

        String raw = view.askLine("Enter move (e2e4 / e7e8q). Captures: e5e4 or e5xe4: ");
        if (raw == null) {
            view.showError("Empty move.");
            return;
        }

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

        // strip separators people like to type
        s = s.replaceAll("[\\s\\-x=:+]", "");

        if (s.length() != 4 && s.length() != 5) {
            throw new IllegalArgumentException("Bad move format. Use e2e4 or e7e8q (captures: e5e4 / e5xe4).");
        }

        // basic square validation
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
}