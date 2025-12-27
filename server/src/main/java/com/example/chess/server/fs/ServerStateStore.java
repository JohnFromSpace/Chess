package com.example.chess.server.fs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ServerStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public ServerStateStore(Path rootDir) {
        this.file = rootDir.resolve("server-state.json");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to init server state store", e);
        }
    }

    public ServerState read() {
        try {
            if (!Files.exists(file)) return null;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, ServerState.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void write(ServerState s) {
        try {
            if (s == null) return;
            String json = GSON.toJson(s);
            Files.writeString(
                    file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to write server state", e);
        }
    }

    public long estimateLastDownAtMs(ServerState prev) {
        long now = System.currentTimeMillis();
        if (prev == null) return now;

        if (prev.graceful && prev.lastShutdownAtMs > 0) return prev.lastShutdownAtMs;
        if (prev.lastHeartbeatAtMs > 0) return prev.lastHeartbeatAtMs;

        return now;
    }
}