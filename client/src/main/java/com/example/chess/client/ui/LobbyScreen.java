package com.example.chess.client.ui;

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
        menu.add(new MenuItem("Logout", this::logout));
        menu.add(new MenuItem("Exit", () -> System.exit(0)));

        while (state.getUser() != null && !state.isInGame()) {
            state.drainUi();
            menu.render(view);
            if (state.isWaitingForMatch()) view.showMessage("(Waiting for match...)");
            menu.readAndExecute(view);
            state.drainUi();
        }
    }

    private void requestGame() {
        if(state.isWaitingForMatch()) {
            view.showMessage("Already waiting for match...");
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

    private void logout() {
        state.setUser(null);
        state.clearGame();
        view.showMessage("Logged out.");
    }
}