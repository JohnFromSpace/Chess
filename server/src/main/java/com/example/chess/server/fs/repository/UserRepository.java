package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class UserRepository {
    private final FileStores fileStores;

    public UserRepository(FileStores fileStores) {
        this.fileStores = fileStores;
    }

    public Optional<User> findByUsername(String username) {
        Map<String, User> all = fileStores.loadAllUsers();
        return Optional.ofNullable(all.get(username));
    }

    public void saveUser(User user) throws IOException {
        Map<String, User> all = fileStores.loadAllUsers();
        all.put(user.getUsername(), user);
        fileStores.writeAllUsers(all);
    }

    public void updateUsers(Consumer<Map<String, User>> mutator) throws IOException {
        Map<String, User> all = fileStores.loadAllUsers();
        mutator.accept(all);
        fileStores.writeAllUsers(all);
    }
}