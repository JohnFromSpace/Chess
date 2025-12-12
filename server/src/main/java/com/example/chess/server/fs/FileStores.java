package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.chess.common.GameModels.Game;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class FileStores extends UserRepository implements GameRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type USER_LIST_TYPE = new TypeToken<List<User>>() {}.getType();
    private static final FileStores fileStores = null;

    private final Path root;
    private final Path usersDir;
    private final Path gamesDir;

    public FileStores(Path root) {
        super(fileStores);
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

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        return loadAllUsers().stream()
                .filter(u -> u.username.equals(username))
                .findFirst();
    }

    @Override
    public void saveUser(User user) {
        List<User> users = loadAllUsers();
        boolean updated = false;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).username.equals(user.username)) {
                users.set(i, user);
                updated = true;
                break;
            }
        }
        if (!updated) {
            users.add(user);
        }

        writeAllUsers(users);
    }

    public Map<String, User> loadAllUsers() {
        try {
            Files.createDirectories(root);
            if (!Files.exists(usersDir)) {
                return new ArrayList<>();
            }
            String json = Files.readString(usersDir);
            List<User> users = GSON.fromJson(json, USER_LIST_TYPE);
            return users != null ? users : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Failed to load all users: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void writeAllUsers(Map<String, User> users) throws IOException {
        try {
            Files.createDirectories(root);
            String json = GSON.toJson(users, USER_LIST_TYPE);
            Files.writeString(usersDir, json);
        } catch (IOException e) {
            System.err.println("Error writing all users: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

    }

    private Path gameFile(String id) {
        return gamesDir.resolve(id + ".json");
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
    public Map<String, Game> findGamesForUser(String username) {
        Map<String, Game> result = new HashMap<>();
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
                        result.put(username, game);
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
    public synchronized void saveGame(Game game) throws IOException {
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
            System.err.println("Error writing current game: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

    }

    public Map<String, Game> loadAllGames() throws IOException {
        Map<String, Game> result = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(gamesDir, "*.json")) {
            for (Path p : ds) {
                String json = Files.readString(p);
                Game g = GSON.fromJson(json, Game.class);
                if (g != null && g.id != null) {
                    result.put(g.id, g);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading all games: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        return result;
    }
}