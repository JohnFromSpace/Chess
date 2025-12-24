package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.AuthScreen;
import com.example.chess.client.ui.InGameScreen;
import com.example.chess.client.ui.LobbyScreen;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.proto.ResponseMessage;

import java.util.HashMap;
import java.util.Map;

public class ClientController {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state = new SessionState();

    public ClientController(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
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

        Map<String, Object> p = (msg.payload == null) ? Map.of() : new HashMap<>(msg.payload);

        switch (msg.type) {
            case "gameStarted" -> {
                // stop waiting immediately (Lobby will stop idling)
                state.setWaitingForMatch(false);

                String gameId = asString(p, "gameId");
                String color = asString(p, "color");
                String opponent = asString(p, "opponent");
                boolean resumed = asBool(p, "resumed", false);

                if (gameId != null) state.setActiveGameId(gameId);
                state.setInGame(true);
                state.setWhite("white".equalsIgnoreCase(color));

                String boardRaw = asString(p, "board");
                if (boardRaw != null && !boardRaw.isBlank()) {
                    state.setLastBoard(orientBoardForPlayer(boardRaw, state.isWhite()));
                }

                state.postUi(() -> {
                    view.showMessage((resumed ? "Resumed" : "Game started")
                            + " vs " + opponent
                            + ". You are " + (state.isWhite() ? "WHITE" : "BLACK")
                            + ". GameId=" + state.getActiveGameId());

                    String b = state.getLastBoard();
                    if (b != null && !b.isBlank()) view.showBoard(b);

                    view.showMessage("Auto-board is " + (state.isAutoShowBoard() ? "ON" : "OFF") + ".");
                });
            }

            case "move" -> {
                // server should send board every move; we handle even if missing
                String moveStr = asString(p, "move");
                String by = asString(p, "by"); // may be null if server doesn't send it

                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);

                String boardRaw = asString(p, "board");
                if (boardRaw != null && !boardRaw.isBlank()) {
                    state.setLastBoard(orientBoardForPlayer(boardRaw, state.isWhite()));
                }

                // Determine if it's our move (fallback to lastSentMove if "by" absent)
                boolean isMine = false;
                if (state.getUser() != null && by != null) {
                    isMine = by.equalsIgnoreCase(state.getUser().username);
                } else if (moveStr != null && state.getLastSentMove() != null) {
                    isMine = moveStr.equalsIgnoreCase(state.getLastSentMove());
                }
                if (isMine) state.setLastSentMove(null);

                final boolean isMineFinal = isMine;
                state.postUi(() -> {
                    if (moveStr != null && !moveStr.isBlank()) {
                        view.showMessage((isMineFinal ? "You played: " : "Opponent played: ") + moveStr);
                        view.showMove(moveStr, whiteInCheck, blackInCheck);
                    }

                    // FIX #6: auto-board must show after EVERY move for BOTH players
                    if (state.isAutoShowBoard()) {
                        String b = state.getLastBoard();
                        if (b != null && !b.isBlank()) view.showBoard(b);
                        else view.showMessage("(Board not received from server.)");
                    } else {
                        view.showMessage("(Board updated. Use 'Print board' to view.)");
                    }
                });
            }

            case "drawOffered" -> state.postUi(() ->
                    view.showMessage("Opponent offered a draw."));

            case "drawDeclined" -> state.postUi(() ->
                    view.showMessage("Opponent declined the draw."));

            case "info" -> state.postUi(() ->
                    view.showMessage(asString(p, "message")));

            case "error" -> state.postUi(() ->
                    view.showError(msg.message != null ? msg.message : asString(p, "message")));

            case "gameOver" -> {
                // End the game immediately in state so InGame exits cleanly
                state.setWaitingForMatch(false);

                String result = asString(p, "result");
                String reason = asString(p, "reason");

                state.clearGame();

                state.postUi(() -> {
                    view.showMessage("Game over: " + result + " reason=" + reason);
                    view.showMessage("Returning to lobby...");
                });
            }

            default -> {
                // ignore unknown push
            }
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

    // board flip: black sees reversed ranks/files
    private static String orientBoardForPlayer(String raw, boolean isWhitePlayer) {
        if (raw == null || raw.isBlank()) return raw;
        if (isWhitePlayer) return raw;

        try {
            String[] lines = raw.split("\\R");
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

            if (rankMap.size() < 8) return raw;

            StringBuilder sb = new StringBuilder();
            sb.append("  h g f e d c b a\n");
            for (int rank = 1; rank <= 8; rank++) {
                String[] pieces = rankMap.get(rank);
                if (pieces == null) pieces = new String[]{".",".",".",".",".",".",".","."};

                sb.append(rank).append(' ');
                for (int f = 7; f >= 0; f--) sb.append(pieces[f]).append(' ');
                sb.append(rank).append('\n');
            }
            sb.append("  h g f e d c b a\n");
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }
}