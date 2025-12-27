package com.example.chess.client.controller;

import com.example.chess.client.SessionState;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.screen.AuthScreen;
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

        Thread uiPump = new Thread(() -> {
            while (true) {
                state.drainUi();
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "ui-pump");
        uiPump.setDaemon(true);
        uiPump.start();
    }

    public void shutdownGracefully() {
        try {
            String gid = state.getActiveGameId();
            if (state.isInGame() && gid != null && !gid.isBlank()) {
                // resign so opponent doesn't wait 60s
                bestEffort(conn.resign(gid), 800);
            }
        } catch (Exception ignored) {}

        try {
            if (state.getUser() != null) {
                bestEffort(conn.logout(), 800);
            }
        } catch (Exception ignored) {}

        try { conn.close(); } catch (Exception ignored) {}
    }

    private static void bestEffort(java.util.concurrent.CompletableFuture<?> fut, long ms) {
        if (fut == null) return;
        try { fut.get(ms, TimeUnit.MILLISECONDS); }
        catch (Exception ignored) {}
    }

    public void run() {
        while (true) {
            new AuthScreen(conn, view, state).show();
            new LobbyScreen(conn, view, state).show();
            if (state.isInGame()) gameUI.runGameLoop();
        }
    }
}