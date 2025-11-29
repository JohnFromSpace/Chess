package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    void saveUser(User user);
    List<User> findAllUsers();
}
