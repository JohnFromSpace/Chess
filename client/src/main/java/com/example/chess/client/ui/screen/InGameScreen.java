package com.example.chess.client.ui.screen;

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
    public void show() throws InterruptedException {
        Menu menu = new Menu("Game");
        menu.add(new MenuItem("Move", this::move));
        menu.add(new MenuItem("Offer draw", this::offerDraw));
        menu.add(new MenuItem("Accept draw", this::acceptDraw));
        menu.add(new MenuItem("Decline draw", this::declineDraw));
        menu.add(new MenuItem("Resign", this::resign));
        menu.add(new MenuItem("Print board", this::printBoard));
        menu.add(new MenuItem("Toggle auto-board", this::toggleAutoBoard));
        menu.add(new MenuItem("Back to lobby", this::backToLobby));
        menu.add(new MenuItem("Exit program", state::requestExit));

        final boolean[] requestedFinalStateOnce = {false};

        Runnable pump = () -> {
            state.drainUi();

            state.tickClocks();
            if (state.isInGame()) {
                boolean flagFell = (state.getWhiteTimeMs() <= 0) || (state.getBlackTimeMs() <= 0);
                if (flagFell && !requestedFinalStateOnce[0]) {
                    requestedFinalStateOnce[0] = true;

                    String gid = state.getActiveGameId();
                    if (gid != null && !gid.isBlank()) {
                        conn.getGameDetails(gid).thenAccept(status -> {
                            if (status == null || status.isError()) return;
                            Object gameObj = status.payload == null ? null : status.payload.get("game");
                            if (gameObj instanceof java.util.Map<?, ?> g) {
                                Object res = g.get("result");
                                Object reason = g.get("reason");
                                if (res != null && !"ONGOING".equalsIgnoreCase(String.valueOf(res))) {
                                    state.postUi(() -> view.showGameOver(String.valueOf(res), String.valueOf(reason)));
                                    state.postUi(state::clearGame);
                                }
                            }
                        });
                    }
                }
            }
        };

        while (state.getUser() != null && state.isInGame() && !state.isExitReqeuested()) {
            pump.run();

            menu.render(view);
            view.showMessage(renderClocksLine());
            view.showMessage("(Auto-board: " + (state.isAutoShowBoard() ? "ON" : "OFF") + ")");

            menu.readAndExecuteResponsive(
                    view,
                    120,
                    pump,
                    () -> !state.isInGame() || state.getUser() == null || state.isExitReqeuested()
            );

            pump.run();
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

        view.showBoardWithCaptured(state.getLastBoard(), youCap, oppCap, state.isWhite());
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

    private void move() throws InterruptedException {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) {
            view.showError("No active game.");
            return;
        }

        Runnable pump = () -> {
            state.drainUi();
            state.tickClocks();
        };

        String raw = view.askLineResponsive(
                "Enter move (e2e4 / e7e8q). Captures: e5e4 or e5xe4: ",
                120,
                pump,
                () -> !state.isInGame() || state.getUser() == null
        );

        if (raw == null) com.example.chess.server.util.Log.warn("Game ended while typing.", null); // game ended while typing

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
            throw new IllegalArgumentException("Bad move format. Use e2e4 or e7e8q (captures: e5e4 / e5xe4).");
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
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); }
        var status = conn.acceptDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw accepted.");
    }

    private void declineDraw() {
        String gameId = state.getActiveGameId();
        if (gameId == null || gameId.isBlank()) { view.showError("No active game."); }
        var status = conn.declineDraw(gameId).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Draw declined.");
    }
}