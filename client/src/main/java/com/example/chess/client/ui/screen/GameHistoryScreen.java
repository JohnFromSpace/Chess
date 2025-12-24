package com.example.chess.client.ui.screen;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class GameHistoryScreen implements Screen {
    private final ClientConnection conn;
    private final ConsoleView view;

    public GameHistoryScreen(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
    }

    @Override
    public void show() {
        var status = conn.listGames().join();
        if (status.isError()) {
            view.showError(status.getMessage());
            return;
        }

        Object gObj = status.payload == null ? null : status.payload.get("games");
        if (!(gObj instanceof List<?> gl) || gl.isEmpty()) {
            view.showMessage("No games found.");
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        view.showMessage("\n=== Your Games ===");
        int i = 1;
        for (Object o : gl) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = str(m.get("id"));
            String opp = str(m.get("opponent"));
            String you = str(m.get("youAre"));
            String res = str(m.get("result"));
            String reason = str(m.get("reason"));
            long createdAt = longVal(m.get("createdAt"));

            String when = createdAt > 0 ? fmt.format(Instant.ofEpochMilli(createdAt)) : "?";
            String r = (reason == null || reason.isBlank()) ? "" : (" (" + reason + ")");
            view.showMessage(String.format("#%d | %s vs %s | %s%s | %s | id=%s", i++, you, opp, res, r, when, id));
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? 0L : Long.parseLong(String.valueOf(o)); }
        catch (Exception ignored) { return 0L; }
    }
}