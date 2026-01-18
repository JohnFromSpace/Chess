package com.example.chess.server.core;

import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.core.move.GameEndHook;
import com.example.chess.server.fs.repository.UserRepository;

import java.util.Map;

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

        if (!g.isRated() || g.getResult() == Result.ABORTED) throw new IllegalArgumentException("The game is not rated because it was aborted.");

        users.updateUsers(all -> {
            UserModels.User w = mustUser(all, g.getWhiteUser());
            UserModels.User b = mustUser(all, g.getBlackUser());

            ensureStats(w);
            ensureStats(b);

            w.stats.setPlayed(w.stats.getPlayed() + 1);
            b.stats.setPlayed(b.stats.getPlayed() + 1);

            double sw;
            if (g.getResult() == Result.WHITE_WIN) {
                w.stats.setWon(w.stats.getWon() + 1);
                b.stats.setWon(b.stats.getWon() + 1);
                sw = 1.0;
            } else if (g.getResult() == Result.BLACK_WIN) {
                b.stats.setWon(b.stats.getWon() + 1);
                w.stats.setLost(w.stats.getLost() + 1);
                sw = 0.0;
            } else {
                w.stats.setDrawn(w.stats.getDrawn() + 1);
                b.stats.setDrawn(b.stats.getDrawn() + 1);
                sw = 0.5;
            }

            int rw = ratingOf(w);
            int rb = ratingOf(b);

            double ew = expected(rw, rb);
            double eb = 1.0 - ew;

            int newRw = (int) Math.round(rw + K * (sw - ew));
            int newRb = (int) Math.round(rb + K * ((1.0 - sw) - eb));

            w.stats.setRating(Math.max(MIN_RATING, newRw));
            b.stats.setRating(Math.max(MIN_RATING, newRb));
        });
    }

    private static UserModels.User mustUser(Map<String, UserModels.User> all, String username) {
        UserModels.User u = all.get(username);
        if (u == null) throw new IllegalArgumentException("Missing user in store: " + username);
        return u;
    }

    private static void ensureStats(UserModels.User u) {
        if (u.stats == null) u.stats = new UserModels.Stats();
        if (u.stats.getRating() <= 0) u.stats.setRating(DEFAULT_RATING);
    }

    private static int ratingOf(UserModels.User u) {
        if (u == null || u.stats == null || u.stats.getRating() <= 0) return DEFAULT_RATING;
        return u.stats.getRating();
    }

    private static double expected(int ra, int rb) {
        return 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 400.0));
    }
}