package com.example.chess.client.ui;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.SessionState;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.ui.menu.Command;
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

    // ---- Commands ----
    static final class LoginCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;
        private final SessionState state;

        LoginCommand(ClientConnection conn, ConsoleView view, SessionState state) {
            this.conn = conn; this.view = view; this.state = state;
        }

        @Override public void execute() {
            String u = view.readLine("Username: ");
            String p = view.readPassword("Password: ");
            var status = conn.login(u, p).join();
            if (status.isError()) view.showError(status.getMessage());
            else {
                state.setUser(status.getUser()); // assuming StatusMessage holds user; if not, fetch from payload mapping
                view.showMessage("Logged in as " + state.getUser().username);
            }
        }
    }

    static final class RegisterCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;

        RegisterCommand(ClientConnection conn, ConsoleView view) { this.conn = conn; this.view = view; }

        @Override public void execute() {
            String username = view.readLine("Username: ");
            String name = view.readLine("Name: ");
            String pass = view.readPassword("Password: ");
            var status = conn.register(username, name, pass).join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Registered successfully.");
        }
    }
}