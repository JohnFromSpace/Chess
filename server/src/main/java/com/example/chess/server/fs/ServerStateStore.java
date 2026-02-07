package com.example.chess.server.fs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.chess.server.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ServerStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
        if (!Files.exists(file)) {
            return null;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) return null;
            return GSON.fromJson(json, ServerState.class);
        } catch (Exception e) {
            backupCorruptState();
            Log.warn("Failed to read server state, starting fresh.", e);
            return null;
        }
    }

    public void write(ServerState s) {
        try {
            if (s == null) throw new IllegalArgumentException("There is no current state for the server.");
            String json = GSON.toJson(s);
            writeAtomically(file, json);
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

    private void backupCorruptState() {
        try {
            if (!Files.exists(file)) return;
            String ts = LocalDateTime.now().format(TS_FMT);
            Path backup = file.resolveSibling(file.getFileName().toString() + ".corrupt-" + ts);
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Log.warn("Failed to back up corrupt server state file: " + file, e);
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);

        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(
                    tmp,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    Log.warn("Failed to clean up temp file: " + tmp, e);
                }
            }
        }
    }
}
