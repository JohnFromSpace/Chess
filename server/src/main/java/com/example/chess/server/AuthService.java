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
        user.setUsername(username);
        user.setName(name);
        user.setPassHash(PasswordUtil.hash(password));

        try {
            userRepository.saveUser(user);
        } catch (IOException e) {
            com.example.chess.server.util.Log.warn("Failed to save user.", e);
        }
        return user;
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

