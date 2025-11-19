package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.chess.common.GameModels.Game;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FileStores {

    private static final Gson GSON = new Gson();

    private final Path root;
    private final Path usersFile;
    private final Path gamesDir;

    public FileStores(Path root) {
        this.root = root;
        this.usersFile = root.resolve("users.json");
        this.gamesDir = root.resolve("games");

        try {
            Files.createDirectories(gamesDir);
            if (!Files.exists(usersFile)) {
                Files.writeString(usersFile, "[]", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file store", e);
        }
    }

    public synchronized Map<String, User> loadUsers() {
        try {
            String json = Files.readString(usersFile, StandardCharsets.UTF_8);
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
            Files.writeString(usersFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save users", e);
        }
    }

    public synchronized Optional<Game> loadGame(String id) {
        Path p = gamesDir.resolve(id + ".json");
        if (!Files.exists(p)) return Optional.empty();
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return Optional.ofNullable(GSON.fromJson(json, Game.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public synchronized void saveGame(Game game) {
        Path p = gamesDir.resolve(game.id + ".json");
        try {
            String json = GSON.toJson(game);
            Files.writeString(p, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save game " + game.id, e);
        }
    }

    public synchronized List<Game> listGamesFor(String username) {
        List<Game> result = new ArrayList<>();
        try (var stream = Files.list(gamesDir)) {
            for (Path p : stream.toList()) {
                String json = Files.readString(p, StandardCharsets.UTF_8);
                Game g = GSON.fromJson(json, Game.class);
                if (g == null) continue;
                if (username.equals(g.whiteUser) || username.equals(g.blackUser)) {
                    result.add(g);
                }
            }
        } catch (IOException e) {
            // ignore, just return what we have
        }
        result.sort(Comparator.comparingLong(a -> -a.createdAt));
        return result;
    }
}

