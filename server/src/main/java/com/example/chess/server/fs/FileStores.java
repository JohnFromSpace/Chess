package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.GameRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.example.chess.common.model.Game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FileStores implements GameRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Type USER_MAP_TYPE =
            new TypeToken<Map<String, User>>() {}.getType();

    private final Path root;
    private final Path usersFile;
    private final Path gamesDir;

    public FileStores(Path root) {
        this.root = root;
        this.usersFile = root.resolve("users.json");
        this.gamesDir = root.resolve("games");

        try {
            Files.createDirectories(root);
            Files.createDirectories(gamesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file store", e);
        }
    }

    public Map<String, User> loadAllUsers() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to ensure data directory: " + root, e);
        }

        if (!Files.exists(usersFile)) {
            return new HashMap<>();
        }

        String json;
        try {
            json = Files.readString(usersFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read users file: " + usersFile, e);
        }

        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }

        try {
            Map<String, User> users = GSON.fromJson(json, USER_MAP_TYPE);
            return users != null ? users : new HashMap<>();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to parse users file: " + usersFile, e);
        }
    }

    public void writeAllUsers(Map<String, User> users) throws IOException {
        Files.createDirectories(root);
        String json = GSON.toJson(users, USER_MAP_TYPE);
        writeAtomically(usersFile, json);
    }

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
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

    @Override
    public Optional<Game> findGameById(String id) {
        Path file = gameFile(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) return Optional.empty();
            Game game = GSON.fromJson(json, Game.class);
            sanitizeReason(game);
            return Optional.ofNullable(game);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read game file: " + file, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to parse game file: " + file, e);
        }
    }

    @Override
    public Map<String, Game> findGamesForUser(String username) {
        Map<String, Game> result = new HashMap<>();
        if (!Files.exists(gamesDir)) {
            return result;
        }

        Set<String> validUsers = new HashSet<>(loadAllUsers().keySet());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    Game game = GSON.fromJson(json, Game.class);

                    if (game == null || game.getId() == null) continue;

                    String w = game.getWhiteUser();
                    String b = game.getBlackUser();

                    if (w == null || b == null || w.isBlank() || b.isBlank()) continue;
                    if (w.equals(b)) continue;

                    if (!validUsers.contains(w) || !validUsers.contains(b)) continue;
                    if (!username.equals(w) && !username.equals(b)) continue;

                    sanitizeReason(game);
                    result.put(game.getId(), game);

                } catch (IOException e) {
                    com.example.chess.server.util.Log.warn("Failed to read game file: " + file, e);
                }
            }
        } catch (IOException e) {
            com.example.chess.server.util.Log.warn("Failed to list games directory: " + gamesDir, e);
        }

        return result;
    }

    @Override
    public void saveGame(Game game) throws IOException {
        if (game == null || game.getId() == null || game.getId().isBlank()) {
            throw new IllegalArgumentException("Game or game.id is null/blank");
        }

        Path file = gameFile(game.getId());

        Files.createDirectories(gamesDir);
        sanitizeReason(game);
        String json = GSON.toJson(game);
        writeAtomically(file, json);
    }

    public java.util.List<Game> loadAllGames() {
        java.util.List<Game> out = new java.util.ArrayList<>();
        try {
            if (!Files.exists(gamesDir)) return out;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
                for (Path file : stream) {
                    try {
                        String json = Files.readString(file, StandardCharsets.UTF_8);
                        Game g = GSON.fromJson(json, Game.class);
                        if (g != null && g.getId() != null && !g.getId().isBlank()) out.add(g);
                    } catch (Exception exception) {
                        com.example.chess.server.util.Log.warn("Failed to read file.", exception);
                    }
                }
            }
        } catch (Exception ex) {
            com.example.chess.server.util.Log.warn("Failed to load all games.", ex);
        }
        return out;
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
                    com.example.chess.server.util.Log.warn("Failed to clean up temp file: " + tmp, e);
                }
            }
        }
    }
}
