package com.example.chess.server.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class BackupFileWriter {
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final AtomicBoolean DIR_FSYNC_WARNED = new AtomicBoolean(false);

    private BackupFileWriter() {}

    static void writeEntryAtomically(InputStream in, Path target, boolean force) throws IOException {
        Objects.requireNonNull(in, "input");
        Objects.requireNonNull(target, "target");
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmpDir = dir != null ? dir : Path.of(".");
        Path tmp = Files.createTempFile(tmpDir, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try (FileChannel out = FileChannel.open(tmp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             ReadableByteChannel src = Channels.newChannel(in)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (src.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    out.write(buffer);
                }
                buffer.clear();
            }
            out.force(true);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                if (force) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw e;
                }
            }
            moved = true;
            forceDirectory(tmpDir);
        } finally {
            if (!moved) Files.deleteIfExists(tmp);
        }
    }

    private static void forceDirectory(Path dir) {
        if (dir == null) return;
        if (WINDOWS) {
            if (DIR_FSYNC_WARNED.compareAndSet(false, true)) {
                System.err.println("Warning: directory fsync skipped on Windows (not supported): " + dir);
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            System.err.println("Warning: directory fsync failed for " + dir + ": " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
