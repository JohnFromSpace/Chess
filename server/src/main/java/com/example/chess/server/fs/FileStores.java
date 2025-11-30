package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.chess.common.GameModels.Game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class FileStores implements UserRepository, GameRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root;
    private final Path usersDir;
    private final Path gamesDir;

    public FileStores(Path root) {
        this.root = root;
        this.usersDir = root.resolve("users");
        this.gamesDir = root.resolve("games");

        try {
            Files.createDirectories(usersDir);
            Files.createDirectories(gamesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file store", e);
        }
    }

    private Path userFile(String username) {
        return usersDir.resolve(username + ".json");
    }

    @Override
    public Optional<User> findByUsername(String username) {
        Path file = userFile(username);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            User user = GSON.fromJson(json, User.class);
            return Optional.ofNullable(user);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read user file: " + file, e);
        }
    }

    @Override
    public void saveUser(User user) {
        if (user == null || user.username == null || user.username.isBlank()) {
            throw new IllegalArgumentException("User or username is null/blank");
        }
        Path file = userFile(user.username);
        try {
            Files.createDirectories(usersDir);
            String json = GSON.toJson(user);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write user file: " + file, e);
        }
    }

    @Override
    public List<User> findAllUsers() {
        List<User> result = new ArrayList<>();
        if (!Files.exists(usersDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(usersDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    User user = GSON.fromJson(json, User.class);
                    if (user != null) {
                        result.add(user);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read user file: " + file + " -> " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list users directory: " + usersDir, e);
        }
        return result;
    }

    // ───────────────────────────── games ─────────────────────────────

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
    }

    /** legacy–style findById if your interface still has it; delegate to findGameById */
    @Override
    public Optional<Game> findById(String id) {
        return findGameById(id);
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
            return Optional.ofNullable(game);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read game file: " + file, e);
        }
    }

    @Override
    public List<Game> findGamesForUser(String username) {
        List<Game> result = new ArrayList<>();
        if (!Files.exists(gamesDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    Game game = GSON.fromJson(json, Game.class);
                    if (game != null &&
                            (username.equals(game.whiteUser) || username.equals(game.blackUser))) {
                        result.add(game);
                    }
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
    public List<Game> findAllGames() {
        List<Game> result = new ArrayList<>();
        if (!Files.exists(gamesDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    Game game = GSON.fromJson(json, Game.class);
                    if (game != null) {
                        result.add(game);
                    }
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
    public synchronized void saveGame(Game game) {
        if (game == null || game.id == null || game.id.isBlank()) {
            throw new IllegalArgumentException("Game or game.id is null/blank");
        }
        Path file = gameFile(game.id);
        try {
            Files.createDirectories(gamesDir);
            String json = GSON.toJson(game);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write game file: " + file, e);
        }
    }
}