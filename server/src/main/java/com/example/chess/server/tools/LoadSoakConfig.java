package com.example.chess.server.tools;

final class LoadSoakConfig {
    int games = 10;
    int moves = 40;
    int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    long durationMs = 0L;
    String dataDir = null;
    int reconnectEvery = 0;

    static LoadSoakConfig parse(String[] args) {
        LoadSoakConfig cfg = new LoadSoakConfig();
        if (args == null) return cfg;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--games" -> cfg.games = intArg(args, ++i, "games");
                case "--moves" -> cfg.moves = intArg(args, ++i, "moves");
                case "--threads" -> cfg.threads = intArg(args, ++i, "threads");
                case "--durationMs" -> cfg.durationMs = longArg(args, ++i);
                case "--dataDir" -> cfg.dataDir = strArg(args, ++i, "dataDir");
                case "--reconnectEvery" -> cfg.reconnectEvery = intArg(args, ++i, "reconnectEvery");
                case "--help", "-h" -> {
                    usage();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Unknown arg: " + a);
            }
        }
        return cfg;
    }

    private static int intArg(String[] args, int i, String name) {
        String raw = strArg(args, i, name);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad " + name + ": " + raw);
        }
    }

    private static long longArg(String[] args, int i) {
        String raw = strArg(args, i, "durationMs");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad " + "durationMs" + ": " + raw);
        }
    }

    private static String strArg(String[] args, int i, String name) {
        if (args == null || i < 0 || i >= args.length) {
            throw new IllegalArgumentException("Missing " + name + " value.");
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("Usage: LoadSoakTool [--games N] [--moves N] [--threads N] [--durationMs MS] [--dataDir PATH] [--reconnectEvery N]");
        System.out.println("Defaults: games=10 moves=40 threads=min(4,cpu) durationMs=0 (disabled) reconnectEvery=0 (disabled)");
        System.out.println("If --dataDir is not provided, an in-memory repository is used.");
    }
}
