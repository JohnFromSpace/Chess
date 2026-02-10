package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.screen.ProfileScreenUserMapper;
import com.example.chess.client.view.ConsoleView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameUIOrchestrator {
    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GameUIOrchestrator(ClientConnection c, ConsoleView v, SessionState s) {
        conn = c;
        view = v;
        state = s;
    }

    public void onGameStarted(Map<String, Object> p) {
        String gameId = str(p.get("gameId"));
        String color  = str(p.get("color"));

        state.setActiveGameId(gameId);
        state.setWhite("white".equalsIgnoreCase(color));
        state.setWaitingForMatch(false);
        state.setInGame(true);

        state.setCapturedByWhite(listStr(p.get("capturedByWhite")));
        state.setCapturedByBlack(listStr(p.get("capturedByBlack")));

        long w = longv(p.get("whiteTimeMs"));
        long b = longv(p.get("blackTimeMs"));
        boolean wtm = bool(p.get("whiteToMove"));
        state.syncClocks(w, b, wtm);

        String board = str(p.get("board"));
        state.setLastBoard(board);

        if (state.isAutoShowBoard()) {
            renderFrame(board, null);
        } else {
            view.showMessage("=== Game started === \nYou are " + (state.isWhite() ? "WHITE" : "BLACK"));
            renderClock(p);
            view.showMessage("(Auto-board: OFF) Use 'Print board' if needed.");
        }
    }

    public void onMove(Map<String, Object> p) {
        String by = str(p.get("by"));
        String mv = str(p.get("move"));

        String board = str(p.get("board"));
        state.setLastBoard(board);

        long w = longv(p.get("whiteTimeMs"));
        long b = longv(p.get("blackTimeMs"));
        boolean wtm = bool(p.get("whiteToMove"));
        state.syncClocks(w, b, wtm);

        state.setCapturedByWhite(listStr(p.get("capturedByWhite")));
        state.setCapturedByBlack(listStr(p.get("capturedByBlack")));

        if (state.isAutoShowBoard()) {
            renderFrame(board, "Move: " + by + " " + mv);
        } else {
            view.showMessage("Move: " + by + " " + mv);
            renderClock(p);
            view.showMessage("(Auto-board: OFF) Press 'Print board' if needed.");
        }
    }

    public void onGameOver(Map<String, Object> p) {
        view.showGameOver(String.valueOf(p.get("result")), String.valueOf(p.get("reason")));

        conn.getStats().thenAccept(status -> {
           if (status != null && !status.isError()) {
               var updated = ProfileScreenUserMapper.userFromPayload(status.getPayload());
               state.postUi(() -> state.setUser(updated));
           }
        });

        state.clearGame();
        running.set(false);
    }

    private void renderFrame(String board, String extraLine) {
        view.showMessage("\n=== Game === You are " + (state.isWhite() ? "WHITE" : "BLACK"));

        view.showBoard(board, state.isWhite());

        var youCap = state.isWhite() ? state.getCapturedByWhite() : state.getCapturedByBlack();
        var oppCap = state.isWhite() ? state.getCapturedByBlack() : state.getCapturedByWhite();

        view.showMessage("Captured by YOU: " + joinCaptured(youCap));
        view.showMessage("Captured by OPP: " + joinCaptured(oppCap));
        view.showMessage("Promotion: q/r/b/n (not limited by captured pieces)");

        if (extraLine != null && !extraLine.isBlank()) view.showMessage(extraLine);
    }

    private void renderClock(Map<String, Object> p) {
        long w = longv(p.get("whiteTimeMs"));
        long b = longv(p.get("blackTimeMs"));
        boolean wtm = bool(p.get("whiteToMove"));

        view.showMessage(String.format("[Clock] White: %02d:%02d | Black: %02d:%02d | %s",
                w / 60000, (w / 1000) % 60,
                b / 60000, (b / 1000) % 60,
                wtm ? "WHITE to move" : "BLACK to move"));
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static boolean bool(Object o) { return (o instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(o)); }
    private static long longv(Object o) { return (o instanceof Number n) ? n.longValue() : Long.parseLong(String.valueOf(o)); }

    private static List<String> listStr(Object o) {
        if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        if (o == null) return List.of();
        return List.of(String.valueOf(o));
    }

    private static String joinCaptured(java.util.List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : pieces) {
            if (s == null || s.isBlank()) continue;
            sb.append(s.trim().charAt(0)).append(' '); // prints captured as chars
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "-" : out;
    }
}
