package com.example.chess.server.fs;

public final class ServerState {
    private String instanceId;
    private long lastHeartbeatAtMs;
    private long lastShutdownAtMs;
    private boolean graceful;

    public ServerState() {}

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    public String getInstanceId() {
        return instanceId;
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