package com.example.chess.client.ui;

import com.example.chess.client.SessionState;
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
        menu.add(new MenuItem("Back to lobby", this::backToLobby)); // FIXED

        while (state.getUser() != null && state.isInGame()) {
            state.drainUi();
            menu.render(view);
            view.showMessage("(Auto-board: " + (state.isAutoShowBoard() ? "ON" : "OFF") + ")");
            menu.readAndExecute(view);
            state.drainUi();
        }
    }

    private void move() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) {
            view.showError("No active game.");
            return;
        }

        String move = view.askLine("Enter move (e2e4 / e7e8q): ").trim();
        if (move.isBlank()) {
            view.showError("Empty move.");
            return;
        }

        var status = conn.makeMove(gameId, move).join();
        if (status.isError()) view.showError(status.getMessage());
        else {
            state.setLastSentMove(move);
            view.showMessage("Move sent.");
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
        // server will push gameOver; locally we can also clear now
        state.clearGame();
    }

    private void backToLobby() {
        String gameId = state.getActiveGameId();
        if (gameId != null && !gameId.isBlank()) {
            var status = conn.resign(gameId).join();
            if (status.isError()) {
                view.showError(status.getMessage());
            } else {
                view.showMessage("Left game (counted as resignation). Returning to lobby...");
            }
        }
        state.clearGame();
    }

    private void toggleAutoBoard() {
        state.setAutoShowBoard(!state.isAutoShowBoard());
        view.showMessage("Auto-board is now " + (state.isAutoShowBoard() ? "ON" : "OFF"));
    }

    private void printBoard() {
        String b = state.getLastBoard();
        if (b == null || b.isBlank()) view.showMessage("No board received yet.");
        else view.showBoard(b);
    }
}