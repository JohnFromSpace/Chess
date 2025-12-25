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
        if (g == null) return;
        if (g.id == null || g.id.isBlank()) return;
        if (g.whiteUser == null || g.blackUser == null) return;

        // Only update for finished games
        if (g.result == null || g.result == Result.ONGOING) return;

        // Atomic update of BOTH users in one locked write
        users.updateUsers(all -> {
            UserModels.User w = mustUser(all, g.whiteUser);
            UserModels.User b = mustUser(all, g.blackUser);

            ensureStats(w);
            ensureStats(b);

            // Played/W/L/D
            w.stats.played++;
            b.stats.played++;

            double sw; // score for white
            if (g.result == Result.WHITE_WIN) {
                w.stats.won++;
                b.stats.lost++;
                sw = 1.0;
            } else if (g.result == Result.BLACK_WIN) {
                b.stats.won++;
                w.stats.lost++;
                sw = 0.0;
            } else { // DRAW
                w.stats.drawn++;
                b.stats.drawn++;
                sw = 0.5;
            }

            // ELO
            int rw = ratingOf(w);
            int rb = ratingOf(b);

            double ew = expected(rw, rb);
            double eb = 1.0 - ew;

            int newRw = (int) Math.round(rw + K * (sw - ew));
            int newRb = (int) Math.round(rb + K * ((1.0 - sw) - eb));

            w.stats.rating = Math.max(MIN_RATING, newRw);
            b.stats.rating = Math.max(MIN_RATING, newRb);
        });
    }

    private static UserModels.User mustUser(Map<String, UserModels.User> all, String username) {
        UserModels.User u = all.get(username);
        if (u == null) throw new IllegalArgumentException("Missing user in store: " + username);
        return u;
    }

    private static void ensureStats(UserModels.User u) {
        if (u.stats == null) u.stats = new UserModels.Stats();
        if (u.stats.rating <= 0) u.stats.rating = DEFAULT_RATING;
    }

    private static int ratingOf(UserModels.User u) {
        if (u == null || u.stats == null || u.stats.rating <= 0) return DEFAULT_RATING;
        return u.stats.rating;
    }

    private static double expected(int ra, int rb) {
        return 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 400.0));
    }
}