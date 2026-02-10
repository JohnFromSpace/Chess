package com.example.chess.server.core;

import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.core.move.GameEndHook;
import com.example.chess.server.fs.repository.UserRepository;

public final class StatsAndRatingService implements GameEndHook {

    private static final int DEFAULT_RATING = 1200;
    private static final int K = 32;
    private static final int MIN_RATING = 100;

    private final UserRepository users;

    public StatsAndRatingService(UserRepository users) {
        this.users = users;
    }

    @Override
    public void onGameFinished(Game g) throws Exception {
        if (g == null) throw new IllegalArgumentException("There is no game.");
        if (g.getId() == null || g.getId().isBlank()) throw new IllegalArgumentException("This game has no ID.");
        if (g.getWhiteUser() == null || g.getBlackUser() == null) throw new IllegalArgumentException("There is no white/black player.");

        if (g.getResult() == null || g.getResult() == Result.ONGOING) throw new IllegalArgumentException("There is no game result/ game is ongoing.");

        if (!g.isRated() || g.getResult() == Result.ABORTED) return;

        users.updateTwoUsers(g.getWhiteUser(), g.getBlackUser(), (w, b) -> {
            ensureStats(w);
            ensureStats(b);

            UserModels.Stats ws = w.getStats();
            UserModels.Stats bs = b.getStats();

            ws.setPlayed(ws.getPlayed() + 1);
            bs.setPlayed(bs.getPlayed() + 1);

            double sw;
            if (g.getResult() == Result.WHITE_WIN) {
                ws.setWon(ws.getWon() + 1);
                bs.setLost(bs.getLost() + 1);
                sw = 1.0;
            } else if (g.getResult() == Result.BLACK_WIN) {
                bs.setWon(bs.getWon() + 1);
                ws.setLost(ws.getLost() + 1);
                sw = 0.0;
            } else {
                ws.setDrawn(ws.getDrawn() + 1);
                bs.setDrawn(bs.getDrawn() + 1);
                sw = 0.5;
            }

            int rw = ratingOf(w);
            int rb = ratingOf(b);

            double ew = expected(rw, rb);
            double eb = 1.0 - ew;

            int newRw = (int) Math.round(rw + K * (sw - ew));
            int newRb = (int) Math.round(rb + K * ((1.0 - sw) - eb));

            ws.setRating(Math.max(MIN_RATING, newRw));
            bs.setRating(Math.max(MIN_RATING, newRb));
        });
    }

    private static void ensureStats(UserModels.User u) {
        UserModels.Stats st = u.getStats();
        if (st == null) {
            st = new UserModels.Stats();
            u.setStats(st);
        }
        if (st.getRating() <= 0) st.setRating(DEFAULT_RATING);
    }

    private static int ratingOf(UserModels.User u) {
        if (u == null) return DEFAULT_RATING;
        UserModels.Stats st = u.getStats();
        if (st == null || st.getRating() <= 0) return DEFAULT_RATING;
        return st.getRating();
    }

    private static double expected(int ra, int rb) {
        return 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 400.0));
    }
}
