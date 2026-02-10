package com.example.chess.server.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String PROP_EXPORT_ENABLED = "chess.metrics.export.enabled";
    private static final String PROP_EXPORT_PATH = "chess.metrics.export.path";
    private static final String PROP_EXPORT_FORMAT = "chess.metrics.export.format";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean(false);

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

    private final boolean exportEnabled;
    private final Path exportPath;
    private final String exportFormat;

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

        this.exportEnabled = parseBoolean(PROP_EXPORT_ENABLED, false);
        String exportPathRaw = System.getProperty(PROP_EXPORT_PATH, "logs/metrics.json");
        this.exportPath = exportEnabled ? Path.of(exportPathRaw) : null;
        String exportFmt = System.getProperty(PROP_EXPORT_FORMAT, "json");
        this.exportFormat = exportFmt == null ? "json" : exportFmt.trim().toLowerCase(Locale.ROOT);

        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!logEnabled && !alertsEnabled && !exportEnabled) return;
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

        if (exportEnabled && exportPath != null) {
            try {
                writeExport(snap);
            } catch (Exception e) {
                Log.warn("Metrics export failed: " + exportPath, e);
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

    private void writeExport(Map<String, Object> snap) throws Exception {
        String json = GSON.toJson(snap);
        if ("ndjson".equals(exportFormat)) {
            appendLine(exportPath, json);
        } else {
            writeAtomically(exportPath, json);
        }
    }

    private static void appendLine(Path target, String line) throws Exception {
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void writeAtomically(Path target, String content) throws Exception {
        Path dir = target.getParent();
        Path tmpDir = dir != null ? dir : Path.of(".");
        Path tmp = null;
        boolean moved = false;
        try {
            if (dir != null) Files.createDirectories(dir);
            tmp = Files.createTempFile(tmpDir, target.getFileName().toString(), ".tmp");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            forceDirectory(tmpDir);
        } finally {
            if (!moved && tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception e) {
                    Log.warn("Failed to clean up temp file: " + tmp, e);
                }
            }
        }
    }

    private static void forceDirectory(Path dir) {
        if (dir == null) return;
        if (WINDOWS) {
            if (DIR_FSYNC_WARNED.compareAndSet(false, true)) {
                Log.warn("Directory fsync skipped on Windows (not supported): " + dir, null);
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (Exception e) {
            Log.warn("Directory fsync failed for: " + dir, e);
        }
    }
}
