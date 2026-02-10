package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.core.ClockService;
import org.junit.Test;

public class DrawFlowValidationTest {

    @Test(expected = IllegalArgumentException.class)
    public void respondDrawRejectsMissingOffer() throws Exception {
        DrawFlow flow = new DrawFlow(new NoopStore(), newFinisher());
        GameContext ctx = newGame();

        User black = new User();
        black.setUsername("black");

        synchronized (ctx) {
            flow.respondDrawLocked(ctx, black, false);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void respondDrawRejectsSelfOffer() throws Exception {
        DrawFlow flow = new DrawFlow(new NoopStore(), newFinisher());
        GameContext ctx = newGame();
        ctx.getGame().setDrawOfferedBy("white");

        User white = new User();
        white.setUsername("white");

        synchronized (ctx) {
            flow.respondDrawLocked(ctx, white, true);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void offerDrawRejectsNonParticipant() throws Exception {
        DrawFlow flow = new DrawFlow(new NoopStore(), newFinisher());
        GameContext ctx = newGame();

        User intruder = new User();
        intruder.setUsername("intruder");

        synchronized (ctx) {
            flow.offerDrawLocked(ctx, intruder);
        }
    }

    private static GameContext newGame() {
        Game game = new Game();
        game.setId("g1");
        game.setWhiteUser("white");
        game.setBlackUser("black");
        return new GameContext(game, null, null);
    }

    private static GameFinisher newFinisher() {
        return new GameFinisher(new NoopStore(), new ClockService(), new ActiveGames(), null);
    }

    private static final class NoopStore implements GameStore {
        @Override
        public void save(Game g) {
        }
    }
}
