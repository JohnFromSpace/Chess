package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.fs.repository.GameRepository;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MoveServiceFlowTest {

    @Test
    public void registerAndMakeMovePersists() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        try (MoveService service = new MoveService(repo, new ClockService(), g -> {})) {
            Game game = new Game();
            game.setId("g1");

            service.registerGame(game, "white", "black", null, null, true);

            User white = new User();
            white.setUsername("white");

            int beforeSaves = repo.saveCount.get();
            service.makeMove("g1", white, "e2e4");
            int afterSaves = repo.saveCount.get();

            assertTrue(afterSaves > beforeSaves);
            Game stored = repo.findGameById("g1").orElse(null);
            assertNotNull(stored);
            assertTrue(stored.getMoves().contains("e2e4"));
            assertFalse(stored.isWhiteMove());
            assertEquals(Result.ONGOING, stored.getResult());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectMoveOutOfTurn() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        try (MoveService service = new MoveService(repo, new ClockService(), g -> {})) {
            Game game = new Game();
            game.setId("g1");

            service.registerGame(game, "white", "black", null, null, true);

            User black = new User();
            black.setUsername("black");

            service.makeMove("g1", black, "e7e5");
        }
    }

    @Test
    public void timeoutEndsGame() throws Exception {
        InMemoryRepo repo = new InMemoryRepo();
        try (MoveService service = new MoveService(repo, new ClockService(), g -> {})) {
            service.recoverOngoingGames(List.of(), System.currentTimeMillis());

            Game game = new Game();
            game.setId("g1");
            game.setWhiteTimeMs(1L);
            game.setBlackTimeMs(5_000L);
            game.setWhiteMove(true);

            service.registerGame(game, "white", "black", null, null, true);

            long deadline = System.currentTimeMillis() + 2_000L;
            while (game.getResult() == Result.ONGOING && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L);
            }

            assertEquals(Result.BLACK_WIN, game.getResult());
            assertTrue(game.getWhiteTimeMs() <= 0L);
        }
    }

    private static final class InMemoryRepo implements GameRepository {
        private final Map<String, Game> games = new ConcurrentHashMap<>();
        private final AtomicInteger saveCount = new AtomicInteger();

        @Override
        public void saveGame(Game game) throws IOException {
            if (game == null || game.getId() == null) throw new IOException("Missing game id.");
            games.put(game.getId(), game);
            saveCount.incrementAndGet();
        }

        @Override
        public Optional<Game> findGameById(String id) {
            return Optional.ofNullable(games.get(id));
        }

        @Override
        public Map<String, Game> findGamesForUser(String username) {
            Map<String, Game> out = new ConcurrentHashMap<>();
            for (Game g : games.values()) {
                if (username == null) continue;
                if (username.equals(g.getWhiteUser()) || username.equals(g.getBlackUser())) {
                    out.put(g.getId(), g);
                }
            }
            return out;
        }

        @Override
        public List<Game> loadAllGames() {
            return List.copyOf(games.values());
        }
    }
}
