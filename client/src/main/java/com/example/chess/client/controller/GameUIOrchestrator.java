package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.screen.InGameScreen;
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

    /**
     * Show the in-game screen once. It loops internally until the game ends.
     * (Creating the screen inside another while-loop just creates confusion.)
     */
    public void runGameLoop() {
        running.set(true);
        new InGameScreen(conn, view, state).show();
        running.set(false);
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

        // If auto-board is ON, render the whole frame here (board + captured + check + clock).
        if (state.isAutoShowBoard()) {
            renderFrame(p, board, null);
        } else {
            view.showMessage("=== Game started === You are " + (state.isWhite() ? "WHITE" : "BLACK"));
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

        // Always show the move line (small), but only redraw the whole board if auto-board is ON.
        if (state.isAutoShowBoard()) {
            renderFrame(p, board, "Move: " + by + " " + mv);
        } else {
            view.showMessage("Move: " + by + " " + mv);
            renderClock(p);
            view.showMessage("(Auto-board: OFF) Press 'Print board' if needed.");
        }
    }

    public void onGameOver(Map<String, Object> p) {
        view.showGameOver(String.valueOf(p.get("result")), String.valueOf(p.get("reason")));
        state.clearGame();
        running.set(false);
    }

    private void renderFrame(Map<String, Object> p, String board, String topLineOrNull) {
        view.clearScreen();

        if (topLineOrNull != null && !topLineOrNull.isBlank()) {
            view.showMessage(topLineOrNull);
        }

        String oriented = orient(board, state.isWhite());

        var youCap = state.isWhite() ? state.getCapturedByWhite() : state.getCapturedByBlack();
        var oppCap = state.isWhite() ? state.getCapturedByBlack() : state.getCapturedByWhite();

        view.showMessage("=== Game === You are " + (state.isWhite() ? "WHITE" : "BLACK"));
        view.showBoardWithCaptured(oriented, youCap, oppCap);

        renderCheckLine(p);
        renderClock(p);
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

    private void renderCheckLine(Map<String, Object> p) {
        boolean wChk = bool(p.get("whiteInCheck"));
        boolean bChk = bool(p.get("blackInCheck"));

        if (state.isWhite() && wChk) view.showMessage("!!! CHECK: You are in check !!!");
        if (!state.isWhite() && bChk) view.showMessage("!!! CHECK: You are in check !!!");

        if (state.isWhite() && bChk) view.showMessage("You put BLACK in check.");
        if (!state.isWhite() && wChk) view.showMessage("You put WHITE in check.");
    }

    public static String orient(String b, boolean isWhite) {
        if (b == null || b.isBlank() || isWhite) return b;

        String[] lines = b.split("\n", -1);
        StringBuilder sb = new StringBuilder();

        for (int i = lines.length - 1; i >= 0; i--) {
            sb.append(lines[i]);
            if (i != 0) sb.append("\n");
        }
        return sb.toString();
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static boolean bool(Object o) { return (o instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(o)); }
    private static long longv(Object o) { return (o instanceof Number n) ? n.longValue() : Long.parseLong(String.valueOf(o)); }

    private static List<String> listStr(Object o) {
        if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        if (o == null) return List.of();
        return List.of(String.valueOf(o));
    }
}