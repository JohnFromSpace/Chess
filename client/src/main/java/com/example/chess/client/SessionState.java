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

    private final Queue<Runnable> uiActions = new ConcurrentLinkedQueue<>();

    private boolean autoShowBoard = true;
    private String lastSentMove;

    private volatile boolean waitingForMatch;
    public boolean isWaitingForMatch() { return waitingForMatch; }
    public void setWaitingForMatch(boolean v) { waitingForMatch = v; }

    private long whiteTimeMs;
    private long blackTimeMs;
    private boolean whiteToMove;
    private long lastClockSyncAtMs; // System.currentTimeMillis()

    public long getWhiteTimeMs() { return whiteTimeMs; }
    public long getBlackTimeMs() { return blackTimeMs; }
    public boolean isWhiteToMove() { return whiteToMove; }

    public void syncClocks(long whiteMs, long blackMs, boolean whiteToMove) {
        this.whiteTimeMs = Math.max(0, whiteMs);
        this.blackTimeMs = Math.max(0, blackMs);
        this.whiteToMove = whiteToMove;
        this.lastClockSyncAtMs = System.currentTimeMillis();
    }

    public void tickClocks() {
        long now = System.currentTimeMillis();
        long dt = now - lastClockSyncAtMs;
        if (dt <= 0) return;

        if (whiteToMove) whiteTimeMs = Math.max(0, whiteTimeMs - dt);
        else blackTimeMs = Math.max(0, blackTimeMs - dt);

        lastClockSyncAtMs = now;
    }

    public boolean isAutoShowBoard() { return autoShowBoard; }
    public void setAutoShowBoard(boolean autoShowBoard) { this.autoShowBoard = autoShowBoard; }

    public String getLastSentMove() { return lastSentMove; }
    public void setLastSentMove(String lastSentMove) { this.lastSentMove = lastSentMove; }

    public String getLastBoard() { return lastBoard; }
    public void setLastBoard(String lastBoard) { this.lastBoard = lastBoard; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getActiveGameId() { return activeGameId; }
    public void setActiveGameId(String activeGameId) { this.activeGameId = activeGameId; }

    public boolean isInGame() { return inGame; }
    public void setInGame(boolean inGame) { this.inGame = inGame; }

    public boolean isWhite() { return isWhite; }
    public void setWhite(boolean white) { isWhite = white; }

    public void clearGame() {
        this.activeGameId = null;
        this.inGame = false;
        this.isWhite = false;
        this.lastBoard = null;
        this.waitingForMatch = false;
        this.lastSentMove = null;
        whiteTimeMs = 0;
        blackTimeMs = 0;
        whiteToMove = false;
        lastClockSyncAtMs = 0;
    }

    public void postUi(Runnable r) {
        if (r != null) uiActions.add(r);
    }

    public void drainUi() {
        Runnable r;
        while ((r = uiActions.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }
}