package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepository {
    private final FileStores fileStores;
    private final Map<String, Object> userLocks = new ConcurrentHashMap<>();

    public UserRepository(FileStores fileStores) {
        this.fileStores = fileStores;
    }

    private Object lockFor(String username) {
        return userLocks.computeIfAbsent(username, u -> new Object());
    }

    public Optional<User> findByUsername(String username) {
        synchronized (lockFor(username)) {
            return fileStores.findByUsername(username);
        }
    }

    public void saveUser(User user) {
        synchronized (lockFor(user.username)) {
            fileStores.saveUser(user);
        }
    }

    public List<User> findAllUsers() {
        synchronized (fileStores) {
            return fileStores.loadAllUsers();
        }
    }
}
