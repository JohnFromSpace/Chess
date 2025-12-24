package com.example.chess.client.ui;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;

import java.util.Map;

public class ProfileScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public ProfileScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Profile");
        menu.add(new MenuItem("Refresh", this::refresh));
        menu.add(new MenuItem("Back", () -> { /* just return */ }));

        // one-shot “panel”: show -> one menu choice -> return
        renderProfile();
        menu.render(view);
        menu.readAndExecute(view);
    }

    private void renderProfile() {
        UserModels.User u = state.getUser();
        if (u == null) {
            view.showError("Not logged in.");
            return;
        }

        int played = (u.stats != null) ? u.stats.played : 0;
        int won    = (u.stats != null) ? u.stats.won : 0;
        int lost   = (u.stats != null) ? u.stats.lost : 0;
        int drawn  = (u.stats != null) ? u.stats.drawn : 0;
        int rating = (u.stats != null && u.stats.rating > 0) ? u.stats.rating : 1200;

        view.showMessage("\n=== Profile ===");
        view.showMessage("User: " + u.username + (u.name != null ? (" (" + u.name + ")") : ""));
        view.showMessage("ELO:  " + rating);
        view.showMessage("W/L/D: " + won + "/" + lost + "/" + drawn + "  | Played: " + played);
    }

    private void refresh() {
        var status = conn.getStats().join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        UserModels.User updated = userFromPayload(status.payload);
        if (updated != null) state.setUser(updated);

        renderProfile();
    }

    @SuppressWarnings("unchecked")
    private static UserModels.User userFromPayload(Map<String, Object> payload) {
        if (payload == null) return null;
        Object userObj = payload.get("user");
        if (!(userObj instanceof Map<?, ?> um)) return null;

        UserModels.User u = new UserModels.User();
        u.username = str(um.get("username"));
        u.name     = str(um.get("name"));

        if (u.stats == null) u.stats = new UserModels.Stats();
        u.stats.played = intVal(um.get("played"));
        u.stats.won    = intVal(um.get("won"));
        u.stats.lost   = intVal(um.get("lost"));
        u.stats.drawn  = intVal(um.get("drawn"));
        u.stats.rating = intValOr(um.get("rating"), 1200);

        return u;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }

    private static int intValOr(Object o, int def) {
        int v = intVal(o);
        return v == 0 ? def : v;
    }
}