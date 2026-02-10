package com.example.chess.client.ui.screen;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;

public class ProfileScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public ProfileScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    private void renderProfile() {
        UserModels.User u = state.getUser();
        if (u == null) { view.showError("Not logged in."); return; }

        UserModels.Stats st = u.getStats();
        int played = (st != null) ? st.getPlayed() : 0;
        int won    = (st != null) ? st.getWon() : 0;
        int lost   = (st != null) ? st.getLost() : 0;
        int drawn  = (st != null) ? st.getDrawn() : 0;
        int rating = (st != null && st.getRating() > 0) ? st.getRating() : 1200;

        view.showMessage("User: " + u.getUsername() + (u.getUsername() != null ? (" (" + u.getUsername() + ")") : ""));
        view.showMessage("ELO:  " + rating);
        view.showMessage("W/L/D: " + won + "/" + lost + "/" + drawn + "  | Played: " + played);
    }

    private void refresh() {
        var status = conn.getStats().join();
        if (status.isError()) { view.showError(status.getMessage()); return; }

        UserModels.User updated = ProfileScreenUserMapper.userFromPayload(status.payload);
        state.setUser(updated);

        renderProfile();
    }

    @Override
    public void show() throws InterruptedException {
        view.clearScreen();

        Menu menu = new Menu("Profile");
        menu.add(new MenuItem("Refresh", this::refresh));
        menu.add(new MenuItem("My games (list)", () -> new GameHistoryScreen(conn, view).show()));
        menu.add(new MenuItem("View game + moves", () -> new GameReplayScreen(conn, view).show()));
        menu.add(new MenuItem("Back", () -> { }));

        renderProfile();
        menu.render(view);
        menu.readAndExecute(view);
    }
}
