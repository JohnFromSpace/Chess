package com.example.chess.server.fs;

public final class ServerState {
    private long lastHeartbeatAtMs;
    private long lastShutdownAtMs;
    private boolean graceful;

    public ServerState() {}

    public void setInstanceId() {
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        this.lastHeartbeatAtMs = lastHeartbeatAtMs;
    }

    public void setLastShutdownAtMs(long lastShutdownAtMs) {
        this.lastShutdownAtMs = lastShutdownAtMs;
    }

    public void setGraceful(boolean graceful) {
        this.graceful = graceful;
    }

    public long getLastHeartbeatAtMs() {
        return lastHeartbeatAtMs;
    }

    public long getLastShutdownAtMs() {
        return lastShutdownAtMs;
    }

    public boolean getGraceful() {
        return graceful;
    }
}