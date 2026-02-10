package com.example.chess.server.client;

import com.example.chess.server.security.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class IpRateLimiter {
    private static final ConcurrentMap<String, Limiter> IP_LIMITERS = new ConcurrentHashMap<>();
    private static final boolean IP_RATE_LIMIT_ENABLED =
            Boolean.parseBoolean(System.getProperty("chess.ratelimit.ip.enabled", "true"));
    private static final long IP_RATE_LIMIT_CAPACITY =
            Long.getLong("chess.ratelimit.ip.capacity", 120L);
    private static final long IP_RATE_LIMIT_REFILL_SECONDS =
            Long.getLong("chess.ratelimit.ip.refillPerSecond", 15L);
    private static final int IP_RATE_LIMIT_MAX_ENTRIES =
            Integer.parseInt(System.getProperty("chess.ratelimit.ip.maxEntries", "10000"));
    private static final long IP_RATE_LIMIT_IDLE_EVICT_MS =
            Long.getLong("chess.ratelimit.ip.idleEvictMs", 900_000L);

    private IpRateLimiter() {}

    static Limiter forIp(String ip) {
        if (!IP_RATE_LIMIT_ENABLED) return null;
        if (ip == null || ip.isBlank()) return null;

        long now = System.currentTimeMillis();
        cleanupIpLimiters(now);

        return IP_LIMITERS.compute(ip, (key, existing) -> {
            boolean expired = IP_RATE_LIMIT_IDLE_EVICT_MS > 0
                    && existing != null
                    && existing.isIdle(now, IP_RATE_LIMIT_IDLE_EVICT_MS);
            if (existing == null || expired) {
                int cap = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, IP_RATE_LIMIT_CAPACITY));
                long refill = Math.max(1L, IP_RATE_LIMIT_REFILL_SECONDS);
                return new Limiter(new RateLimiter(cap, refill), now);
            }
            existing.touch(now);
            return existing;
        });
    }

    private static void cleanupIpLimiters(long now) {
        if (IP_RATE_LIMIT_MAX_ENTRIES <= 0) return;
        if (IP_LIMITERS.size() <= IP_RATE_LIMIT_MAX_ENTRIES) return;
        if (IP_RATE_LIMIT_IDLE_EVICT_MS <= 0) return;

        for (Map.Entry<String, Limiter> entry : IP_LIMITERS.entrySet()) {
            Limiter limiter = entry.getValue();
            if (limiter != null && limiter.isIdle(now, IP_RATE_LIMIT_IDLE_EVICT_MS)) {
                IP_LIMITERS.remove(entry.getKey(), limiter);
            }
        }
    }

    static final class Limiter {
        private final RateLimiter limiter;
        private volatile long lastSeenMs;

        private Limiter(RateLimiter limiter, long now) {
            this.limiter = limiter;
            this.lastSeenMs = now;
        }

        private void touch(long now) {
            lastSeenMs = now;
        }

        private boolean isIdle(long now, long idleMs) {
            return now - lastSeenMs >= idleMs;
        }

        boolean tryAcquire() {
            touch(System.currentTimeMillis());
            return limiter.tryAcquire();
        }
    }
}
