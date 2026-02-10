package com.example.chess.server.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DataBackupTool {
    private static final String DEFAULT_DATA_DIR = "data";

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
                Path dataDir = !params.isEmpty() ? Path.of(params.get(0)) : Path.of(DEFAULT_DATA_DIR);
                Path outZip = params.size() > 1 ? Path.of(params.get(1)) : DataBackupService.defaultBackupPath();
                DataBackupService.backup(dataDir, outZip, includeCorrupt);
            }
            case "restore" -> {
                if (params.isEmpty()) {
                    throw new IllegalArgumentException("restore requires <backup.zip>.");
                }
                Path zip = Path.of(params.get(0));
                Path dataDir = params.size() > 1 ? Path.of(params.get(1)) : Path.of(DEFAULT_DATA_DIR);
                boolean force = flags.contains("--force");
                boolean purge = flags.contains("--purge");
                DataBackupService.restore(zip, dataDir, force, purge, includeCorrupt);
            }
            case "help", "-h", "--help" -> usage();
            default -> throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  backup [dataDir] [outZip] [--include-corrupt]");
        System.out.println("  restore <backup.zip> [dataDir] [--force] [--purge] [--include-corrupt]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  backup data backups/chess-data.zip");
        System.out.println("  restore backups/chess-data.zip data --force");
    }
}
