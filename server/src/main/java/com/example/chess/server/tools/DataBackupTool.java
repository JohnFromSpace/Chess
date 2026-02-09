package com.example.chess.server.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class DataBackupTool {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_DATA_DIR = "data";
    private static final String DEFAULT_BACKUP_PREFIX = "chess-data-";

    private DataBackupTool() {}

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Backup tool error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args == null || args.length == 0) {
            usage();
            return;
        }

        String cmd = args[0].toLowerCase(Locale.ROOT).trim();
        List<String> flags = new ArrayList<>();
        List<String> params = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) flags.add(a);
            else params.add(a);
        }

        boolean includeCorrupt = flags.contains("--include-corrupt");

        switch (cmd) {
            case "backup" -> {
                Path dataDir = params.size() > 0 ? Path.of(params.get(0)) : Path.of(DEFAULT_DATA_DIR);
                Path outZip = params.size() > 1 ? Path.of(params.get(1)) : defaultBackupPath();
                backup(dataDir, outZip, includeCorrupt);
            }
            case "restore" -> {
                if (params.isEmpty()) {
                    throw new IllegalArgumentException("restore requires <backup.zip>.");
                }
                Path zip = Path.of(params.get(0));
                Path dataDir = params.size() > 1 ? Path.of(params.get(1)) : Path.of(DEFAULT_DATA_DIR);
                boolean force = flags.contains("--force");
                boolean purge = flags.contains("--purge");
                restore(zip, dataDir, force, purge, includeCorrupt);
            }
            case "help", "-h", "--help" -> usage();
            default -> throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  backup [dataDir] [outZip] [--include-corrupt]");
        System.out.println("  restore <backup.zip> [dataDir] [--force] [--purge] [--include-corrupt]");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  backup data backups/chess-data.zip");
        System.out.println("  restore backups/chess-data.zip data --force");
    }

    private static Path defaultBackupPath() {
        String ts = LocalDateTime.now().format(TS_FMT);
        return Path.of(DEFAULT_BACKUP_PREFIX + ts + ".zip");
    }

    private static void backup(Path dataDir, Path outZip, boolean includeCorrupt) throws IOException {
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
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    Path rel = dataDir.relativize(file);
                    if (!shouldInclude(rel, includeCorrupt)) return FileVisitResult.CONTINUE;

                    String entryName = rel.toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    throw exc;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        System.out.println("Backup created: " + outZip.toAbsolutePath());
    }

    private static void restore(Path zip, Path dataDir, boolean force, boolean purge, boolean includeCorrupt)
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
                if (!isSafeEntry(name)) {
                    throw new IllegalArgumentException("Unsafe entry in zip: " + name);
                }
                if (!shouldRestore(name, includeCorrupt)) {
                    continue;
                }

                Path target = normalizedRoot.resolve(name).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    throw new IllegalArgumentException("Zip entry escapes target directory: " + name);
                }
                if (!force && Files.exists(target)) {
                    throw new IllegalStateException("Target already exists: " + target);
                }

                writeEntryAtomically(zis, target, force);
                zis.closeEntry();
            }
        }

        System.out.println("Restore completed into: " + normalizedRoot);
    }

    private static void writeEntryAtomically(InputStream in, Path target, boolean force) throws IOException {
        Objects.requireNonNull(in, "input");
        Objects.requireNonNull(target, "target");
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmpDir = dir != null ? dir : Path.of(".");
        Path tmp = Files.createTempFile(tmpDir, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try (OutputStream out = Files.newOutputStream(tmp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) continue;
                out.write(buf, 0, n);
            }
            out.flush();
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
        } finally {
            if (!moved) Files.deleteIfExists(tmp);
        }
    }

    private static boolean shouldInclude(Path relative, boolean includeCorrupt) {
        if (relative == null) return false;
        String rel = relative.toString().replace('\\', '/');
        if (rel.isBlank()) return false;

        String name = relative.getFileName() != null ? relative.getFileName().toString() : rel;

        if (name.endsWith(".lock") || name.endsWith(".tmp")) return false;
        if (name.contains(".corrupt-")) return includeCorrupt;

        if (rel.equals("users.json")) return true;
        if (rel.equals("server-state.json")) return true;
        return rel.startsWith("games/") && name.endsWith(".json");
    }

    private static boolean shouldRestore(String entryName, boolean includeCorrupt) {
        String name = entryName.replace('\\', '/');
        if (name.startsWith("/")) return false;
        if (name.contains("..")) return false;

        String base = name.substring(name.lastIndexOf('/') + 1);
        if (base.endsWith(".lock") || base.endsWith(".tmp")) return false;
        if (base.contains(".corrupt-")) return includeCorrupt;

        if (name.equals("users.json")) return true;
        if (name.equals("server-state.json")) return true;
        return name.startsWith("games/") && base.endsWith(".json");
    }

    private static boolean isSafeEntry(String name) {
        if (name == null) return false;
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.startsWith("\\")) return false;
        return !n.contains("..");
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
