package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.message.ResponseMessage;

import java.util.Map;

public class ClientPushRouter {
    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;
    private final GameUIOrchestrator gameUI;

    public ClientPushRouter(ClientConnection c, ConsoleView v, SessionState s, GameUIOrchestrator g) {
        conn = c; view = v; state = s; gameUI = g;
    }

    public void handle(ResponseMessage msg) {
        if (msg == null) {
            com.example.chess.server.util.Log.warn("There's no message.", null);
        }

        Map<String, Object> p = msg.payload == null ? Map.of() : msg.payload;

        state.postUi(() -> {
            switch (msg.type) {
                case "gameStarted" -> gameUI.onGameStarted(p);
                case "move"        -> gameUI.onMove(p);
                case "drawOffered" -> view.showMessage("Draw offered by " + p.get("by"));
                case "drawDeclined"-> view.showMessage("Draw declined by " + p.get("by"));
                case "gameOver"    -> gameUI.onGameOver(p);
                case "info"        -> view.showMessage(String.valueOf(p.get("message")));
                default            -> view.showMessage("Push: " + msg.type + " " + p);
            }
        });
    }
}