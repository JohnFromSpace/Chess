package com.example.chess.client.ui.screen;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;

import java.util.Map;

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
    public void show() throws InterruptedException {
        Menu menu = new Menu("Auth");
        menu.add(new MenuItem("Login", this::login));
        menu.add(new MenuItem("Register", this::register));
        menu.add(new MenuItem("Exit", () -> System.err.close()));

        while (state.getUser() == null) {
            state.drainUi();
            menu.render(view);
            menu.readAndExecute(view);
            state.drainUi();
        }
    }

    private void login() {
        String u = view.askLine("Username: ").trim();
        String p = view.askLine("Password: ").trim();

        var status = conn.login(u, p).join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        Object userObj = status.payload != null ? status.payload.get("user") : null;
        if (!(userObj instanceof Map<?, ?> um)) {
            view.showError("Login OK, but missing user payload.");
            return;
        }

        UserModels.User user = new UserModels.User();
        user.username = str(um.get("username"));
        user.name = str(um.get("name"));

        UserModels.Stats st = new UserModels.Stats();
        st.played = intVal(um.get("played"));
        st.won = intVal(um.get("won"));
        st.drawn = intVal(um.get("drawn"));
        st.lost = intVal(um.get("lost"));
        st.rating = intVal(um.get("rating"));
        user.stats = st;

        state.setUser(user);
        view.showMessage("Logged in as " + user.username);
    }

    private void register() {
        String username = view.askLine("Username: ").trim();
        String name = view.askLine("Name: ").trim();
        String pass = view.askLine("Password: ").trim();

        var status = conn.register(username, name, pass).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Registered successfully.");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();

        return Integer.parseInt(String.valueOf(o));
    }
}