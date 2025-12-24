package com.example.chess.server.core;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MatchmakingService {

    private final Map<String, ClientHandler> queue = new LinkedHashMap<>();
    private final MoveService moves;
    private final ClockService clocks;

    public MatchmakingService(MoveService moves, ClockService clocks) {
        this.moves = moves;
        this.clocks = clocks;
    }

    public synchronized void enqueue(ClientHandler h, User u) throws IOException {
        if (h == null || u == null || u.username == null) return;

        if (queue.containsKey(u.username)) {
            h.sendInfo("Already waiting for opponent.");
            return;
        }

        if (queue.isEmpty()) {
            queue.put(u.username, h);
            h.sendInfo("Waiting for opponent...");
            return;
        }

        // match immediately with first waiting player
        var it = queue.entrySet().iterator();
        var entry = it.next();
        it.remove();

        String u1 = entry.getKey();
        ClientHandler h1 = entry.getValue();

        startMatch(h1, u1, h, u);
    }

    private void startMatch(ClientHandler h1, String u1, ClientHandler h2, User u2) throws IOException {
        boolean h1IsWhite = Math.random() < 0.5;

        String whiteUser = h1IsWhite ? u1 : u2.username;
        String blackUser = h1IsWhite ? u2.username : u1;

        Game g = new Game();
        g.id = UUID.randomUUID().toString();
        g.whiteUser = whiteUser;
        g.blackUser = blackUser;
        g.createdAt = System.currentTimeMillis();
        g.lastUpdate = g.createdAt;

        // defaults (match client defaults)
        g.timeControlMs = 5 * 60_000L;
        g.incrementMs = 3_000L;
        g.whiteTimeMs = g.timeControlMs;
        g.blackTimeMs = g.timeControlMs;
        g.whiteMove = true;

        moves.registerGame(g, whiteUser, blackUser, h1, h2, h1IsWhite);
        clocks.register(g);
    }

    public synchronized void onDisconnect(ClientHandler h, User u) {
        if (u == null || u.username == null) return;
        queue.remove(u.username);
    }
}