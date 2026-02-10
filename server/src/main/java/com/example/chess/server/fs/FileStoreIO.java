package com.example.chess.server.fs;

import com.example.chess.server.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class FileStoreIO {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean(false);

    private FileStoreIO() {}

    static void quarantineFile(Path file, String kind) {
        try {
            if (!Files.exists(file)) return;
            String ts = LocalDateTime.now().format(TS_FMT);
            Path backup = file.resolveSibling(file.getFileName().toString() + ".corrupt-" + ts);
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("Quarantined corrupt " + kind + " file: " + file, null);
        } catch (Exception e) {
            Log.warn("Failed to quarantine corrupt " + kind + " file: " + file, e);
        }
    }

    static void writeAtomically(Path target, String content) throws IOException {
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmpDir = dir != null ? dir : Path.of(".");

        Path tmp = Files.createTempFile(tmpDir, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
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
