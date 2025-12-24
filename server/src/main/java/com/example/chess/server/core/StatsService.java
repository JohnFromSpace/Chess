package com.example.chess.server.core;

import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;
import com.example.chess.server.logic.RulesEngine;
import com.example.chess.common.board.Move;
import com.example.chess.common.board.Board;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class StatsService {

    private final UserRepository users;
    private final GameRepository games;

    public StatsService(UserRepository users, GameRepository games) {
        this.users = users;
        this.games = games;
    }

    public void saveSnapshot(Game g) throws IOException {
        games.saveGame(g);
    }

    public boolean finishGame(Game g) throws IOException {
        games.saveGame(g);
        return updateStatsAndElo(g);
    }

    public List<Game> listGames(String username) {
        Map<String, Game> m = games.findGamesForUser(username);
        return m.values().stream()
                .sorted(Comparator.comparingLong(x -> -x.createdAt))
                .collect(Collectors.toList());
    }

    public Game getGame(String id, String username) {
        Game g = games.findGameById(id).orElse(null);
        if (g == null) return null;
        if (!username.equals(g.whiteUser) && !username.equals(g.blackUser)) return null;
        return g;
    }

    public List<Map<String, Object>> buildTimeline(Game savedGame) {
        if (savedGame == null) return List.of();

        RulesEngine rules = new RulesEngine();

        Game replay = new Game();
        replay.whiteUser = savedGame.whiteUser;
        replay.blackUser = savedGame.blackUser;
        replay.createdAt = savedGame.createdAt;
        replay.board = Board.initial();
        replay.whiteMove = true;

        List<Map<String, Object>> out = new ArrayList<>();

        // initial position
        out.add(Map.of(
                "ply", 0,
                "atMs", savedGame.createdAt,
                "by", "",
                "move", "",
                "board", replay.board.toPrettyString(),
                "whiteInCheck", false,
                "blackInCheck", false
        ));

        int ply = 0;
        for (String rec : (savedGame.moves == null ? List.<String>of() : savedGame.moves)) {
            ply++;

            long atMs = savedGame.createdAt;
            String by = "";
            String uci = rec;

            // expected: atMs|by|uci
            String[] parts = rec.split("\\|", 3);
            if (parts.length == 3) {
                try { atMs = Long.parseLong(parts[0]); } catch (Exception ignored) {}
                by = parts[1];
                uci = parts[2];
            }

            try {
                Move m = Move.parse(uci);
                if (rules.isLegalMove(replay, replay.board, m)) {
                    rules.applyMove(replay.board, replay, m, true);
                    // flip turn like in real game:
                    replay.whiteMove = !replay.whiteMove;
                }
            } catch (Exception ignored) {
            }

            boolean wChk = rules.isKingInCheck(replay.board, true);
            boolean bChk = rules.isKingInCheck(replay.board, false);

            Map<String, Object> row = new HashMap<>();
            row.put("ply", ply);
            row.put("atMs", atMs);
            row.put("by", by);
            row.put("move", uci);
            row.put("board", replay.board.toPrettyString());
            row.put("whiteInCheck", wChk);
            row.put("blackInCheck", bChk);
            out.add(row);
        }

        return out;
    }

    private boolean updateStatsAndElo(Game g) throws IOException {
        if (g == null || g.whiteUser == null || g.blackUser == null) return false;
        if (g.result == Result.ONGOING) return false;

        return users.updateUsersAtomically(g.whiteUser, g.blackUser, (wu, bu) -> {
            if (wu.stats == null) wu.stats = new UserModels.Stats();
            if (bu.stats == null) bu.stats = new UserModels.Stats();

            // played always increments
            wu.stats.played++;
            bu.stats.played++;

            double sW;
            if (g.result == Result.WHITE_WIN) { wu.stats.won++; bu.stats.lost++; sW = 1.0; }
            else if (g.result == Result.BLACK_WIN) { bu.stats.won++; wu.stats.lost++; sW = 0.0; }
            else { wu.stats.drawn++; bu.stats.drawn++; sW = 0.5; }

            // elo
            int rW = wu.stats.rating <= 0 ? 1200 : wu.stats.rating;
            int rB = bu.stats.rating <= 0 ? 1200 : bu.stats.rating;

            double eW = 1.0 / (1.0 + Math.pow(10.0, (rB - rW) / 400.0));
            double eB = 1.0 - eW;

            double k = 32.0;
            int newRW = (int) Math.round(rW + k * (sW - eW));
            int newRB = (int) Math.round(rB + k * ((1.0 - sW) - eB));

            wu.stats.rating = Math.max(100, newRW);
            bu.stats.rating = Math.max(100, newRB);
        });
    }
}