package com.example.chess.server.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class MetricsFileExporter {
    private static final String PROP_EXPORT_ENABLED = "chess.metrics.export.enabled";
    private static final String PROP_EXPORT_PATH = "chess.metrics.export.path";
    private static final String PROP_EXPORT_FORMAT = "chess.metrics.export.format";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean(false);

    private final boolean enabled;
    private final Path exportPath;
    private final String exportFormat;

    MetricsFileExporter() {
        this.enabled = Boolean.parseBoolean(System.getProperty(PROP_EXPORT_ENABLED, "false"));
        String exportPathRaw = System.getProperty(PROP_EXPORT_PATH, "logs/metrics.json");
        this.exportPath = enabled ? Path.of(exportPathRaw) : null;
        String exportFmt = System.getProperty(PROP_EXPORT_FORMAT, "json");
        this.exportFormat = exportFmt == null ? "json" : exportFmt.trim().toLowerCase(Locale.ROOT);
    }

    boolean isEnabled() {
        return enabled && exportPath != null;
    }

    void export(Map<String, Object> snap) throws Exception {
        if (!isEnabled()) return;
        String json = GSON.toJson(snap);
        if ("ndjson".equals(exportFormat)) {
            appendLine(exportPath, json);
        } else {
            writeAtomically(exportPath, json);
        }
    }

    private static void appendLine(Path target, String line) throws Exception {
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void writeAtomically(Path target, String content) throws Exception {
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
        } finally {
            if (!moved && tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception e) {
                    Log.warn("Failed to clean up temp file: " + tmp, e);
                }
            }
        }
    }

    private static void forceDirectory(Path dir) {
        if (dir == null) return;
        if (WINDOWS) {
            if (DIR_FSYNC_WARNED.compareAndSet(false, true)) {
                Log.info("Directory fsync skipped on Windows (not supported): " + dir);
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (Exception e) {
            Log.warn("Directory fsync failed for: " + dir, e);
        }
    }
}
