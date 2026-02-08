package com.example.chess.server.db;

import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class DbBootstrap {
    private static final Gson GSON = new GsonBuilder().create();

    private DbBootstrap() {
    }

    public static void importJsonIfEmpty(Db db, FileStores backups) {
        if (db == null || backups == null) return;

        boolean usersEmpty = isTableEmpty(db, "users");
        boolean gamesEmpty = isTableEmpty(db, "games");

        if (usersEmpty) {
            importUsers(db, backups.loadAllUsers());
        }

        if (gamesEmpty) {
            importGames(db, backups.loadAllGames());
        }
    }

    private static boolean isTableEmpty(Db db, String table) {
        String sql = "SELECT 1 FROM " + table + " LIMIT 1";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return !rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check database table: " + table, e);
        }
    }

    private static void importUsers(Db db, Map<String, UserModels.User> users) {
        if (users == null || users.isEmpty()) return;

        String sql = """
                INSERT INTO users (username, name, pass_hash, played, won, drawn, lost, rating)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (username) DO NOTHING
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            int count = 0;

            for (UserModels.User u : users.values()) {
                if (u == null || u.getUsername() == null || u.getUsername().isBlank()) continue;
                UserModels.Stats s = u.stats == null ? new UserModels.Stats() : u.stats;

                ps.setString(1, u.getUsername());
                ps.setString(2, safeString(u.getName()));
                ps.setString(3, safeString(u.getPassHash()));
                ps.setInt(4, s.getPlayed());
                ps.setInt(5, s.getWon());
                ps.setInt(6, s.getDrawn());
                ps.setInt(7, s.getLost());
                ps.setInt(8, s.getRating());
                ps.addBatch();
                count++;
            }

            if (count > 0) {
                ps.executeBatch();
                c.commit();
                Log.warn("Imported " + count + " users from JSON backup.", null);
            } else {
                c.rollback();
            }
        } catch (SQLException e) {
            Log.warn("Failed to import users from JSON backup.", e);
        }
    }

    private static void importGames(Db db, List<Game> games) {
        if (games == null || games.isEmpty()) return;

        String sql = """
                INSERT INTO games (id, white_user, black_user, created_at_ms, last_update_ms, result, rated, game_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (id) DO NOTHING
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            int count = 0;

            for (Game g : games) {
                if (g == null || g.getId() == null || g.getId().isBlank()) continue;
                if (g.getWhiteUser() == null || g.getWhiteUser().isBlank()) continue;
                if (g.getBlackUser() == null || g.getBlackUser().isBlank()) continue;

                sanitizeReason(g);
                String json = GSON.toJson(g);
                String result = g.getResult() == null ? "ONGOING" : g.getResult().name();

                ps.setString(1, g.getId());
                ps.setString(2, g.getWhiteUser());
                ps.setString(3, g.getBlackUser());
                ps.setLong(4, g.getCreatedAt());
                ps.setLong(5, g.getLastUpdate());
                ps.setString(6, result);
                ps.setBoolean(7, g.isRated());
                ps.setString(8, json);
                ps.addBatch();
                count++;
            }

            if (count > 0) {
                ps.executeBatch();
                c.commit();
                Log.warn("Imported " + count + " games from JSON backup.", null);
            } else {
                c.rollback();
            }
        } catch (SQLException e) {
            Log.warn("Failed to import games from JSON backup.", e);
        }
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static void sanitizeReason(Game game) {
        if (game == null) return;
        String r = game.getResultReason();
        if (r == null) return;
        r = r.trim();
        if (r.equalsIgnoreCase("Time.") || r.equalsIgnoreCase("Time")) {
            game.setResultReason("timeout");
        }
    }
}
