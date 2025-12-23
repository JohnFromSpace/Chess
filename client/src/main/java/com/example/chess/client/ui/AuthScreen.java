package com.example.chess.client.ui;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

public class AuthScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public AuthScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Auth");
        menu.add(new MenuItem("Login", new LoginCommand(conn, view, state)));
        menu.add(new MenuItem("Register", new RegisterCommand(conn, view)));
        menu.add(new MenuItem("Exit", () -> System.exit(0)));

        while (state.getUser() == null) {
            menu.render(view);
            menu.readAndExecute(view);
        }
    }
}