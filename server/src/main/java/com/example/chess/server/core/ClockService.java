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
        if (g == null || g.getId() == null) throw new IllegalArgumentException("There is no game.");

        State s = new State();
        s.whiteMs = g.getWhiteTimeMs();
        s.blackMs = g.getBlackTimeMs();
        s.whiteToMove = g.isWhiteMove();
        s.incrementMs = g.getIncrementMs();
        s.lastTickMs = System.currentTimeMillis();

        clocks.put(g.getId(), s);
    }

    public void stop(String gameId) {
        if (gameId != null) clocks.remove(gameId);
    }

    public void onMoveApplied(Game g) {
        if (g == null || g.getId() == null) throw new IllegalArgumentException("There is no game.");
        State s = clocks.get(g.getId());
        if (s == null) throw new IllegalArgumentException("Empty state.");
        synchronized (s) {
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

            g.setWhiteTimeMs(s.whiteMs);
            g.setBlackTimeMs(s.blackMs);
            g.setWhiteMove(s.whiteToMove);
            g.setLastUpdate(now);
        }
    }

    /** Tick without a move (for timeouts). Returns true if someone reached 0. */
    public boolean tick(Game g) {
        if (g == null || g.getId() == null) return false;
        State s = clocks.get(g.getId());
        if (s == null) return false;

        synchronized (s) {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - s.lastTickMs);
            if (elapsed == 0) return false;

            if (s.whiteToMove) s.whiteMs = Math.max(0, s.whiteMs - elapsed);
            else s.blackMs = Math.max(0, s.blackMs - elapsed);

            s.lastTickMs = now;

            g.setWhiteTimeMs(s.whiteMs);
            g.setBlackTimeMs(s.blackMs);
            g.setWhiteMove(s.whiteToMove);
            g.setLastUpdate(now);

            return (s.whiteMs <= 0) || (s.blackMs <= 0);
        }
    }
}
