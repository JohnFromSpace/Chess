package com.example.chess.server.core;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.move.MoveService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MatchmakingService {

    private final Map<String, ClientHandler> queue = new LinkedHashMap<>();
    private final Object queueLock = new Object();
    private final MoveService moves;

    public MatchmakingService(MoveService moves) {
        this.moves = moves;
    }

    public void enqueue(ClientHandler h, User u) throws IOException {
        if (h == null || u == null || u.getUsername() == null) throw new IllegalArgumentException("Empty client handler.");

        synchronized (queueLock) {
            if (queue.containsKey(u.getUsername())) {
                h.sendInfo("Already waiting for opponent.");
                return;
            }

            if (queue.isEmpty()) {
                queue.put(u.getUsername(), h);
                h.sendInfo("Waiting for opponent...");
                return;
            }

            var it = queue.entrySet().iterator();
            var entry = it.next();
            it.remove();

            startMatch(entry.getValue(), entry.getKey(), h, u);
        }
    }

    private void startMatch(ClientHandler h1, String u1, ClientHandler h2, User u2) throws IOException {
        boolean h1IsWhite = Math.random() < 0.5;

        String whiteUser = h1IsWhite ? u1 : u2.getUsername();
        String blackUser = h1IsWhite ? u2.getUsername() : u1;

        Game g = new Game();
        g.setId(UUID.randomUUID().toString());
        g.setWhiteUser(whiteUser);
        g.setBlackUser(blackUser);

        long now = System.currentTimeMillis();
        g.setCreatedAt(now);
        g.setLastUpdate(now);

        g.setTimeControlMs(5 * 60_000L);
        g.setIncrementMs(3_000L);
        g.setWhiteTimeMs(g.getTimeControlMs());
        g.setBlackTimeMs(g.getTimeControlMs());
        g.setWhiteMove(true);

        moves.registerGame(g, whiteUser, blackUser, h1, h2, h1IsWhite);
    }

    public void onDisconnect(User u) {
        if (u == null || u.getUsername() == null || u.getUsername().isBlank()) return;
        String username = u.getUsername();
        synchronized (queueLock) {
            queue.remove(username);
        }
    }
}
