package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.screen.AuthScreen;
import com.example.chess.client.ui.screen.InGameScreen;
import com.example.chess.client.ui.screen.LobbyScreen;
import com.example.chess.client.view.ConsoleView;

import java.util.concurrent.TimeUnit;

public class ClientController {
    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state = new SessionState();
    private final GameUIOrchestrator gameUI;

    public ClientController(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
        this.gameUI = new GameUIOrchestrator(conn, view, state);
        this.conn.setPushHandler(new ClientPushRouter(conn, view, state, gameUI)::handle);
    }

    public void shutdownGracefully() {
        try {
            if (state.getUser() != null) {
                tryJoin(conn.logout());
            }
        } catch (Exception ex) {
            com.example.chess.client.util.Log.warn("Failed to log out.", ex);
        } finally {
            conn.close();
        }
    }

    public void run() throws InterruptedException {
        while (!state.isExitReqeuested()) {
            if(state.getUser() == null) {
                new AuthScreen(conn, view, state).show();
            }

            if(state.isExitReqeuested()) break;

            if(state.getUser() != null && !state.isInGame()) {
                new LobbyScreen(conn, view, state).show();
            }

            if(state.isExitReqeuested()) break;

            if(state.isInGame()) {
                new InGameScreen(conn, view, state).show();
            }
        }
    }

    private void tryJoin(java.util.concurrent.CompletableFuture<?> future){
        if(future == null) return;
        try {
            if(state.getUser() != null) {
                future.orTimeout(2, TimeUnit.SECONDS).join();
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}
