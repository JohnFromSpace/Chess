package com.example.chess.server;

import com.example.chess.server.util.Log;

import java.nio.file.Path;

final class ServerConfig {
    final int port;
    final Path dataDir;
    final boolean tls;
    final boolean tlsClientAuth;
    final int coreThreads;
    final int maxThreads;
    final int queueCapacity;
    final int maxLineChars;
    final int readTimeoutMs;

    private ServerConfig(int port,
                         Path dataDir,
                         boolean tls,
                         boolean tlsClientAuth,
                         int coreThreads,
                         int maxThreads,
                         int queueCapacity,
                         int maxLineChars,
                         int readTimeoutMs) {
        this.port = port;
        this.dataDir = dataDir;
        this.tls = tls;
        this.tlsClientAuth = tlsClientAuth;
        this.coreThreads = coreThreads;
        this.maxThreads = maxThreads;
        this.queueCapacity = queueCapacity;
        this.maxLineChars = maxLineChars;
        this.readTimeoutMs = readTimeoutMs;
    }

    static ServerConfig load() {
        int port = parseInt("chess.server.port", 5000);
        Path dataDir = parsePath("chess.data.dir", Path.of("data"));
        boolean tls = parseBoolean("chess.tls.enabled", false);
        boolean tlsClientAuth = parseBoolean("chess.tls.clientAuth", false);
        int coreThreads = parseInt("chess.server.threads.core", 8);
        int maxThreads = parseInt("chess.server.threads.max", 64);
        int queueCapacity = parseInt("chess.server.queue.capacity", 256);
        int maxLineChars = parseInt("chess.socket.maxLineChars", 16384);
        int readTimeoutMs = parseInt("chess.socket.readTimeoutMs", 60000);
        return new ServerConfig(port, dataDir, tls, tlsClientAuth, coreThreads, maxThreads, queueCapacity,
                maxLineChars, readTimeoutMs);
    }

    void logSummary() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Config summary: ");
        sb.append("dataDir=").append(dataDir.toAbsolutePath());
        sb.append(", port=").append(port);
        sb.append(", tls=").append(tls);
        sb.append(", threads=").append(coreThreads).append("/").append(maxThreads);
        sb.append(", queueCap=").append(queueCapacity);
        sb.append(", socket.maxLineChars=").append(maxLineChars);
        sb.append(", socket.readTimeoutMs=").append(readTimeoutMs);
        Log.info(sb.toString());
    }

    private static int parseInt(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Log.warn("Invalid integer for " + key + ": " + raw + " (using default " + defaultValue + ")", e);
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String key, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        return Boolean.parseBoolean(raw.trim());
    }

    private static Path parsePath(String key, Path defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        return Path.of(raw.trim());
    }
}
