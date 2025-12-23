package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.*;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.common.proto.ResponseMessage;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;

import java.util.List;

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
                String boardStr = asString(p, "board");
                if (boardStr != null) {
                    model.setLastBoard(boardStr);
                    view.showBoard(boardStr);
                }
            }
            case "move" -> {
                String moveStr = asString(p, "move");
                boolean whiteInCheck = asBool(p, "whiteInCheck", false);
                boolean blackInCheck = asBool(p, "blackInCheck", false);
                view.showMove(moveStr, whiteInCheck, blackInCheck);

                String boardStr = asString(p, "board");
                if (boardStr != null) {
                    model.setLastBoard(boardStr);
                    view.showBoard(boardStr);
                }
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

    private Menu buildInGameMenu() {
        return new Menu("Game",
                List.of(
                        new MenuItem(1, "Make move",     () -> { doMove();      return true; }),
                        new MenuItem(2, "Offer draw",    () -> { offerDraw();   return true; }),
                        new MenuItem(3, "Resign",        () -> { resign();      return true; }),
                        new MenuItem(4, "Print board",   () -> { printBoard();  return true; })
                ));
    }

    private void printBoard() {
        String b = model.getLastBoard();
        if (b == null) view.showMessage("No board received yet.");
        else view.showBoard(b);
    }
}