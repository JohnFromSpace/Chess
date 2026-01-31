package com.example.chess.server.core;

import com.example.chess.server.fs.ServerState;
import com.example.chess.server.fs.ServerStateStore;

import java.util.concurrent.*;

public final class ServerHeartbeatService implements AutoCloseable {
    private final ServerStateStore store;
    private final String instanceId;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "server-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public ServerHeartbeatService(ServerStateStore store, String instanceId) {
        if (store == null) throw new IllegalArgumentException("store is null");
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is blank");
        this.store = store;
        this.instanceId = instanceId;
    }

    public void start() {
        exec.scheduleAtFixedRate(() -> {
            ServerState s = new ServerState();
            s.setInstanceId(instanceId);
            s.setLastHeartbeatAtMs(System.currentTimeMillis());
            s.setLastShutdownAtMs(0L);
            s.setGraceful(false);
            store.write(s);
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void markGracefulShutdown() {
        ServerState s = new ServerState();
        s.setInstanceId(instanceId);
        long now = System.currentTimeMillis();
        s.setLastHeartbeatAtMs(now);
        s.setLastShutdownAtMs(now);
        s.setGraceful(true);
        store.write(s);
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}