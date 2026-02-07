package com.example.chess.server.security;

import java.util.concurrent.TimeUnit;

public final class RateLimiter {
    private final int capacity;
    private final long refillEveryMs;

    private int tokens;
    private long lastRefillMs;

    public RateLimiter(int capacity, long refillEverySeconds) {
        if (capacity <= 0) throw new IllegalArgumentException();
        if (refillEverySeconds <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.refillEveryMs = TimeUnit.SECONDS.toMillis(refillEverySeconds);
        this.tokens = capacity;
        this.lastRefillMs = System.currentTimeMillis();
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillMs;
        if (elapsed >= refillEveryMs) {
            long periods = elapsed / refillEveryMs;
            long add = periods * capacity;
            tokens = (int) Math.min(capacity, (long) tokens + add);
            lastRefillMs += periods * refillEveryMs;
        }
        if (tokens <= 0) return false;
        tokens--;
        return true;
    }
}
