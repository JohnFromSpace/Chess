package com.example.chess.server.core;

import com.example.chess.common.model.Game;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClockService {

    private static final class State {
        long whiteMs;
        long blackMs;
        long lastTickMs;
        boolean whiteToMove;
        long incrementMs;
    }

    private final ConcurrentMap<String, State> clocks = new ConcurrentHashMap<>();

    public void register(Game g) {
        if (g == null || g.id == null) return;
        State s = new State();
        s.whiteMs = g.whiteTimeMs;
        s.blackMs = g.blackTimeMs;
        s.whiteToMove = g.whiteMove;
        s.incrementMs = g.incrementMs;
        s.lastTickMs = System.currentTimeMillis();
        clocks.put(g.id, s);
    }

    public void stop(String gameId) {
        if (gameId != null) clocks.remove(gameId);
    }

    public void onMoveApplied(Game g) {
        if (g == null || g.id == null) return;
        State s = clocks.get(g.id);
        if (s == null) return;

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - s.lastTickMs);

        // side that is currently to-move BEFORE flip is the mover
        if (s.whiteToMove) {
            s.whiteMs = Math.max(0, s.whiteMs - elapsed);
            s.whiteMs += Math.max(0, s.incrementMs);
        } else {
            s.blackMs = Math.max(0, s.blackMs - elapsed);
            s.blackMs += Math.max(0, s.incrementMs);
        }

        s.whiteToMove = !s.whiteToMove;
        s.lastTickMs = now;

        g.whiteTimeMs = s.whiteMs;
        g.blackTimeMs = s.blackMs;
        g.whiteMove = s.whiteToMove;
        g.lastUpdate = now;
    }
}