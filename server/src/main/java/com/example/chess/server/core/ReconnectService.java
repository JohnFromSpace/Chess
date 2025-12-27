package com.example.chess.server.core;

import java.util.Map;
import java.util.concurrent.*;

public final class ReconnectService {
    private final long graceMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reconnect-grace");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public ReconnectService(long graceMs) {
        this.graceMs = graceMs;
    }

    public void scheduleDrop(String key, Runnable action) {
        scheduleDrop(key, graceMs, action);
    }

    public void scheduleDrop(String key, long delayMs, Runnable action) {
        cancel(key);
        long d = Math.max(0L, delayMs);
        tasks.put(key, scheduler.schedule(action, d, TimeUnit.MILLISECONDS));
    }

    public void cancel(String key) {
        ScheduledFuture<?> f = tasks.remove(key);
        if (f != null) f.cancel(false);
    }
}