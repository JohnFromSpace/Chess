package com.example.chess.server.util;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerMetricsReporter implements AutoCloseable {
    private static final String PROP_LOG_ENABLED = "chess.metrics.log.enabled";
    private static final String PROP_LOG_INTERVAL_MS = "chess.metrics.log.intervalMs";

    private static final String PROP_ALERT_ENABLED = "chess.metrics.alert.enabled";
    private static final String PROP_ALERT_CONN_CURRENT = "chess.metrics.alert.connections.current";
    private static final String PROP_ALERT_QUEUE_SIZE = "chess.metrics.alert.matchmaking.queue";
    private static final String PROP_ALERT_ACTIVE_GAMES = "chess.metrics.alert.active.games";
    private static final String PROP_ALERT_HEAP_USED_PCT = "chess.metrics.alert.heap.used.pct";
    private static final String PROP_ALERT_ERROR_RATE_PCT = "chess.metrics.alert.error.rate.pct";

    private final ServerMetrics metrics;
    private final ScheduledExecutorService exec;
    private final boolean logEnabled;
    private final boolean alertsEnabled;
    private final long intervalMs;

    private final long warnConnCurrent;
    private final long warnQueueSize;
    private final long warnActiveGames;
    private final double warnHeapUsedPct;
    private final double warnErrorRatePct;

    private long lastRequests;
    private long lastErrors;

    public ServerMetricsReporter(ServerMetrics metrics) {
        if (metrics == null) throw new IllegalArgumentException("metrics is null");
        this.metrics = metrics;

        this.logEnabled = parseBoolean(PROP_LOG_ENABLED, true);
        this.alertsEnabled = parseBoolean(PROP_ALERT_ENABLED, true);
        this.intervalMs = parseLong(PROP_LOG_INTERVAL_MS, 60_000L);

        this.warnConnCurrent = parseLong(PROP_ALERT_CONN_CURRENT, -1L);
        this.warnQueueSize = parseLong(PROP_ALERT_QUEUE_SIZE, -1L);
        this.warnActiveGames = parseLong(PROP_ALERT_ACTIVE_GAMES, -1L);
        this.warnHeapUsedPct = parseDouble(PROP_ALERT_HEAP_USED_PCT, -1.0);
        this.warnErrorRatePct = parseDouble(PROP_ALERT_ERROR_RATE_PCT, 5.0);

        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!logEnabled && !alertsEnabled) return;
        long period = Math.max(1_000L, intervalMs);
        exec.scheduleAtFixedRate(this::report, period, period, TimeUnit.MILLISECONDS);
    }

    private void report() {
        Map<String, Object> snap = metrics.snapshot();
        long connectionsCurrent = asLong(snap.get("connectionsCurrent"));
        long requestsTotal = asLong(snap.get("requestsTotal"));
        long requestsErrors = asLong(snap.get("requestsErrors"));
        long requestsRateLimited = asLong(snap.get("requestsRateLimited"));
        long requestsInvalid = asLong(snap.get("requestsInvalid"));
        long onlineUsers = asLong(snap.get("onlineUsers"));
        long matchmakingQueue = asLong(snap.get("matchmakingQueue"));
        long activeGames = asLong(snap.get("activeGames"));
        long heapUsed = asLong(snap.get("heapUsedBytes"));
        long heapMax = asLong(snap.get("heapMaxBytes"));

        double heapUsedPct = heapMax > 0 ? (heapUsed * 100.0) / heapMax : 0.0;

        long reqDelta = requestsTotal - lastRequests;
        long errDelta = requestsErrors - lastErrors;
        double errorRatePct = reqDelta > 0 ? (errDelta * 100.0) / reqDelta : 0.0;

        if (logEnabled) {
            Log.info("Metrics: conn=" + connectionsCurrent
                    + " req=" + requestsTotal
                    + " err=" + requestsErrors
                    + " rl=" + requestsRateLimited
                    + " inv=" + requestsInvalid
                    + " online=" + onlineUsers
                    + " queue=" + matchmakingQueue
                    + " games=" + activeGames
                    + " heapUsedPct=" + fmtPct(heapUsedPct)
                    + " errRatePct=" + fmtPct(errorRatePct));
        }

        if (alertsEnabled) {
            if (warnConnCurrent > 0 && connectionsCurrent >= warnConnCurrent) {
                Log.warn("Metric alert: connectionsCurrent=" + connectionsCurrent + " threshold=" + warnConnCurrent, null);
            }
            if (warnQueueSize > 0 && matchmakingQueue >= warnQueueSize) {
                Log.warn("Metric alert: matchmakingQueue=" + matchmakingQueue + " threshold=" + warnQueueSize, null);
            }
            if (warnActiveGames > 0 && activeGames >= warnActiveGames) {
                Log.warn("Metric alert: activeGames=" + activeGames + " threshold=" + warnActiveGames, null);
            }
            if (warnHeapUsedPct > 0 && heapUsedPct >= warnHeapUsedPct) {
                Log.warn("Metric alert: heapUsedPct=" + fmtPct(heapUsedPct) + " threshold=" + fmtPct(warnHeapUsedPct), null);
            }
            if (warnErrorRatePct > 0 && errorRatePct >= warnErrorRatePct) {
                Log.warn("Metric alert: errorRatePct=" + fmtPct(errorRatePct) + " threshold=" + fmtPct(warnErrorRatePct), null);
            }
        }

        lastRequests = requestsTotal;
        lastErrors = requestsErrors;
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    private static String fmtPct(double value) {
        return String.format("%.2f", value);
    }

    private static long parseLong(String key, long defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            Log.warn("Invalid long for " + key + ": " + raw, e);
            return defaultValue;
        }
    }

    private static double parseDouble(String key, double defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            Log.warn("Invalid double for " + key + ": " + raw, e);
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String key, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        return Boolean.parseBoolean(raw.trim());
    }
}
