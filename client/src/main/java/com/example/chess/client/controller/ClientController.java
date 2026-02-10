package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.screen.AuthScreen;
import com.example.chess.client.ui.screen.InGameScreen;
import com.example.chess.client.ui.screen.LobbyScreen;
import com.example.chess.client.view.ConsoleView;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ClientController {
    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state = new SessionState();

    public ClientController(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
        GameUIOrchestrator gameUI = new GameUIOrchestrator(conn, view, state);
        this.conn.setPushHandler(new ClientPushRouter(view, state, gameUI)::handle);
    }

    public void shutdownGracefully() {
        try {
            if (state.getUser() != null && conn.isOpen()) {
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
            try {
                if (state.getUser() == null) {
                    new AuthScreen(conn, view, state).show();
                }

                if (state.isExitReqeuested()) break;

                if (state.getUser() != null && !state.isInGame()) {
                    new LobbyScreen(conn, view, state).show();
                }

                if (state.isExitReqeuested()) break;

                if (state.isInGame()) {
                    new InGameScreen(conn, view, state).show();
                }
            } catch (RuntimeException e) {
                if (handleDisconnect(e)) break;
                throw e;
            }
        }
    }

    private void tryJoin(java.util.concurrent.CompletableFuture<?> future){
        if(future == null) return;
        try {
            if (conn.isOpen() && state.getUser() != null) {
                future.orTimeout(2, TimeUnit.SECONDS).join();
            }
        } catch (RuntimeException e) {
            if (!handleDisconnect(e)) {
                com.example.chess.client.util.Log.warn("Request failed.", e);
            }
        }
    }

    private boolean handleDisconnect(RuntimeException e) {
        Throwable root = rootCause(e);
        if (root instanceof IOException) {
            view.showError("Disconnected from server.");
            state.requestExit();
            return true;
        }
        return false;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
