package com.example.chess.client;

import com.example.chess.common.UserModels.User;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SessionState {

    private User user;

    private String activeGameId;
    private boolean inGame;
    private boolean isWhite;
    private String lastBoard;

    private boolean waitingForMatch;
    private boolean autoShowBoard = true;
    private String lastSentMove;

    private long timeControlMs = 5 * 60_000L; // default 05:00
    private long incrementMs = 3_000L;        // default +3s
    private long whiteTimeMs = timeControlMs;
    private long blackTimeMs = timeControlMs;
    private boolean whiteToMove = true;
    private long lastClockSyncAtMs = System.currentTimeMillis();

    private final Queue<Runnable> uiQueue = new ConcurrentLinkedQueue<>();

    public void postUi(Runnable r) {
        if (r != null) uiQueue.add(r);
    }

    public void drainUi() {
        Runnable r;
        while ((r = uiQueue.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getActiveGameId() { return activeGameId; }
    public void setActiveGameId(String activeGameId) { this.activeGameId = activeGameId; }

    public boolean isInGame() { return inGame; }
    public void setInGame(boolean inGame) {
        this.inGame = inGame;
        this.lastClockSyncAtMs = System.currentTimeMillis();
    }

    public boolean isWhite() { return isWhite; }
    public void setWhite(boolean white) { isWhite = white; }

    public String getLastBoard() { return lastBoard; }
    public void setLastBoard(String lastBoard) { this.lastBoard = lastBoard; }

    public boolean isWaitingForMatch() { return waitingForMatch; }
    public void setWaitingForMatch(boolean waitingForMatch) { this.waitingForMatch = waitingForMatch; }

    public boolean isAutoShowBoard() { return autoShowBoard; }
    public void setAutoShowBoard(boolean autoShowBoard) { this.autoShowBoard = autoShowBoard; }

    public String getLastSentMove() { return lastSentMove; }
    public void setLastSentMove(String lastSentMove) { this.lastSentMove = lastSentMove; }

    public void setTimeControlMs(long ms) {
        if (ms > 0) this.timeControlMs = ms;
    }

    public void setIncrementMs(long ms) {
        if (ms >= 0) this.incrementMs = ms;
    }

    // server-sync (authoritative)
    public synchronized void syncClocks(long whiteMs, long blackMs, Boolean whiteToMoveMaybe) {
        if (whiteMs >= 0) this.whiteTimeMs = whiteMs;
        if (blackMs >= 0) this.blackTimeMs = blackMs;
        if (whiteToMoveMaybe != null) this.whiteToMove = whiteToMoveMaybe;
        this.lastClockSyncAtMs = System.currentTimeMillis();
    }

    // local ticking between server updates
    public synchronized void tickClocks() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastClockSyncAtMs;
        if (elapsed <= 0) return;

        if (inGame) {
            if (whiteToMove) whiteTimeMs = Math.max(0, whiteTimeMs - elapsed);
            else blackTimeMs = Math.max(0, blackTimeMs - elapsed);
        }
        lastClockSyncAtMs = now;
    }

    public synchronized long getWhiteTimeMs() { return whiteTimeMs; }
    public synchronized long getBlackTimeMs() { return blackTimeMs; }
    public synchronized boolean isWhiteToMove() { return whiteToMove; }

    public void clearGame() {
        this.activeGameId = null;
        this.inGame = false;
        this.isWhite = false;
        this.lastBoard = null;
        this.waitingForMatch = false;
        this.lastSentMove = null;

        // reset clocks to defaults
        this.whiteTimeMs = timeControlMs;
        this.blackTimeMs = timeControlMs;
        this.whiteToMove = true;
        this.lastClockSyncAtMs = System.currentTimeMillis();
    }
}