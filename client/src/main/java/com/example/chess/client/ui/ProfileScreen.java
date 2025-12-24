package com.example.chess.client.ui;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.UserModels;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
        menu.add(new MenuItem("View game by id", this::viewGameById));
        menu.add(new MenuItem("Back", () -> {}));

        renderProfile();
        menu.render(view);
        menu.readAndExecute(view);
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

    private void viewGameById() {
        String id = view.askLine("Game id: ").trim();
        if (id.isBlank()) { view.showError("Empty id."); return; }

        var status = conn.getGameDetails(id).join();
        if (status.isError()) { view.showError(status.getMessage()); return; }

        Map<String, Object> payload = status.payload;
        if (payload == null) { view.showError("Missing payload."); return; }

        Object gameObj = payload.get("game");
        if (!(gameObj instanceof Map<?, ?> gm)) { view.showError("Missing game."); return; }

        view.showMessage("\n=== Game " + gm.get("id") + " ===");
        view.showMessage("White: " + gm.get("whiteUser") + " | Black: " + gm.get("blackUser"));
        view.showMessage("Result: " + gm.get("result") + " (" + gm.get("reason") + ")");

        Object tlObj = gm.get("timeline");
        if (!(tlObj instanceof List<?> tl)) {
            view.showError("No timeline available.");
            return;
        }

        for (Object rowObj : tl) {
            if (!(rowObj instanceof Map<?, ?> row)) continue;

            int ply = intVal(row.get("ply"));
            long at = longVal(row.get("atMs"));
            String by = str(row.get("by"));
            String mv = str(row.get("move"));
            String board = str(row.get("board"));

            boolean wChk = boolVal(row.get("whiteInCheck"));
            boolean bChk = boolVal(row.get("blackInCheck"));

            view.showMessage("\n--- Ply " + ply + " @ " + fmtTime(at) + " ---");
            if (ply == 0) view.showMessage("(initial position)");
            else view.showMessage("By: " + by + " | Move: " + mv);

            if (wChk) view.showMessage("CHECK: White is in check.");
            if (bChk) view.showMessage("CHECK: Black is in check.");

            view.showBoard(board);

            String cmd = view.askLine("Enter=next, q=quit: ").trim();
            if (cmd.equalsIgnoreCase("q")) break;
        }
    }

    private static String fmtTime(long ms) {
        try { return new SimpleDateFormat("HH:mm:ss").format(new Date(ms)); }
        catch (Exception e) { return String.valueOf(ms); }
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static int intVal(Object o) { return (o instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(o)); }
    private static long longVal(Object o) { return (o instanceof Number n) ? n.longValue() : Long.parseLong(String.valueOf(o)); }
    private static boolean boolVal(Object o) { return (o instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(o)); }
}