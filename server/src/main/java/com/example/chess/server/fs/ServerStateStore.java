package com.example.chess.server.fs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.chess.server.util.Log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class ServerStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean(false);

    private final Path file;
    private final Path lockFile;
    private final Object mutex = new Object();

    public ServerStateStore(Path rootDir) {
        this.file = rootDir.resolve("server-state.json");
        this.lockFile = rootDir.resolve("server-state.json.lock");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to init server state store", e);
        }
    }

    public ServerState read() {
        try {
            return withLock(() -> {
                if (!Files.exists(file)) {
                    return null;
                }
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    if (json.isBlank()) {
                        backupCorruptState();
                        return null;
                    }
                    return GSON.fromJson(json, ServerState.class);
                } catch (Exception e) {
                    backupCorruptState();
                    Log.warn("Failed to read server state, starting fresh.", e);
                    return null;
                }
            });
        } catch (UncheckedIOException e) {
            Log.warn("Failed to lock server state file: " + file, e);
            return null;
        }
    }

    public void write(ServerState s) {
        try {
            if (s == null) throw new IllegalArgumentException("Missing server state.");
            String json = GSON.toJson(s);
            withLock(() -> {
                writeAtomically(file, json);
                return null;
            });
        } catch (UncheckedIOException e) {
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
            forceDirectory(backup.getParent());
        } catch (Exception e) {
            Log.warn("Failed to back up corrupt server state file: " + file, e);
        }
    }

    private <T> T withLock(Supplier<T> action) {
        synchronized (mutex) {
            try {
                Path dir = file.getParent();
                if (dir != null) Files.createDirectories(dir);
                try (FileChannel channel = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                     FileLock lock = channel.lock()) {
                    if (!lock.isValid()) {
                        throw new IOException("Failed to acquire server state lock: " + lockFile);
                    }
                    return action.get();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to lock server state file: " + file, e);
            }
        }
    }

    private static void writeAtomically(Path target, String content) {
        Path dir = target.getParent();
        Path tmpDir = dir != null ? dir : Path.of(".");
        Path tmp = null;
        boolean moved = false;
        try {
            if (dir != null) Files.createDirectories(dir);
            tmp = Files.createTempFile(tmpDir, target.getFileName().toString(), ".tmp");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            forceDirectory(tmpDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        } finally {
            if (!moved && tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    Log.warn("Failed to clean up temp file: " + tmp, e);
                }
            }
        }
    }

    private static void forceDirectory(Path dir) {
        if (dir == null) return;
        if (WINDOWS) {
            if (DIR_FSYNC_WARNED.compareAndSet(false, true)) {
                Log.warn("Directory fsync skipped on Windows (not supported): " + dir, null);
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            Log.warn("Directory fsync failed for: " + dir, e);
        }
    }
}
