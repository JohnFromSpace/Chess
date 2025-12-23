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
                if (boardStr != null && !boardStr.isBlank()) state.setLastBoard(boardStr);

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

                    // Helpful hint
                    view.showMessage("Tip: Use 'Print board' any time. Auto-board is "
                            + (state.isAutoShowBoard() ? "ON" : "OFF") + ".");
                });
            }

            case "move" -> {
                String moveStr = asString(p, "move");
                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);

                String boardStr = asString(p, "board");
                if (boardStr != null && !boardStr.isBlank()) state.setLastBoard(boardStr);

                boolean isMine = moveStr != null && moveStr.equalsIgnoreCase(state.getLastSentMove());
                if (isMine) state.setLastSentMove(null);

                state.postUi(() -> {
                    if (moveStr != null) {
                        String who = isMine ? "You played" : "Opponent played";
                        view.showMessage(who + ": " + moveStr);
                        view.showMove(moveStr, whiteInCheck, blackInCheck);
                    }

                    if ((state.isAutoShowBoard() || whiteInCheck || blackInCheck)
                            && state.getLastBoard() != null) {
                        view.showBoard(state.getLastBoard());
                    } else {
                        view.showMessage("(Board updated. Use 'Print board' to view.)");
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
}