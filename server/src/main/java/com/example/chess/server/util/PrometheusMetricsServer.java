package com.example.chess.server.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public final class PrometheusMetricsServer implements AutoCloseable {
    private static final String PROP_ENABLED = "chess.metrics.prometheus.enabled";
    private static final String PROP_HOST = "chess.metrics.prometheus.host";
    private static final String PROP_PORT = "chess.metrics.prometheus.port";
    private static final String PROP_PATH = "chess.metrics.prometheus.path";

    private final ServerMetrics metrics;
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String path;
    private HttpServer server;

    public PrometheusMetricsServer(ServerMetrics metrics) {
        if (metrics == null) throw new IllegalArgumentException("metrics is null");
        this.metrics = metrics;
        this.enabled = Boolean.parseBoolean(System.getProperty(PROP_ENABLED, "false"));
        this.host = System.getProperty(PROP_HOST, "0.0.0.0").trim();
        this.port = parseInt(PROP_PORT, 9102);
        this.path = normalizePath(System.getProperty(PROP_PATH, "/metrics"));
    }

    public void start() {
        if (!enabled || server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext(path, this::handleMetrics);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "prometheus-metrics");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            Log.info("Prometheus metrics exporter started on " + host + ":" + port + path);
        } catch (IOException e) {
            Log.warn("Failed to start Prometheus metrics server.", e);
            server = null;
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String body = formatMetrics();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String formatMetrics() {
        Map<String, Object> snap = metrics.snapshot();
        StringBuilder sb = new StringBuilder(1024);

        appendGauge(sb, "chess_connections_current", "Current open connections.", snap.get("connectionsCurrent"));
        appendCounter(sb, "chess_connections_total", "Total connections since start.", snap.get("connectionsTotal"));
        appendCounter(sb, "chess_requests_total", "Total requests since start.", snap.get("requestsTotal"));
        appendCounter(sb, "chess_requests_errors_total", "Total request errors since start.", snap.get("requestsErrors"));
        appendCounter(sb, "chess_requests_rate_limited_total", "Total rate limited requests.", snap.get("requestsRateLimited"));
        appendCounter(sb, "chess_requests_invalid_total", "Total invalid requests.", snap.get("requestsInvalid"));
        appendGauge(sb, "chess_online_users", "Current online users.", snap.get("onlineUsers"));
        appendGauge(sb, "chess_matchmaking_queue", "Current matchmaking queue size.", snap.get("matchmakingQueue"));
        appendGauge(sb, "chess_active_games", "Current active games.", snap.get("activeGames"));
        appendGauge(sb, "chess_heap_used_bytes", "Current heap used in bytes.", snap.get("heapUsedBytes"));
        appendGauge(sb, "chess_heap_committed_bytes", "Current heap committed in bytes.", snap.get("heapCommittedBytes"));
        appendGauge(sb, "chess_heap_max_bytes", "Max heap size in bytes.", snap.get("heapMaxBytes"));
        appendGauge(sb, "chess_available_processors", "Available processors.", snap.get("availableProcessors"));
        appendGauge(sb, "chess_uptime_ms", "Uptime in milliseconds.", snap.get("uptimeMs"));
        appendGauge(sb, "chess_last_request_timestamp_ms", "Last request timestamp (ms).", snap.get("lastRequestAtMs"));

        double heapUsed = asDouble(snap.get("heapUsedBytes"));
        double heapMax = asDouble(snap.get("heapMaxBytes"));
        if (heapMax > 0) {
            appendGauge(sb, "chess_heap_used_pct", "Heap used percentage.", (heapUsed * 100.0) / heapMax);
        }

        Object byTypeRaw = snap.get("requestsByType");
        if (byTypeRaw instanceof Map<?, ?> byType) {
            appendHelp(sb, "chess_requests_by_type_total", "Requests by type.");
            appendType(sb, "chess_requests_by_type_total", "counter");
            for (Map.Entry<?, ?> entry : byType.entrySet()) {
                String key = entry.getKey() == null ? "unknown" : String.valueOf(entry.getKey());
                double value = asDouble(entry.getValue());
                sb.append("chess_requests_by_type_total{type=\"")
                        .append(escapeLabel(key))
                        .append("\"} ")
                        .append(formatNumber(value))
                        .append('\n');
            }
        }

        return sb.toString();
    }

    private static void appendGauge(StringBuilder sb, String name, String help, Object value) {
        if (!isNumber(value)) return;
        appendHelp(sb, name, help);
        appendType(sb, name, "gauge");
        sb.append(name).append(' ').append(formatNumber(asDouble(value))).append('\n');
    }

    private static void appendCounter(StringBuilder sb, String name, String help, Object value) {
        if (!isNumber(value)) return;
        appendHelp(sb, name, help);
        appendType(sb, name, "counter");
        sb.append(name).append(' ').append(formatNumber(asDouble(value))).append('\n');
    }

    private static void appendHelp(StringBuilder sb, String name, String help) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n');
    }

    private static void appendType(StringBuilder sb, String name, String type) {
        sb.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static boolean isNumber(Object value) {
        return value instanceof Number;
    }

    private static double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String escapeLabel(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"");
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return "/metrics";
        String p = raw.trim();
        return p.startsWith("/") ? p : "/" + p;
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
}