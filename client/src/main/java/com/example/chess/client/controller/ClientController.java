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

        // push handler
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

        Map<String, Object> p = (msg.payload != null) ? msg.payload : Map.of();

        switch (msg.type) {
            case "gameStarted" -> {
                String gameId = asString(p, "gameId");
                String color = asString(p, "color");
                String opponent = asString(p, "opponent");
                boolean resumed = asBool(p, "resumed", false);

                if (gameId != null) state.setActiveGameId(gameId);
                state.setInGame(true);
                state.setWhite("white".equalsIgnoreCase(color));

                view.showMessage((resumed ? "Resumed" : "Game started")
                        + " vs " + opponent
                        + ". You are " + (state.isWhite() ? "WHITE" : "BLACK")
                        + ". GameId=" + gameId);

                String boardStr = asString(p, "board");
                if (boardStr != null && !boardStr.isBlank()) {
                    state.setLastBoard(boardStr);
                    view.showBoard(boardStr);
                }
            }

            case "move" -> {
                String moveStr = asString(p, "move");
                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);

                if (moveStr != null) {
                    view.showMove(moveStr, whiteInCheck, blackInCheck);
                }

                String boardStr = asString(p, "board");
                if (boardStr != null && !boardStr.isBlank()) {
                    state.setLastBoard(boardStr);
                    view.showBoard(boardStr);
                }
            }

            case "drawOffered" ->
                    view.showMessage("Draw offered by: " + asString(p, "by"));

            case "drawDeclined" ->
                    view.showMessage("Draw declined by: " + asString(p, "by"));

            case "gameOver" -> {
                view.showMessage("Game over: " + asString(p, "result")
                        + " reason=" + asString(p, "reason"));
                state.clearGame(); // make sure clearGame() clears lastBoard too
            }

            case "info" ->
                    view.showMessage(asString(p, "message"));

            case "error" ->
                    view.showError(msg.message != null ? msg.message : asString(p, "message"));

            default ->
                    view.showMessage("Push: " + msg.type + " " + p);
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