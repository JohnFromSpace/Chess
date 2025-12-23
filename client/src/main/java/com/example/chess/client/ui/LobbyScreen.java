package com.example.chess.client.ui;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.SessionState;
import com.example.chess.client.ui.menu.Command;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

public class LobbyScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public LobbyScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn; this.view = view; this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Lobby");
        menu.add(new MenuItem("Request game", new RequestGameCommand(conn, view, state)));
        menu.add(new MenuItem("Logout", () -> { state.setUser(null); state.clearGame(); }));
        menu.add(new MenuItem("Exit", () -> System.exit(0)));

        while (state.getUser() != null && !state.isInGame()) {
            menu.render(view);
            menu.readAndExecute(view);
        }
    }

    static final class RequestGameCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;
        private final SessionState state;

        RequestGameCommand(ClientConnection conn, ConsoleView view, SessionState state) {
            this.conn = conn; this.view = view; this.state = state;
        }

        @Override public void execute() {
            var status = conn.requestGame().join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Queued / matched. Waiting for server...");
        }
    }
}
