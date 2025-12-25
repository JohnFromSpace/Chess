package com.example.chess.server.core;

import com.example.chess.common.model.Game;
import com.example.chess.server.fs.repository.GameRepository;

import java.util.*;

public class StatsService {
    private final GameRepository games;

    public StatsService(GameRepository games) {
        this.games = games;
    }

    public List<Game> listGamesForUser(String username) {
        Map<String, Game> m = games.findGamesForUser(username);
        List<Game> out = new ArrayList<>(m.values());
        out.sort(Comparator.comparingLong((Game g) -> g.lastUpdate).reversed());
        return out;
    }

    public Game getGameForUser(String gameId, String username) {
        Game g = games.findGameById(gameId).orElse(null);
        if (g == null) return null;
        if (!username.equals(g.whiteUser) && !username.equals(g.blackUser)) return null;
        return g;
    }
}