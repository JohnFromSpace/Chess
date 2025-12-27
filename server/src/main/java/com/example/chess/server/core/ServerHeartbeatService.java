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
        this.store = store;
        this.instanceId = instanceId;
    }

    public void start() {
        exec.scheduleAtFixedRate(() -> {
            ServerState s = new ServerState();
            s.instanceId = instanceId;
            s.lastHeartbeatAtMs = System.currentTimeMillis();
            s.lastShutdownAtMs = 0L;
            s.graceful = false;
            store.write(s);
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void markGracefulShutdown() {
        ServerState s = new ServerState();
        s.instanceId = instanceId;
        long now = System.currentTimeMillis();
        s.lastHeartbeatAtMs = now;
        s.lastShutdownAtMs = now;
        s.graceful = true;
        store.write(s);
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}