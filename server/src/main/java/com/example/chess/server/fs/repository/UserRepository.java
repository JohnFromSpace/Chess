package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class UserRepository {
    private final FileStores fileStores;
    private final Object userLock = new Object();

    public UserRepository(FileStores fileStores) {
        this.fileStores = fileStores;
    }

    public void saveUser(User user) throws IOException {
        synchronized (userLock) {
            Map<String, User> all = fileStores.loadAllUsers();
            all.put(user.username, user);
            fileStores.writeAllUsers(all);
        }
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
}
