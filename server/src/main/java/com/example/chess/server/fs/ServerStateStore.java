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
            if (!Files.exists(file)) throw new IllegalArgumentException("The file " + file.getFileName() + "doesn't exist.");
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, ServerState.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read the server state: " + e.getMessage());
        }
    }

    public void write(ServerState s) {
        try {
            if (s == null) throw new IllegalArgumentException("There is no current state for the server.");
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

        if (prev.getGraceful() && prev.getLastShutdownAtMs() > 0) return prev.getLastShutdownAtMs();
        if (prev.getLastHeartbeatAtMs() > 0) return prev.getLastHeartbeatAtMs();

        return now;
    }
}