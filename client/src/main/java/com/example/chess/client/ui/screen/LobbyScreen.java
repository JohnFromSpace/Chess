package com.example.chess.client.ui.screen;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

public class LobbyScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public LobbyScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Lobby");
        menu.add(new MenuItem("Request game", this::requestGame));
        menu.add(new MenuItem("Profile", this::openProfile));
        menu.add(new MenuItem("Logout", this::logout));
        menu.add(new MenuItem("Exit", () -> System.exit(0)));

        Runnable pump = state::drainUi;
        boolean waitingHintShown = false;

        while (state.getUser() != null && !state.isInGame()) {
            pump.run();
            if (state.isInGame()) break;

            if (state.isWaitingForMatch() && !waitingHintShown) {
                view.showMessage("Waiting for opponent... (you can still Exit / Logout / Profile)");
                waitingHintShown = true;
            }
            if (!state.isWaitingForMatch()) {
                waitingHintShown = false;
            }

            menu.render(view);
            menu.readAndExecuteResponsive(
                    view,
                    120,
                    pump,
                    () -> state.isInGame() || state.getUser() == null
            );

            pump.run();
            if (state.isInGame()) break;
        }
    }

    private void requestGame() {
        if (state.isWaitingForMatch()) {
            view.showMessage("Already waiting for a match.");
            return;
        }

        var status = conn.requestGame().join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        state.setWaitingForMatch(true);
        view.showMessage("Queued / matched. Waiting for server...");
    }

    private void openProfile() {
        new ProfileScreen(conn, view, state).show();
    }

    private void logout() {
        try { conn.logout().join(); } catch (Exception ignored) {}

        state.setUser(null);
        state.clearGame();
        state.setWaitingForMatch(false);
        view.showMessage("Logged out.");
    }
}