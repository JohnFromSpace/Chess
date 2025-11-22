package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.chess.common.GameModels.Game;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
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

    public synchronized User loadUser(String username) {
        List<User> users = loadAllUsers();
        for (User u : users) {
            if (u.username.equals(username)) {
                return u;
            }
        }
        return null;
    }

    public synchronized void saveUser(User user) {
        List<User> users = loadAllUsers();
        boolean replaced = false;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).username.equals(user.username)) {
                users.set(i, user);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            users.add(user);
        }
        writeAllUsers(users);
    }

    private List<User> loadAllUsers() {
        try {
            if (!Files.exists(usersFile)) return new ArrayList<>();
            String json = Files.readString(usersFile);
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
            Files.writeString(usersFile, json);
        } catch (IOException ignored) {
        }
    }

    // ---------------- GAMES ----------------

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

    public synchronized List<Game> findGamesForUser(String username) {
        List<Game> games = loadAllGames();
        List<Game> result = new ArrayList<>();
        for (Game g : games) {
            if (username.equals(g.whiteUser) || username.equals(g.blackUser)) {
                result.add(g);
            }
        }
        return result;
    }

    public synchronized Game findGameById(String gameId) {
        List<Game> games = loadAllGames();
        for (Game g : games) {
            if (gameId.equals(g.id)) {
                return g;
            }
        }
        return null;
    }

    private List<Game> loadAllGames() {
        try {
            if (!Files.exists(usersFile)) return new ArrayList<>();
            String json = Files.readString(usersFile);
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
            Files.writeString(usersFile, json);
        } catch (IOException ignored) {
        }
    }
}