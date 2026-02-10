package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.core.ClockService;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class DrawFlowFailureTest {

    @Test
    public void offerDrawRollsBackWhenSaveFails() throws Exception {
        GameStore store = new FailingStore();
        GameFinisher finisher = new GameFinisher(store, new ClockService(), new ActiveGames(), null);
        DrawFlow flow = new DrawFlow(store, finisher);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");
        game.setLastUpdate(123L);

        GameContext ctx = new GameContext(game, null, null);
        User white = new User();
        white.setUsername("white");

        boolean threw = false;
        try {
            synchronized (ctx) {
                flow.offerDrawLocked(ctx, white);
            }
        } catch (IOException e) {
            threw = true;
        }
        assertTrue("Expected IOException", threw);

        assertNull(game.getDrawOfferedBy());
        assertEquals(123L, game.getLastUpdate());
    }

    @Test
    public void declineDrawRollsBackWhenSaveFails() throws Exception {
        GameStore store = new FailingStore();
        GameFinisher finisher = new GameFinisher(store, new ClockService(), new ActiveGames(), null);
        DrawFlow flow = new DrawFlow(store, finisher);

        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");
        game.setDrawOfferedBy("white");
        game.setLastUpdate(55L);

        GameContext ctx = new GameContext(game, null, null);
        User black = new User();
        black.setUsername("black");

        boolean threw = false;
        try {
            synchronized (ctx) {
                flow.respondDrawLocked(ctx, black, false);
            }
        } catch (IOException e) {
            threw = true;
        }
        assertTrue("Expected IOException", threw);

        assertEquals("white", game.getDrawOfferedBy());
        assertEquals(55L, game.getLastUpdate());
    }

    private static final class FailingStore implements GameStore {
        @Override
        public void save(Game g) throws IOException {
            throw new IOException("Disk full");
        }
    }
}
