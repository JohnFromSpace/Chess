package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class UserRepository {
    private final FileStores fileStores;
    private final Object userLock = new Object();

    public UserRepository(FileStores fileStores) {
        this.fileStores = fileStores;
    }

    public User register(User user) throws IOException {
        synchronized (userLock) {
            Map<String, User> all = fileStores.loadAllUsers();
            if (all.containsKey(user.username)) {
                throw new IllegalArgumentException("Username already exists");
            }
            all.put(user.username, user);
            fileStores.writeAllUsers(all);
            return user;
        }
    }

    public Optional<User> findByUsername(String username) {
        synchronized (userLock) {
            Map<String, User> all = fileStores.loadAllUsers();
            return Optional.ofNullable(all.get(username));
        }
    }

    public void saveUser(User user) throws IOException {
        synchronized (userLock) {
            Map<String, User> all = fileStores.loadAllUsers();
            all.put(user.username, user);
            fileStores.writeAllUsers(all);
        }
    }

    public boolean updateUsersAtomically(String u1, String u2, BiConsumer<User, User> mutator) throws IOException {
        synchronized (userLock) {
            Map<String, User> all = fileStores.loadAllUsers();
            User a = all.get(u1);
            User b = all.get(u2);
            if (a == null || b == null) return false;

            mutator.accept(a, b);

            all.put(u1, a);
            all.put(u2, b);
            fileStores.writeAllUsers(all);
            return true;
        }
    }
}
