package com.example.chess.server.fs;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FileStoresTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void saveGameAndLoad() throws Exception {
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

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("alice");
        game.setBlackUser("bob");

        stores.saveGame(game);

        Optional<Game> loaded = stores.findGameById("g1");
        assertTrue(loaded.isPresent());
        assertEquals("alice", loaded.get().getWhiteUser());
        assertEquals("bob", loaded.get().getBlackUser());

        assertEquals(1, stores.loadAllGames().size());
        assertTrue(stores.findGamesForUser("alice").containsKey("g1"));
    }

    @Test
    public void updateUsersIsSerialized() throws Exception {
        Path root = temp.newFolder("data").toPath();
        FileStores stores = new FileStores(root);

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            new Thread(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    stores.updateUsers(users -> {
                        User u = new User();
                        u.setUsername("u" + idx);
                        users.put(u.getUsername(), u);
                        return null;
                    });
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            fail("Concurrent update failed: " + error.get().getMessage());
        }

        Map<String, User> users = stores.loadAllUsers();
        assertEquals(threads, users.size());
    }

    @Test
    public void quarantinesCorruptGameFile() throws Exception {
        Path root = temp.newFolder("data").toPath();
        FileStores stores = new FileStores(root);

        Path gameFile = root.resolve("games").resolve("g1.json");
        Files.writeString(gameFile, "not-json", StandardCharsets.UTF_8);

        Optional<Game> loaded = stores.findGameById("g1");
        assertFalse(loaded.isPresent());
        assertFalse(Files.exists(gameFile));

        boolean quarantined = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.resolve("games"))) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.startsWith("g1.json.corrupt-")) {
                    quarantined = true;
                    break;
                }
            }
        }
        assertTrue(quarantined);
    }

    @Test
    public void quarantinesCorruptUsersFile() throws Exception {
        Path root = temp.newFolder("data").toPath();
        FileStores stores = new FileStores(root);

        Path usersFile = root.resolve("users.json");
        Files.writeString(usersFile, "not-json", StandardCharsets.UTF_8);

        Map<String, User> users = stores.loadAllUsers();
        assertTrue(users.isEmpty());
        assertFalse(Files.exists(usersFile));

        boolean quarantined = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.startsWith("users.json.corrupt-")) {
                    quarantined = true;
                    break;
                }
            }
        }
        assertTrue(quarantined);
    }
}
