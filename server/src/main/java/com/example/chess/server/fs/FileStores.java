package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.example.chess.common.GameModels.Game;

import java.io.IOException;
import java.lang.reflect.Type;
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
        this.usersDir = root.resolve("users.json");
        this.gamesDir = root.resolve("games");

        try {
            Files.createDirectories(gamesDir);
            if (!Files.exists(usersDir)) {
                Files.writeString(usersDir, "[]", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file store", e);
        }
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
                    // логваме, но не спираме целия процес
                    System.err.println("Failed to read user file: " + file + " -> " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list users directory: " + usersDir, e);
        }
        return result;
    }

    private Path userFile(String username) {
        return usersDir.resolve(username + ".json");
    }

    @Override
    public Optional<Game> findById(String id) {
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

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
    }

    public synchronized Map<String, User> loadUsers() {
        try {
            String json = Files.readString(usersDir, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<User>>() {}.getType();
            List<User> users = GSON.fromJson(json, listType);
            if (users == null) users = new ArrayList<>();
            Map<String, User> map = new HashMap<>();
            for (User u : users) {
                map.put(u.username, u);
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load users", e);
        }
    }

    public synchronized void saveUsers(Collection<User> users) {
        try {
            String json = GSON.toJson(users);
            Files.writeString(usersDir, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save users", e);
        }
    }

    public synchronized User loadUser(String username) {
        List<User> users = loadAllUsers();
        for (User u : users) {
            if (u.username.equals(username)) {
                return u;
            }
        }
        return null;
    }

    private List<User> loadAllUsers() {
        try {
            if (!Files.exists(usersDir)) return new ArrayList<>();
            String json = Files.readString(usersDir);
            if (json.isEmpty()) return new ArrayList<>();

            Type type = new TypeToken<List<User>>() {}.getType();
            List<User> list = GSON.fromJson(json, type);
            return (list != null) ? list : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAllUsers(List<User> users) {
        try {
            Files.createDirectories(root);
            String json = GSON.toJson(users);
            Files.writeString(usersDir, json);
        } catch (IOException ignored) {
        }
    }

    @Override
    public Optional<Game> findGameById(String id) {
        return Optional.empty();
    }

    public synchronized void saveGame(Game game) {
        List<Game> games = loadAllGames();
        boolean replaced = false;

        for (int i = 0; i < games.size(); i++) {
            Game g = games.get(i);
            if (g != null && Objects.equals(g.id, game.id)) {
                games.set(i, game);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            games.add(game);
        }
        writeAllGames(games);
    }

    private List<Game> loadAllGames() {
        try {
            if (!Files.exists(usersDir)) return new ArrayList<>();
            String json = Files.readString(usersDir);
            if (json.isEmpty()) return new ArrayList<>();

            Type type = new TypeToken<List<Game>>() {}.getType();
            List<Game> list = GSON.fromJson(json, type);
            return (list != null) ? list : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAllGames(List<Game> games) {
        try {
            Files.createDirectories(root);
            String json = GSON.toJson(games);
            Files.writeString(usersDir, json);
        } catch (IOException ignored) {
        }
    }
}