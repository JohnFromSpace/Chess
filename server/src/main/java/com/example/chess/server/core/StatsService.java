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
        out.sort(Comparator.comparingLong((Game g) -> g.getLastUpdate()).reversed());
        return out;
    }

    public Game getGameForUser(String gameId, String username) {
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("Missing gameId.");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Missing username.");

        Game g = games.findGameById(gameId).orElse(null);
        if (g == null) return null;

        String white = g.getWhiteUser();
        String black = g.getBlackUser();
        if (white == null || black == null) return null;

        if (!username.equals(white) && !username.equals(black)) return null;
        return g;
    }

    public Map<String, Object> toGameDetailsPayload(Game g) {
        Map<String, Object> game = new HashMap<>();
        game.put("id", g.getId());
        game.put("whiteUser", g.getWhiteUser());
        game.put("blackUser", g.getBlackUser());
        game.put("result", g.getResult() == null ? "ONGOING" : g.getResult().name());
        game.put("reason", g.getResultReason());
        game.put("createdAt", g.getCreatedAt());
        game.put("lastUpdate", g.getLastUpdate());
        game.put("whiteTimeMs", g.getWhiteTimeMs());
        game.put("blackTimeMs", g.getBlackTimeMs());
        game.put("whiteToMove", g.isWhiteMove());
        game.put("board", g.getBoard() == null ? "" : g.getBoard().toPrettyString());

        g.ensureMoveHistory();
        List<Map<String, Object>> mh = new ArrayList<>();
        if (g.getMoveHistory() != null) {
            for (var e : g.getMoveHistory()) {
                Map<String, Object> m = new HashMap<>();
                m.put("by", e.getBy());
                m.put("move", e.getMove());
                m.put("atMs", e.getAtMs());
                mh.add(m);
            }
        }
        game.put("moveHistory", mh);

        Map<String, Object> payload = new HashMap<>();
        payload.put("game", game);
        return payload;
    }
}
