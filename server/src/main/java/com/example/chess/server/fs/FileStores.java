package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.GameRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.example.chess.common.model.Game;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FileStores implements GameRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type USER_MAP_TYPE = new TypeToken<Map<String, User>>() {}.getType();

    private final Path root;
    private final Path usersFile;
    private final Path gamesDir;

    public FileStores(Path root) {
        this.root = root;
        this.usersFile = root.resolve("users.json");
        this.gamesDir = root.resolve("games");
    }

    Map<String, User> loadAllUsers() {
        try {
            Files.createDirectories(root);
            if (!Files.exists(usersFile)) return new HashMap<>();

            String json = Files.readString(usersFile, StandardCharsets.UTF_8);
            Map<String, User> users = GSON.fromJson(json, USER_MAP_TYPE);
            return users != null ? users : new HashMap<>();
        } catch (Exception e) {
            System.err.println("Failed to load all users: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    void writeAllUsers(Map<String, User> all) throws IOException {
        Files.createDirectories(root);
        String json = GSON.toJson(all == null ? new HashMap<>() : all, USER_MAP_TYPE);
        Files.writeString(usersFile, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
    }

    private static void sanitizeReason(Game g) {
        if (g == null) return;
        if (g.getResultReason() == null) return;
        String r = g.getResultReason().trim();
        if (r.equalsIgnoreCase("Time.") || r.equalsIgnoreCase("Time")) {
            g.setResultReason("timeout");
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
            Game game = GSON.fromJson(json, Game.class);
            sanitizeReason(game);
            return Optional.ofNullable(game);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read game file: " + file, e);
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
                    System.err.println("Failed to read game file: " + file + " -> " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list games directory: " + gamesDir, e);
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
        String json = GSON.toJson(game);
        Files.writeString(file, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}