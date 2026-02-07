package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels.User;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

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
        fileStores.updateUsers(all -> all.put(user.getUsername(), user));
    }

    public void updateUsers(Consumer<Map<String, User>> mutator) throws IOException {
        fileStores.updateUsers(mutator);
    }

    public User createUser(String username, String name, String passHash) throws IOException {
        AtomicReference<User> created = new AtomicReference<>();
        fileStores.updateUsers(all -> {
            if (all.containsKey(username)) {
                throw new IllegalArgumentException("Username is already taken.");
            }
            User user = new User();
            user.setUsername(username);
            user.setName(name);
            user.setPassHash(passHash);
            all.put(username, user);
            created.set(user);
        });
        return created.get();
    }
}
