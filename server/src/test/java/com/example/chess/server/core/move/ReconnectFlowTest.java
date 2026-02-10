package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.core.ReconnectService;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ReconnectFlowTest {

    @Test
    public void disconnectWithNoMovesAbortsGame() throws Exception {
        ActiveGames games = new ActiveGames();
        InMemoryStore store = new InMemoryStore();
        CountDownLatch finished = new CountDownLatch(1);

        GameFinisher finisher = new GameFinisher(store, new ClockService(), games, g -> finished.countDown());
        ReconnectFlow flow = new ReconnectFlow(games, new ReconnectService(10L), finisher, store);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");

        GameContext ctx = new GameContext(game, null, null);
        games.put(ctx);

        User u = new User();
        u.setUsername("white");

        flow.onDisconnect(u);

        assertTrue("game should finish", finished.await(1, TimeUnit.SECONDS));
        assertEquals(Result.ABORTED, game.getResult());
        assertEquals("Aborted (no moves).", game.getResultReason());
        long deadline = System.currentTimeMillis() + 200L;
        while (games.size() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(0, games.size());
    }

    @Test
    public void reconnectBeforeGraceKeepsGameOngoing() throws Exception {
        ActiveGames games = new ActiveGames();
        InMemoryStore store = new InMemoryStore();
        CountDownLatch finished = new CountDownLatch(1);

        GameFinisher finisher = new GameFinisher(store, new ClockService(), games, g -> finished.countDown());
        ReconnectFlow flow = new ReconnectFlow(games, new ReconnectService(200L), finisher, store);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");

        GameContext ctx = new GameContext(game, null, null);
        games.put(ctx);

        User u = new User();
        u.setUsername("white");

        flow.onDisconnect(u);

        TestClientHandler newHandler = new TestClientHandler();
        flow.tryReconnect(u, newHandler);

        assertEquals(1, newHandler.startedCount.get());
        assertEquals(0, newHandler.gameOverCount.get());
        assertEquals(0L, game.getWhiteOfflineSince());
        assertSame(newHandler, ctx.getWhiteHandler());

        assertFalse("game should not finish", finished.await(400, TimeUnit.MILLISECONDS));
        assertEquals(Result.ONGOING, game.getResult());
    }

    @Test
    public void reconnectAfterGameFinishedPushesGameOver() {
        ActiveGames games = new ActiveGames();
        InMemoryStore store = new InMemoryStore();

        GameFinisher finisher = new GameFinisher(store, new ClockService(), games, null);
        ReconnectFlow flow = new ReconnectFlow(games, new ReconnectService(200L), finisher, store);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");
        game.setResult(Result.WHITE_WIN);

        GameContext ctx = new GameContext(game, null, null);
        games.put(ctx);

        User u = new User();
        u.setUsername("white");

        TestClientHandler newHandler = new TestClientHandler();
        flow.tryReconnect(u, newHandler);

        assertEquals(0, newHandler.startedCount.get());
        assertEquals(1, newHandler.gameOverCount.get());
    }

    private static final class InMemoryStore implements GameStore {
        @Override
        public void save(Game g) {
        }
    }

    private static final class TestClientHandler extends ClientHandler {
        private final AtomicInteger startedCount = new AtomicInteger();
        private final AtomicInteger gameOverCount = new AtomicInteger();

        private TestClientHandler() {
            super(null, null, null, null, null);
        }

        @Override
        public void pushGameStarted(Game g, boolean isWhite) {
            startedCount.incrementAndGet();
        }

        @Override
        public void pushGameOver(Game g, boolean statsOk, boolean persistOk) {
            gameOverCount.incrementAndGet();
        }

        @Override
        public void sendInfo(String message) {
        }
    }
}
