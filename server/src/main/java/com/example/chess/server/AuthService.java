package com.example.chess.server;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.util.Map;
import java.util.Optional;

public class AuthService {

    private final FileStores fileStores;
    private final Map<String, User> users;

    public AuthService(@org.jetbrains.annotations.NotNull FileStores fileStores) {
        this.fileStores = fileStores;
        this.users = fileStores.loadUsers();
    }

    public synchronized User register(String username, String name, String password) {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        User user = new User();
        user.username = username;
        user.name = name;
        user.passHash = PasswordUtil.hash(password);
        users.put(username, user);
        fileStores.saveUsers(users.values());
        return user;
    }

    public synchronized User login(String username, String password) {
        User user = users.get(username);

        if (user == null) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        if (!PasswordUtil.verify(password, user.passHash)) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        return user;
    }

    public synchronized void updateUser(User user) {
        users.put(user.username, user);
        fileStores.saveUsers(users.values());
    }

    public synchronized Optional<User> find(String username) {
        return Optional.ofNullable(users.get(username));
    }
}

