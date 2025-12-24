package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.AuthScreen;
import com.example.chess.client.ui.InGameScreen;
import com.example.chess.client.ui.LobbyScreen;
import com.example.chess.client.ui.ProfileScreenUserMapper;
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

                applyClockFields(p);

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

                    // show clocks immediately so you see them even if you were still in Lobby
                    view.showMessage(renderClocksLine());

                    view.showMessage("Auto-board is " + (state.isAutoShowBoard() ? "ON" : "OFF") + ".");
                });
            }

            case "move" -> {
                // --- CLOCK SYNC (FIX) ---
                applyClockFields(p);

                String moveStr = asString(p, "move");
                String by = asString(p, "by"); // may be null if server doesn't send it

                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);

                String boardRaw = asString(p, "board");
                if (boardRaw != null && !boardRaw.isBlank()) {
                    state.setLastBoard(orientBoardForPlayer(boardRaw, state.isWhite()));
                }

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

                    if (state.isAutoShowBoard()) {
                        String b = state.getLastBoard();
                        if (b != null && !b.isBlank()) view.showBoard(b);
                        else view.showMessage("(Board not received from server.)");
                    } else {
                        view.showMessage("(Board updated. Use 'Print board' to view.)");
                    }

                    // show updated clocks after every move
                    view.showMessage(renderClocksLine());
                });
            }

            case "drawOffered" -> state.postUi(() -> view.showMessage("Opponent offered a draw."));
            case "drawDeclined" -> state.postUi(() -> view.showMessage("Opponent declined the draw."));
            case "info" -> state.postUi(() -> view.showMessage(asString(p, "message")));
            case "error" -> state.postUi(() -> view.showError(msg.message != null ? msg.message : asString(p, "message")));

            case "gameOver" -> {
                state.setWaitingForMatch(false);

                String result = asString(p, "result");
                String reason = asString(p, "reason");

                state.clearGame();

                // after state.clearGame();
                conn.getStats().thenAccept(status -> {
                    if (status == null || status.isError()) return;

                    var updated = ProfileScreenUserMapper.userFromPayload(status.payload);
                    if (updated != null) state.setUser(updated);

                    state.postUi(() -> {
                        var u = state.getUser();
                        if (u != null && u.stats != null) {
                            view.showMessage("[Profile updated] ELO " + u.stats.rating
                                    + " | W/L/D " + u.stats.won + "/" + u.stats.lost + "/" + u.stats.drawn);
                        }
                    });
                }).exceptionally(ex -> null);
            }

            default -> {
                // ignore unknown push
            }
        }
    }

    private void applyClockFields(Map<String, Object> p) {
        long tc = asLong(p, "timeControlMs", -1);
        long inc = asLong(p, "incrementMs", -1);
        if (tc > 0) state.setTimeControlMs(tc);
        if (inc >= 0) state.setIncrementMs(inc);

        long w = asLong(p, "whiteTimeMs", -1);
        long b = asLong(p, "blackTimeMs", -1);
        Boolean wtm = asBoolObj(p, "whiteToMove");

        if (w >= 0 || b >= 0 || wtm != null) {
            state.syncClocks(w, b, wtm);
        }
    }

    private String renderClocksLine() {
        String w = fmt(state.getWhiteTimeMs());
        String b = fmt(state.getBlackTimeMs());
        String turn = state.isWhiteToMove() ? "WHITE to move" : "BLACK to move";
        return "[Clock] White: " + w + " | Black: " + b + " | " + turn;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long m = s / 60;
        long r = s % 60;
        return String.format("%02d:%02d", m, r);
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

    private static long asLong(Map<String, Object> p, String k, long def) {
        Object v = p.get(k);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static Boolean asBoolObj(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return Boolean.parseBoolean(s);
    }

    private static String orientBoardForPlayer(String raw, boolean isWhitePlayer) {
        if (raw == null || raw.isBlank()) return raw;

        String[] lines = raw.split("\\R");

        Boolean rawFilesNormal = null; // null = unknown
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("a b c d e f g h")) { rawFilesNormal = true; break; }
            if (t.startsWith("h g f e d c b a")) { rawFilesNormal = false; break; }

            // If we hit a rank line first, stop trying to detect header
            if (t.matches("^[1-8]\\s+.*")) break;
        }
        if (rawFilesNormal == null) rawFilesNormal = true; // default

        Map<Integer, String[]> rankMap = new HashMap<>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.matches("^[1-8]\\s+.*")) continue;

            String[] tok = t.split("\\s+");
            if (tok.length < 9) continue;

            int rank;
            try {
                rank = Integer.parseInt(tok[0]);
            } catch (NumberFormatException e) {
                continue;
            }

            String[] row = new String[8];
            for (int i = 0; i < 8; i++) row[i] = tok[i + 1];

            if (!rawFilesNormal) {
                for (int i = 0; i < 4; i++) {
                    String tmp = row[i];
                    row[i] = row[7 - i];
                    row[7 - i] = tmp;
                }
            }

            rankMap.put(rank, row);
        }

        // Infer whether raw is "white-oriented" (rank 8 mostly lowercase = black pieces on top)
        boolean rawHasBlackOnTop = true;
        String[] r8 = rankMap.get(8);
        if (r8 != null) {
            int lower = 0, upper = 0;
            for (String p : r8) {
                if (p == null || p.length() != 1) continue;
                char c = p.charAt(0);
                if (!Character.isLetter(c)) continue;
                if (Character.isLowerCase(c)) lower++;
                else upper++;
            }
            if (lower + upper > 0) rawHasBlackOnTop = (lower >= upper);
        }

        // If raw orientation is opposite of what we need, rotate ranks (8<->1)
        boolean rotateRanks = isWhitePlayer ? !rawHasBlackOnTop : rawHasBlackOnTop;

        // Output: white => a..h, black => h..a
        boolean reverseFilesOut = !isWhitePlayer;
        String header = reverseFilesOut ? "  h g f e d c b a" : "  a b c d e f g h";

        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");

        // Always print ranks top->bottom as 8..1 (normal chess diagram)
        for (int displayRank = 8; displayRank >= 1; displayRank--) {
            int sourceRank = rotateRanks ? (9 - displayRank) : displayRank;
            String[] row = rankMap.get(sourceRank);
            if (row == null) row = new String[]{".",".",".",".",".",".",".","."};

            sb.append(displayRank).append(' ');
            if (reverseFilesOut) {
                for (int f = 7; f >= 0; f--) sb.append(row[f]).append(' ');
            } else {
                for (int f = 0; f < 8; f++) sb.append(row[f]).append(' ');
            }
            sb.append(displayRank).append("\n");
        }

        sb.append(header);
        return sb.toString();
    }
}