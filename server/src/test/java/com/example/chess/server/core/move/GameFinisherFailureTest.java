package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.ClockService;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class GameFinisherFailureTest {

    @Test
    public void gameOverFlagsPersistFailure() {
        GameStore store = new FailingStore();
        ActiveGames games = new ActiveGames();
        GameFinisher finisher = new GameFinisher(store, new ClockService(), games, null);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");

        TestHandler handler = new TestHandler();
        GameContext ctx = new GameContext(game, handler, null);
        games.put(ctx);

        Runnable notify;
        synchronized (ctx) {
            notify = finisher.finishLocked(ctx, Result.WHITE_WIN, "Checkmate.");
        }
        assertNotNull(notify);
        notify.run();

        assertEquals(1, handler.gameOverCount.get());
        assertFalse(handler.persistOk.get());
    }

    private static final class FailingStore implements GameStore {
        @Override
        public void save(Game g) throws IOException {
            throw new IOException("Disk full");
        }
    }

    private static final class TestHandler extends ClientHandler {
        private final AtomicInteger gameOverCount = new AtomicInteger();
        private final AtomicBoolean persistOk = new AtomicBoolean(true);

        private TestHandler() {
            super(null, null, null, null, null);
        }

        @Override
        public void pushGameOver(Game g, boolean statsOk, boolean persistOk) {
            gameOverCount.incrementAndGet();
            this.persistOk.set(persistOk);
        }

        @Override
        public void sendInfo(String message) {
        }
    }
}
