package com.example.chess.client.ui.screen;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class GameReplayScreen implements Screen {
    private final ClientConnection conn;
    private final ConsoleView view;

    public GameReplayScreen(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
    }

    @Override
    public void show() {
        String token = view.askLine("Enter gameId (or UUID prefix): ").trim();
        if (token.isBlank()) return;

        // simplest: require full gameId (you can keep your #N alias if you already added it)
        var status = conn.getGameDetails(token).join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        Object gameObj = status.payload == null ? null : status.payload.get("game");
        if (!(gameObj instanceof Map<?, ?> g)) {
            view.showError("Bad server payload: missing game.");
            return;
        }

        view.showMessage("\n=== Game ===");
        view.showMessage("Id: " + str(g.get("id")));
        view.showMessage("White: " + str(g.get("whiteUser")) + " | Black: " + str(g.get("blackUser")));
        view.showMessage("Result: " + str(g.get("result")) + " (" + str(g.get("reason")) + ")");

        String board = str(g.get("board"));
        if (board != null && !board.isBlank()) {
            view.showMessage("\nFinal board:");
            view.showBoard(board);
        }

        Object mhObj = g.get("moveHistory");
        if (!(mhObj instanceof List<?> mh) || mh.isEmpty()) {
            view.showMessage("\n(No moves)");
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        view.showMessage("\nMoves:");
        int ply = 1;
        for (Object o : mh) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String by = str(m.get("by"));
            String mv = str(m.get("move"));
            long at = longVal(m.get("atMs"));
            String when = (at > 0) ? fmt.format(Instant.ofEpochMilli(at)) : "?";
            view.showMessage(String.format("%02d) %-10s %-6s @ %s", ply++, by == null ? "?" : by, mv, when));
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? 0L : Long.parseLong(String.valueOf(o)); }
        catch (Exception ignored) { return 0L; }
    }
}