package com.example.chess.server.tools;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class DataBackupService {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_BACKUP_PREFIX = "chess-data-";

    private DataBackupService() {}

    static Path defaultBackupPath() {
        String ts = LocalDateTime.now().format(TS_FMT);
        return Path.of(DEFAULT_BACKUP_PREFIX + ts + ".zip");
    }

    static void backup(Path dataDir, Path outZip, boolean includeCorrupt) throws IOException {
        if (dataDir == null || !Files.exists(dataDir) || !Files.isDirectory(dataDir)) {
            throw new IllegalArgumentException("Data directory not found: " + dataDir);
        }
        if (outZip == null) throw new IllegalArgumentException("Missing output zip path.");

        Path parent = outZip.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(outZip, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            Files.walkFileTree(dataDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new FileVisitor<>() {
                @Override
                public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    Path rel = dataDir.relativize(file);
                    if (!BackupEntryRules.shouldInclude(rel, includeCorrupt)) return FileVisitResult.CONTINUE;

                    String entryName = rel.toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult visitFileFailed(Path file, @NotNull IOException exc) throws IOException {
                    throw exc;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        System.out.println("Backup created: " + outZip.toAbsolutePath());
    }

    static void restore(Path zip, Path dataDir, boolean force, boolean purge, boolean includeCorrupt)
            throws IOException {
        if (zip == null || !Files.exists(zip) || !Files.isRegularFile(zip)) {
            throw new IllegalArgumentException("Backup zip not found: " + zip);
        }
        if (dataDir == null) throw new IllegalArgumentException("Missing data dir.");

        Files.createDirectories(dataDir);

        if (purge) {
            if (!force) {
                throw new IllegalArgumentException("--purge requires --force.");
            }
            purgeDataDir(dataDir);
        }

        Path normalizedRoot = dataDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip, StandardOpenOption.READ))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!BackupEntryRules.isSafeEntry(name)) {
                    throw new IllegalArgumentException("Unsafe entry in zip: " + name);
                }
                if (!BackupEntryRules.shouldRestore(name, includeCorrupt)) {
                    continue;
                }

                Path target = normalizedRoot.resolve(name).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    throw new IllegalArgumentException("Zip entry escapes target directory: " + name);
                }
                if (!force && Files.exists(target)) {
                    throw new IllegalStateException("Target already exists: " + target);
                }

                BackupFileWriter.writeEntryAtomically(zis, target, force);
                zis.closeEntry();
            }
        }

        System.out.println("Restore completed into: " + normalizedRoot);
    }

    private static void purgeDataDir(Path dataDir) throws IOException {
        Path users = dataDir.resolve("users.json");
        Path usersLock = dataDir.resolve("users.json.lock");
        Path serverState = dataDir.resolve("server-state.json");

        deleteIfExists(users);
        deleteIfExists(usersLock);
        deleteIfExists(serverState);

        Path games = dataDir.resolve("games");
        if (!Files.exists(games)) return;
        try (var stream = Files.newDirectoryStream(games)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) continue;
                deleteIfExists(p);
            }
        }
    }

    private static void deleteIfExists(Path p) throws IOException {
        if (p != null && Files.exists(p)) Files.delete(p);
    }
}
