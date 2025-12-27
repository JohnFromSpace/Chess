package com.example.chess.server.fs;

public final class ServerState {
    public String instanceId;
    public long lastHeartbeatAtMs;
    public long lastShutdownAtMs;
    public boolean graceful;

    public ServerState() {}
}