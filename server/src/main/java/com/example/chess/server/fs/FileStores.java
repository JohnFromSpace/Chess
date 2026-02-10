package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.fs.repository.GameRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class FileStores implements GameRepository {

    private final UserFileStore userStore;
    private final GameFileStore gameStore;

    public FileStores(Path root) {
        if (root == null) throw new IllegalArgumentException("Missing data directory.");
        this.userStore = new UserFileStore(root);
        this.gameStore = new GameFileStore(root.resolve("games"), () -> userStore.loadAllUsers().keySet());
    }

    public Map<String, User> loadAllUsers() {
        return userStore.loadAllUsers();
    }

    public <T> T updateUsers(Function<Map<String, User>, T> updater) throws IOException {
        return userStore.updateUsers(updater);
    }

    @Override
    public Optional<Game> findGameById(String id) {
        return gameStore.findGameById(id);
    }

    @Override
    public Map<String, Game> findGamesForUser(String username) {
        return gameStore.findGamesForUser(username);
    }

    @Override
    public void saveGame(Game game) throws IOException {
        gameStore.saveGame(game);
    }

    @Override
    public List<Game> loadAllGames() {
        return gameStore.loadAllGames();
    }

}
