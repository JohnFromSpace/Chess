package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.AuthScreen;
import com.example.chess.client.ui.InGameScreen;
import com.example.chess.client.ui.LobbyScreen;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.proto.ResponseMessage;

import java.util.Map;

public class ClientController {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state = new SessionState();

    public ClientController(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;

        // Push handler runs on network thread -> only enqueue UI actions.
        this.conn.setPushHandler(this::onPush);
    }

    public void run() {
        while (true) {
            new AuthScreen(conn, view, state).show();
            new LobbyScreen(conn, view, state).show();
            if (state.isInGame()) new InGameScreen(conn, view, state).show();
        }
    }

    private void onPush(ResponseMessage msg) {
        if (msg == null) return;

        Map<String, Object> p = (msg.payload != null) ? new java.util.HashMap<>(msg.payload) : java.util.Map.of();

        switch (msg.type) {
            case "gameStarted" -> {
                // Update state immediately so Lobby loop exits even before UI drain
                String gameId = asString(p, "gameId");
                String color = asString(p, "color");
                state.setWaitingForMatch(false);

                if (gameId != null) state.setActiveGameId(gameId);
                state.setInGame(true);
                state.setWhite("white".equalsIgnoreCase(color));

                String boardStr = asString(p, "board");
                if (boardStr != null && !boardStr.isBlank()) {
                    String oriented = orientBoardForPlayer(boardStr, state.isWhite());
                    state.setLastBoard(oriented);
                    view.showBoard(oriented);
                }

                state.postUi(() -> {
                    String opponent = asString(p, "opponent");
                    boolean resumed = asBool(p, "resumed", false);

                    view.showMessage((resumed ? "Resumed" : "Game started")
                            + " vs " + opponent
                            + ". You are " + (state.isWhite() ? "WHITE" : "BLACK")
                            + ". GameId=" + state.getActiveGameId());

                    if (state.getLastBoard() != null) {
                        view.showBoard(state.getLastBoard());
                    }

                    view.showMessage("Tip: Use 'Print board' any time. Auto-board is "
                            + (state.isAutoShowBoard() ? "ON" : "OFF") + ".");

                    Long w = asLong(p, "whiteTimeMs");
                    Long b = asLong(p, "blackTimeMs");
                    Boolean wtm = asBoolObj(p, "whiteToMove");

                    if (w != null && b != null && wtm != null) {
                        state.syncClocks(w, b, wtm);
                    }
                });
            }

            case "move" -> {
                String moveStr = asString(p, "move");
                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);
                Long w = asLong(p, "whiteTimeMs");
                Long b = asLong(p, "blackTimeMs");
                Boolean wtm = asBoolObj(p, "whiteToMove");

                String boardStr = asString(p, "board");
                if (boardStr != null && !boardStr.isBlank()) {
                    String oriented = orientBoardForPlayer(boardStr, state.isWhite());
                    state.setLastBoard(oriented);
                }

                boolean isMine = moveStr != null && moveStr.equalsIgnoreCase(state.getLastSentMove());
                if (isMine) state.setLastSentMove(null);

                state.postUi(() -> {
                    if (moveStr != null) {
                        String who = isMine ? "You played" : "Opponent played";
                        view.showMessage(who + ": " + moveStr);
                        view.showMove(moveStr, whiteInCheck, blackInCheck);
                    }

                    if (state.isAutoShowBoard()) {
                        String boardState = state.getLastBoard();
                        if (boardState != null && !boardState.isBlank()) view.showBoard(boardState);
                    } else {
                        view.showMessage("(Board updated. Use 'Print board' to view.)");
                    }

                    if (w != null && b != null && wtm != null) {
                        state.syncClocks(w, b, wtm);
                    }
                });
            }

            case "gameOver" -> {
                String result = asString(p, "result");
                String reason = asString(p, "reason");
                state.clearGame();

                state.postUi(() -> {
                    view.showMessage("Game over: " + result + " reason=" + reason);
                    view.showMessage("Returning to lobby...");
                });
            }

            case "drawOffered" ->
                    state.postUi(() -> view.showMessage("Draw offered by: " + asString(p, "by")));

            case "drawDeclined" ->
                    state.postUi(() -> view.showMessage("Draw declined by: " + asString(p, "by")));

            case "info" ->
                    state.postUi(() -> view.showMessage(asString(p, "message")));

            case "error" ->
                    state.postUi(() -> view.showError(msg.message != null ? msg.message : asString(p, "message")));
        }
    }

    private static String asString(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean asBool(Map<String, Object> p, String k, boolean def) {
        Object v = p.get(k);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static Long asLong(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static Boolean asBoolObj(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static String orientBoardForPlayer(String raw, boolean isWhitePlayer) {
        if (raw == null || raw.isBlank()) return raw;
        if (isWhitePlayer) return raw;

        try {
            String[] lines = raw.split("\\R");
            // rank -> pieces[8], ranks 1..8
            java.util.Map<Integer, String[]> rankMap = new java.util.HashMap<>();

            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) continue;

                String[] tok = t.split("\\s+");
                if (tok.length >= 10 && tok[0].matches("[1-8]") && tok[tok.length - 1].matches("[1-8]")) {
                    int rank = Integer.parseInt(tok[0]);
                    String[] pieces = new String[8];
                    for (int i = 0; i < 8; i++) pieces[i] = tok[i + 1];
                    rankMap.put(rank, pieces);
                }
            }

            // If we couldn't parse ranks, fallback
            if (rankMap.size() < 8) return raw;

            StringBuilder sb = new StringBuilder();
            sb.append("  h g f e d c b a\n");
            // black view: top shows rank 1 ... bottom rank 8
            for (int rank = 1; rank <= 8; rank++) {
                String[] pieces = rankMap.get(rank);
                if (pieces == null) pieces = new String[]{".",".",".",".",".",".",".","."};

                sb.append(rank).append(' ');
                for (int f = 7; f >= 0; f--) {
                    sb.append(pieces[f]).append(' ');
                }
                sb.append(rank).append('\n');
            }
            sb.append("  h g f e d c b a\n");
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }
}