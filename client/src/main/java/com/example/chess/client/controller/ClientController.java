package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.AuthScreen;
import com.example.chess.client.ui.InGameScreen;
import com.example.chess.client.ui.LobbyScreen;
import com.example.chess.client.view.ConsoleView;

public class ClientController {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state = new SessionState();

    public ClientController(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
        this.conn.setPushHandler(new ClientPushRouter(conn, view, state)::handle);
    }

    public void run() {
        while (true) {
            new AuthScreen(conn, view, state).show();
            new LobbyScreen(conn, view, state).show();
            if (state.isInGame()) new InGameScreen(conn, view, state).show();
        }
    }
}
