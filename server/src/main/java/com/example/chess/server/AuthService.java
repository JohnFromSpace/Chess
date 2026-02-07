package com.example.chess.server;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.repository.UserRepository;

import java.io.IOException;

public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public synchronized User register(String username, String name, String password) {
        try {
            return userRepository.createUser(username, name, PasswordUtil.hash(password));
        } catch (IOException e) {
            com.example.chess.server.util.Log.warn("Failed to save user.", e);
            throw new RuntimeException("Failed to save user.", e);
        }
    }

    public synchronized User login(String username, String password) {
        User currentUser = userRepository.findByUsername(username).
                orElseThrow(() -> new IllegalArgumentException("Invalid credentials."));

        if (!PasswordUtil.verify(password, currentUser.getPassHash())) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        return currentUser;
    }

    public synchronized User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user."));
    }
}

