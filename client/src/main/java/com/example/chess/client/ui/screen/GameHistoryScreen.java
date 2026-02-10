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

    private record Row(
            String id,
            String youAre,
            String opponent,
            String result,
            String reason,
            long createdAt,
            long lastUpdate
    ) {
        long sortTs() {
            return lastUpdate > 0 ? lastUpdate : createdAt;
        }

        String matchupText() {
            String you = (youAre == null || youAre.isBlank()) ? "?" : youAre;
            String opp = (opponent == null || opponent.isBlank()) ? "?" : opponent;
            return you + " vs " + opp;
        }

        String resultText() {
            String res = (result == null || result.isBlank()) ? "?" : result;
            String rr = normalizeReason(reason);
            if (rr.isBlank()) return res;
            return res + " (" + rr + ")";
        }

        String idText() {
            return "id=" + (id == null ? "?" : id);
        }
    }

    @Override
    public void show() {
        var status = conn.listGames().join();
        if (status.isError()) {
            view.showError(status.getMessage());
        }

        Object gObj = status.getPayload() == null ? null : status.getPayload().get("games");
        if (!(gObj instanceof List<?> gl) || gl.isEmpty()) {
            view.showMessage("No games found.");
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (Object o : gl) {
            if (!(o instanceof Map<?, ?> m)) continue;

            rows.add(new Row(
                    str(m.get("id")),
                    str(m.get("youAre")),
                    str(m.get("opponent")),
                    str(m.get("result")),
                    str(m.get("reason")),
                    longVal(m.get("createdAt")),
                    longVal(m.get("lastUpdate"))
            ));
        }

        if (rows.isEmpty()) {
            view.showMessage("No games found.");
        }

        rows.sort(Comparator
                .comparingLong(Row::sortTs)
                .thenComparing(r -> r.id == null ? "" : r.id));

        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        List<String[]> cols = new ArrayList<>(rows.size());
        int numW = 0, matchW = 0, resW = 0, timeW = 0;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String num = "#" + (i + 1);

            long ts = r.sortTs();
            String when = ts > 0 ? fmt.format(Instant.ofEpochMilli(ts)) : "?";

            String match = r.matchupText();
            String res = r.resultText();

            cols.add(new String[]{num, match, res, when, r.idText()});

            numW = Math.max(numW, num.length());
            matchW = Math.max(matchW, match.length());
            resW = Math.max(resW, res.length());
            timeW = Math.max(timeW, when.length());
        }

        view.showMessage("\n=== Your Games ===");

        for (String[] c : cols) {
            view.showMessage(String.format(
                    "%-" + numW + "s | %-" + matchW + "s | %-" + resW + "s | %-" + timeW + "s | %s",
                    c[0], c[1], c[2], c[3], c[4]
            ));
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null) throw new IllegalArgumentException("Empty message.");
        String r = reason.trim();
        if (r.isEmpty()) throw new IllegalArgumentException("Empty trimmed message.");

        String low = r.toLowerCase(Locale.ROOT);

        if (low.contains("both disconnected")) return "Both disconnected.";
        if (low.startsWith("aborted") && low.contains("no moves")) return "No moves.";

        if (low.contains("resign")) return "Resignation.";
        if (low.contains("timeout") || low.equals("time") || low.equals("time.")) return "Timeout.";

        return r.endsWith(".") ? r : (r + ".");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? 0L : Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            com.example.chess.client.util.Log.warn("Failed: ", e);
            return 0L;
        }
    }
}
