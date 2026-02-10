package com.example.chess.server.fs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ServerStateStoreTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void quarantinesCorruptServerState() throws Exception {
        Path root = temp.newFolder("data").toPath();
        ServerStateStore store = new ServerStateStore(root);

        Path stateFile = root.resolve("server-state.json");
        Files.writeString(stateFile, "not-json", StandardCharsets.UTF_8);

        assertNull(store.read());
        assertFalse(Files.exists(stateFile));

        boolean quarantined = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.startsWith("server-state.json.corrupt-")) {
                    quarantined = true;
                    break;
                }
            }
        }
        assertTrue(quarantined);
    }
}
