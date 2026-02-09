package com.example.chess.server.util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

public final class ServerMetrics {

    private final long startTimeMs;
    private final IntSupplier onlineUsers;
    private final IntSupplier matchmakingQueue;
    private final IntSupplier activeGames;

    private final AtomicLong currentConnections = new AtomicLong();
    private final AtomicLong totalConnections = new AtomicLong();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalRateLimited = new AtomicLong();
    private final AtomicLong totalInvalidRequests = new AtomicLong();
    private final AtomicLong lastRequestAtMs = new AtomicLong();

    private final ConcurrentMap<String, AtomicLong> requestsByType = new ConcurrentHashMap<>();

    public ServerMetrics(IntSupplier onlineUsers, IntSupplier matchmakingQueue, IntSupplier activeGames) {
        this.startTimeMs = System.currentTimeMillis();
        this.onlineUsers = onlineUsers;
        this.matchmakingQueue = matchmakingQueue;
        this.activeGames = activeGames;
    }

    public void onConnectionOpen() {
        totalConnections.incrementAndGet();
        currentConnections.incrementAndGet();
    }

    public void onConnectionClosed() {
        long value = currentConnections.decrementAndGet();
        if (value < 0) currentConnections.set(0);
    }

    public void onRequest(String type) {
        totalRequests.incrementAndGet();
        lastRequestAtMs.set(System.currentTimeMillis());
        if (type == null) return;
        requestsByType.computeIfAbsent(type, k -> new AtomicLong()).incrementAndGet();
    }

    public void onError(String type) {
        totalErrors.incrementAndGet();
        if (type != null) {
            requestsByType.computeIfAbsent(type + "_error", k -> new AtomicLong()).incrementAndGet();
        }
    }

    public void onRateLimited() {
        totalRateLimited.incrementAndGet();
    }

    public void onInvalidRequest() {
        totalInvalidRequests.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        Runtime rt = Runtime.getRuntime();

        Map<String, Object> out = new HashMap<>();
        out.put("status", "ok");
        out.put("timestampMs", now);
        out.put("uptimeMs", now - startTimeMs);
        out.put("connectionsCurrent", currentConnections.get());
        out.put("connectionsTotal", totalConnections.get());
        out.put("requestsTotal", totalRequests.get());
        out.put("requestsErrors", totalErrors.get());
        out.put("requestsRateLimited", totalRateLimited.get());
        out.put("requestsInvalid", totalInvalidRequests.get());
        out.put("lastRequestAtMs", lastRequestAtMs.get());
        out.put("onlineUsers", safeGet(onlineUsers));
        out.put("matchmakingQueue", safeGet(matchmakingQueue));
        out.put("activeGames", safeGet(activeGames));
        out.put("heapUsedBytes", rt.totalMemory() - rt.freeMemory());
        out.put("heapCommittedBytes", rt.totalMemory());
        out.put("heapMaxBytes", rt.maxMemory());
        out.put("availableProcessors", rt.availableProcessors());

        Map<String, Object> byType = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> entry : requestsByType.entrySet()) {
            byType.put(entry.getKey(), entry.getValue().get());
        }
        out.put("requestsByType", byType);

        return out;
    }

    private static int safeGet(IntSupplier supplier) {
        if (supplier == null) return -1;
        try {
            return supplier.getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }
}
