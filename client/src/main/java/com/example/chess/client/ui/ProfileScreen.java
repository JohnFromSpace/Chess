package com.example.chess.client.ui;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;

import java.util.List;

public class ProfileScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;
    private GameCatalog lastCatalog = new GameCatalog(List.of());

    public ProfileScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn;
        this.view = view;
        this.state = state;
    }

    private void renderProfile() {
        UserModels.User u = state.getUser();
        if (u == null) { view.showError("Not logged in."); return; }

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
        if (status.isError()) { view.showError(status.getMessage()); return; }

        UserModels.User updated = ProfileScreenUserMapper.userFromPayload(status.payload);
        if (updated != null) state.setUser(updated);

        renderProfile();
    }

    @Override
    public void show() {
        Menu menu = new Menu("Profile");
        menu.add(new MenuItem("Refresh", this::refresh));
        menu.add(new MenuItem("My games (list)", this::listMyGames));
        menu.add(new MenuItem("View game (#N or id)", this::viewGame));
        menu.add(new MenuItem("Back", () -> { }));

        renderProfile();
        menu.render(view);
        menu.readAndExecute(view);
    }

    private void listMyGames() {
        var status = conn.listGames().join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }
        lastCatalog = GameCatalog.fromListGamesPayload(status.payload);
        lastCatalog.print(view);
    }

    private void viewGame() {
        // ensure we have a catalog
        if (lastCatalog == null || lastCatalog.isEmpty()) listMyGames();
        if (lastCatalog == null || lastCatalog.isEmpty()) return;

        String token = view.askLine("Enter game #N or gameId/prefix: ").trim();
        String gameId;
        try {
            gameId = lastCatalog.resolveToGameId(token);
        } catch (IllegalArgumentException e) {
            view.showError(e.getMessage());
            return;
        }

        var status = conn.getGameDetails(gameId).join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        // show raw details (you can plug in your replay viewer here)
        Object gameObj = status.payload == null ? null : status.payload.get("game");
        view.showMessage("\n=== Game Details ===");
        view.showMessage("Requested: " + token + " -> id=" + gameId);
        view.showMessage(String.valueOf(gameObj));
    }

}