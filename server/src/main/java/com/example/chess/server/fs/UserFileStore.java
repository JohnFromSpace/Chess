package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

final class UserFileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type USER_MAP_TYPE =
            new TypeToken<Map<String, User>>() {}.getType();

    private final Path root;
    private final Path usersFile;
    private final Path usersLockFile;
    private final Object usersMutex = new Object();

    UserFileStore(Path root) {
        this.root = root;
        this.usersFile = root.resolve("users.json");
        this.usersLockFile = root.resolve("users.json.lock");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize user store", e);
        }
    }

    Map<String, User> loadAllUsers() {
        return withUserLock(this::readUsersUnlocked);
    }

    <T> T updateUsers(Function<Map<String, User>, T> updater) throws IOException {
        if (updater == null) throw new IllegalArgumentException("Missing users updater.");
        try {
            return withUserLock(() -> {
                Map<String, User> users = new TreeMap<>(readUsersUnlocked());
                T result = updater.apply(users);
                writeUsersUnlocked(users);
                return result;
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Map<String, User> readUsersUnlocked() {
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

        if (json.isBlank()) {
            FileStoreIO.quarantineFile(usersFile, "users");
            return new HashMap<>();
        }

        try {
            Map<String, User> users = GSON.fromJson(json, USER_MAP_TYPE);
            if (users == null) {
                FileStoreIO.quarantineFile(usersFile, "users");
                return new HashMap<>();
            }
            return users;
        } catch (RuntimeException e) {
            FileStoreIO.quarantineFile(usersFile, "users");
            com.example.chess.server.util.Log.warn("Failed to parse users file: " + usersFile, e);
            return new HashMap<>();
        }
    }

    private void writeUsersUnlocked(Map<String, User> users) {
        try {
            Files.createDirectories(root);
            String json = GSON.toJson(users, USER_MAP_TYPE);
            FileStoreIO.writeAtomically(usersFile, json);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write users file: " + usersFile, e);
        }
    }

    private <T> T withUserLock(Supplier<T> action) {
        synchronized (usersMutex) {
            try {
                Files.createDirectories(root);
                try (FileChannel channel = FileChannel.open(usersLockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                     FileLock lock = channel.lock()) {
                    if (!lock.isValid()) {
                        throw new IOException("Failed to acquire users file lock: " + usersLockFile);
                    }
                    return action.get();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to lock users file: " + usersLockFile, e);
            }
        }
    }
}
