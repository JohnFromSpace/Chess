package com.example.chess.client;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.*;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.proto.ResponseMessage;

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
        switch (msg.type) {
            case "gameStarted" -> {
                String gameId = (String) msg.payload.get("gameId");
                state.setActiveGameId(gameId);
                state.setInGame(true);
                Object color = msg.payload.get("color");
                state.setWhite("white".equals(color));
                view.showMessage("Game started. You are " + color + ". GameId=" + gameId);
            }
            case "move" -> {
                view.showMessage("Move: " + msg.payload.get("move")
                        + (Boolean.TRUE.equals(msg.payload.get("whiteInCheck")) ? " (white in check)" : "")
                        + (Boolean.TRUE.equals(msg.payload.get("blackInCheck")) ? " (black in check)" : ""));
            }
            case "drawOffered" -> view.showMessage("Draw offered by: " + msg.payload.get("by"));
            case "drawDeclined" -> view.showMessage("Draw declined by: " + msg.payload.get("by"));
            case "gameOver" -> {
                view.showMessage("Game over: " + msg.payload.get("result") + " reason=" + msg.payload.get("reason"));
                state.clearGame();
            }
            case "info" -> view.showMessage(String.valueOf(msg.payload.get("message")));
            default -> view.showMessage("Push: " + msg.type + " " + msg.payload);
        }
    }
}