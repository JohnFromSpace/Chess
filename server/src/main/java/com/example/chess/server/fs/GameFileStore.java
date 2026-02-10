package com.example.chess.server.fs;

import com.example.chess.common.model.Game;
import com.example.chess.server.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

final class GameFileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path gamesDir;
    private final Supplier<Set<String>> validUsersSupplier;
    private final ConcurrentMap<String, Object> gameMutexes = new ConcurrentHashMap<>();

    GameFileStore(Path gamesDir, Supplier<Set<String>> validUsersSupplier) {
        this.gamesDir = gamesDir;
        this.validUsersSupplier = validUsersSupplier;
        try {
            Files.createDirectories(gamesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize games store", e);
        }
    }

    Optional<Game> findGameById(String id) {
        Path file = gameFile(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Game game = readGameFile(file);
        if (game == null) return Optional.empty();
        sanitizeReason(game);
        return Optional.of(game);
    }

    Map<String, Game> findGamesForUser(String username) {
        Map<String, Game> result = new HashMap<>();
        if (!Files.exists(gamesDir)) {
            return result;
        }

        Set<String> validUsers = safeValidUsers();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
            for (Path file : stream) {
                Game game = readGameFile(file);
                if (game == null || game.getId() == null) continue;

                String w = game.getWhiteUser();
                String b = game.getBlackUser();

                if (w == null || b == null || w.isBlank() || b.isBlank()) continue;
                if (w.equals(b)) continue;

                if (!validUsers.contains(w) || !validUsers.contains(b)) continue;
                if (!username.equals(w) && !username.equals(b)) continue;

                sanitizeReason(game);
                result.put(game.getId(), game);
            }
        } catch (IOException e) {
            Log.warn("Failed to list games directory: " + gamesDir, e);
        }

        return result;
    }

    void saveGame(Game game) throws IOException {
        if (game == null || game.getId() == null || game.getId().isBlank()) {
            throw new IllegalArgumentException("Game or game.id is null/blank");
        }

        Path file = gameFile(game.getId());

        Files.createDirectories(gamesDir);
        sanitizeReason(game);
        String json = GSON.toJson(game);
        try {
            withGameLock(file, () -> {
                try {
                    FileStoreIO.writeAtomically(file, json);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    List<Game> loadAllGames() {
        List<Game> out = new ArrayList<>();
        try {
            if (!Files.exists(gamesDir)) return out;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
                for (Path file : stream) {
                    Game g = readGameFile(file);
                    if (g != null && g.getId() != null && !g.getId().isBlank()) out.add(g);
                }
            }
        } catch (IOException | DirectoryIteratorException ex) {
            Log.warn("Failed to load all games.", ex);
        }
        return out;
    }

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
    }

    private Path gameLockFile(Path gameFile) {
        return gameFile.resolveSibling(gameFile.getFileName().toString() + ".lock");
    }

    private static void sanitizeReason(Game game) {
        if (game == null) return; // avoid throwing exceptions for an unstarted game
        String r = game.getResultReason();
        if (r == null) return; // allow ongoing games
        r = r.trim();
        if (r.equalsIgnoreCase("Time.") || r.equalsIgnoreCase("Time")) {
            game.setResultReason("timeout");
        }
    }

    private Game readGameFile(Path file) {
        if (!Files.exists(file)) return null;
        try {
            return withGameLock(file, () -> {
                String json;
                try {
                    json = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    Log.warn("Failed to read game file: " + file, e);
                    return null;
                }

                if (json.isBlank()) {
                    FileStoreIO.quarantineFile(file, "game");
                    return null;
                }

                try {
                    Game game = GSON.fromJson(json, Game.class);
                    if (game == null) {
                        FileStoreIO.quarantineFile(file, "game");
                    }
                    return game;
                } catch (RuntimeException e) {
                    FileStoreIO.quarantineFile(file, "game");
                    Log.warn("Failed to parse game file: " + file, e);
                    return null;
                }
            });
        } catch (UncheckedIOException e) {
            Log.warn("Failed to lock game file: " + file, e);
            return null;
        }
    }

    private <T> T withGameLock(Path gameFile, Supplier<T> action) {
        Object mutex = gameMutexes.computeIfAbsent(gameFile.getFileName().toString(), k -> new Object());
        synchronized (mutex) {
            try {
                Files.createDirectories(gamesDir);
                Path lockFile = gameLockFile(gameFile);
                try (FileChannel channel = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                     FileLock lock = channel.lock()) {
                    if (!lock.isValid()) {
                        throw new IOException("Failed to acquire game lock: " + lockFile);
                    }
                    return action.get();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to lock game file: " + gameFile, e);
            }
        }
    }

    private Set<String> safeValidUsers() {
        try {
            if (validUsersSupplier == null) return Set.of();
            return new HashSet<>(validUsersSupplier.get());
        } catch (Exception e) {
            Log.warn("Failed to load valid users list.", e);
            return Set.of();
        }
    }
}
