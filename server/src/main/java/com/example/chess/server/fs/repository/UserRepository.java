package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels;
import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public class UserRepository {
    private final FileStores stores;

    public UserRepository(FileStores stores) {
        this.stores = stores;
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        Map<String, User> users = loadUsers();
        return Optional.ofNullable(users.get(username));
    }

    public synchronized User createUser(String username, String name, String passHash) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        return stores.updateUsers(users -> {
            if (users.containsKey(username)) {
                throw new IllegalArgumentException("Username is already taken.");
            }

            User created = new User();
            created.setUsername(username);
            created.setName(name == null ? "" : name);
            created.setPassHash(passHash == null ? "" : passHash);
            ensureStats(created);

            users.put(username, created);
            return created;
        });
    }

    public synchronized void updateTwoUsers(String usernameA,
                                            String usernameB,
                                            BiConsumer<User, User> mutator) throws IOException {
        if (usernameA == null || usernameA.isBlank()) {
            throw new IllegalArgumentException("Missing username.");
        }
        if (usernameB == null || usernameB.isBlank()) {
            throw new IllegalArgumentException("Missing username.");
        }
        if (usernameA.equals(usernameB)) {
            throw new IllegalArgumentException("Usernames must be different.");
        }
        if (mutator == null) throw new IllegalArgumentException("Missing user mutator.");

        stores.updateUsers(users -> {
            User a = users.get(usernameA);
            User b = users.get(usernameB);
            if (a == null || b == null) {
                throw new IllegalArgumentException("Missing user in store.");
            }

            mutator.accept(a, b);
            ensureStats(a);
            ensureStats(b);

            users.put(usernameA, a);
            users.put(usernameB, b);
            return null;
        });
    }

    private Map<String, User> loadUsers() {
        return new TreeMap<>(stores.loadAllUsers());
    }

    private static void ensureStats(User user) {
        if (user.stats == null) user.stats = new UserModels.Stats();
        if (user.stats.getRating() <= 0) user.stats.setRating(1200);
    }
}
