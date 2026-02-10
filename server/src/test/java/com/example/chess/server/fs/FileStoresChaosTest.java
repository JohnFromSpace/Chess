package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

public class FileStoresChaosTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void handlesIntermittentCorruption() throws Exception {
        Path root = temp.newFolder("data").toPath();
        FileStores stores = new FileStores(root);

        stores.updateUsers(users -> {
            User white = new User();
            white.setUsername("alice");
            users.put("alice", white);
            User black = new User();
            black.setUsername("bob");
            users.put("bob", black);
            return null;
        });

        Random rnd = new Random(42L);
        for (int i = 0; i < 20; i++) {
            String id = "g" + i;
            Game game = new Game();
            game.setId(id);
            game.setWhiteUser("alice");
            game.setBlackUser("bob");

            stores.saveGame(game);

            Path gameFile = root.resolve("games").resolve(id + ".json");
            assertTrue(Files.exists(gameFile));

            if (rnd.nextBoolean()) {
                String json = Files.readString(gameFile, StandardCharsets.UTF_8);
                int cut = Math.max(1, json.length() / 2);
                Files.writeString(gameFile, json.substring(0, cut), StandardCharsets.UTF_8);

                Optional<Game> loaded = stores.findGameById(id);
                assertTrue(loaded.isEmpty());
                assertFalse(Files.exists(gameFile));
            } else {
                Optional<Game> loaded = stores.findGameById(id);
                assertTrue(loaded.isPresent());
            }
        }
    }
}
