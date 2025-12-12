package com.example.chess.server;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public synchronized User register(String username, String name, String password) {
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        User user = new User();
        user.username = username;
        user.name = name;
        user.passHash = PasswordUtil.hash(password);

        try {
            userRepository.saveUser(user);
        } catch (IOException e) {
            System.err.print("Failed to save user: " + e);
            throw new RuntimeException(e);
        }
        return user;
    }

    public synchronized User login(String username, String password) {
        User currentUser = userRepository.findByUsername(username).
                orElseThrow(() -> new IllegalArgumentException("Invalid credentials."));

        if (!PasswordUtil.hash(password).equals(currentUser.passHash)) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        return currentUser;
    }
}

