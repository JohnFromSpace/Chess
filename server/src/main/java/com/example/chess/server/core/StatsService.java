package com.example.chess.server.core;

import com.example.chess.common.model.Game;
import com.example.chess.server.fs.repository.GameRepository;

import java.io.IOException;
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

    public Map<String, Object> toGameDetailsPayload(Game g) {
        g.ensureMoveHistory();

        Map<String, Object> gm = new HashMap<>();
        gm.put("id", g.id);
        gm.put("whiteUser", g.whiteUser);
        gm.put("blackUser", g.blackUser);
        gm.put("result", String.valueOf(g.result));
        gm.put("reason", g.resultReason);
        gm.put("createdAt", g.createdAt);
        gm.put("lastUpdate", g.lastUpdate);
        gm.put("board", g.board == null ? null : g.board.toPrettyString());

        List<Map<String, Object>> mh = new ArrayList<>();
        for (Game.MoveEntry e : (g.moveHistory == null ? List.<Game.MoveEntry>of() : g.moveHistory)) {
            Map<String, Object> x = new HashMap<>();
            x.put("by", e.by);
            x.put("move", e.move);
            x.put("atMs", e.atMs);
            mh.add(x);
        }
        gm.put("moveHistory", mh);

        Map<String, Object> payload = new HashMap<>();
        payload.put("game", gm);
        return payload;
    }
}