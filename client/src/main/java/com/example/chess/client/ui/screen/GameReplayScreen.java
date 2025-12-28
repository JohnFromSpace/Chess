package com.example.chess.client.ui.screen;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        String white = str(g.get("whiteUser"));
        String black = str(g.get("blackUser"));

        view.showMessage("\n=== Game ===");
        view.showMessage("Id: " + str(g.get("id")));
        view.showMessage("White: " + white + " | Black: " + black);
        view.showMessage("Result: " + str(g.get("result")) + " (" + str(g.get("reason")) + ")");

        long wMs = longVal(g.get("whiteTimeMs"));
        long bMs = longVal(g.get("blackTimeMs"));
        view.showMessage(String.format("[Clock] White: %s | Black: %s", fmtClock(wMs), fmtClock(bMs)));

        String board = str(g.get("board"));
        if (board != null && !board.isBlank()) {
            view.showMessage("\nFinal board:");
            view.showBoard(board);
        }

        Object mhObj = g.get("moveHistory");
        if (!(mhObj instanceof List<?> raw) || raw.isEmpty()) {
            view.showMessage("\n(No moves recorded for this game.)");
            return;
        }

        List<MoveItem> moves = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String mv = str(m.get("move"));
            if (mv == null || mv.isBlank()) continue;
            moves.add(new MoveItem(str(m.get("by")), mv, longVal(m.get("atMs"))));
        }

        if (moves.isEmpty()) {
            view.showMessage("\n(No moves recorded for this game.)");
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        view.showMessage("\nMoves:");
        int fullMove = 1;
        for (int i = 0; i < moves.size(); i += 2) {
            MoveItem w = moves.get(i);
            MoveItem b = (i + 1 < moves.size()) ? moves.get(i + 1) : null;

            String line = String.format("%02d. %-8s %-8s", fullMove++, w.move, (b == null ? "" : b.move));

            if (w.atMs > 0 || (b != null && b.atMs > 0)) {
                line += "   (" + (w.by == null ? "WHITE" : w.by) + " @ " + fmt.format(Instant.ofEpochMilli(w.atMs)) + ")";
                if (b != null) line += " (" + (b.by == null ? "BLACK" : b.by) + " @ " + fmt.format(Instant.ofEpochMilli(b.atMs)) + ")";
            }

            view.showMessage(line);
        }
    }

    private static final class MoveItem {
        final String by;
        final String move;
        final long atMs;

        MoveItem(String by, String move, long atMs) {
            this.by = by;
            this.move = move;
            this.atMs = atMs;
        }
    }

    private static String fmtClock(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long min = total / 60L;
        long sec = total % 60L;
        return String.format("%02d:%02d", min, sec);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? 0L : Long.parseLong(String.valueOf(o)); }
        catch (Exception ignored) { return 0L; }
    }
}