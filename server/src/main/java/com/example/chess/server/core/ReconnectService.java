package com.example.chess.server.core;

import java.util.Map;
import java.util.concurrent.*;

public final class ReconnectService {

    private final long graceMs;

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "reconnect-grace");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public ReconnectService(long graceMs) {
        this.graceMs = graceMs;
    }

    public long getGraceMs() {
        return graceMs;
    }

    public void scheduleDrop(String key, Runnable task, long delayMs) {
        cancel(key);
        long d = Math.max(0L, delayMs);
        ScheduledFuture<?> f = exec.schedule(() -> {
            try { task.run(); }
            finally { pending.remove(key); }
        }, d, TimeUnit.MILLISECONDS);
        pending.put(key, f);
    }

    public void cancel(String key) {
        ScheduledFuture<?> f = pending.remove(key);
        if (f != null) f.cancel(false);
    }
}