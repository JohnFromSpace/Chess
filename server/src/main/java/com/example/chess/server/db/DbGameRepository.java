package com.example.chess.server.db;

import com.example.chess.common.model.Game;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DbGameRepository implements GameRepository {
    private static final Gson GSON = new GsonBuilder().create();

    private final Db db;
    private final FileStores backups;

    public DbGameRepository(Db db, FileStores backups) {
        this.db = db;
        this.backups = backups;
    }

    @Override
    public void saveGame(Game game) throws IOException {
        if (game == null || game.getId() == null || game.getId().isBlank()) {
            throw new IllegalArgumentException("Game or game.id is null/blank");
        }
        if (game.getWhiteUser() == null || game.getWhiteUser().isBlank()) {
            throw new IllegalArgumentException("Game has no white user.");
        }
        if (game.getBlackUser() == null || game.getBlackUser().isBlank()) {
            throw new IllegalArgumentException("Game has no black user.");
        }

        sanitizeReason(game);

        String json = GSON.toJson(game);
        String result = game.getResult() == null ? "ONGOING" : game.getResult().name();

        String sql = """
                INSERT INTO games (id, white_user, black_user, created_at_ms, last_update_ms, result, rated, game_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (id) DO UPDATE SET
                    white_user = EXCLUDED.white_user,
                    black_user = EXCLUDED.black_user,
                    created_at_ms = EXCLUDED.created_at_ms,
                    last_update_ms = EXCLUDED.last_update_ms,
                    result = EXCLUDED.result,
                    rated = EXCLUDED.rated,
                    game_json = EXCLUDED.game_json
                WHERE games.last_update_ms <= EXCLUDED.last_update_ms
                """;

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, game.getId());
            ps.setString(2, game.getWhiteUser());
            ps.setString(3, game.getBlackUser());
            ps.setLong(4, game.getCreatedAt());
            ps.setLong(5, game.getLastUpdate());
            ps.setString(6, result);
            ps.setBoolean(7, game.isRated());
            ps.setString(8, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save game to database.", e);
        }

        if (backups != null) {
            try {
                backups.saveGame(game);
            } catch (IOException e) {
                Log.warn("Failed to write game backup: " + game.getId(), e);
            }
        }
    }

    @Override
    public Optional<Game> findGameById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();

        String sql = "SELECT id, game_json FROM games WHERE id = ?";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String json = rs.getString("game_json");
                Game g = parseGame(json, rs.getString("id"));
                return Optional.ofNullable(g);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read game from database.", e);
        }
    }

    @Override
    public Map<String, Game> findGamesForUser(String username) {
        Map<String, Game> out = new HashMap<>();
        if (username == null || username.isBlank()) return out;

        String sql = """
                SELECT id, game_json
                FROM games
                WHERE white_user = ? OR black_user = ?
                ORDER BY last_update_ms DESC
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    Game g = parseGame(rs.getString("game_json"), id);
                    if (g != null) out.put(id, g);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read user games from database.", e);
        }
        return out;
    }

    @Override
    public List<Game> loadAllGames() {
        List<Game> out = new ArrayList<>();
        String sql = "SELECT id, game_json FROM games";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                Game g = parseGame(rs.getString("game_json"), id);
                if (g != null) out.add(g);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load games from database.", e);
        }
        return out;
    }

    private static Game parseGame(String json, String id) {
        if (json == null || json.isBlank()) return null;
        try {
            Game game = GSON.fromJson(json, Game.class);
            if (game != null && (game.getId() == null || game.getId().isBlank())) {
                game.setId(id);
            }
            sanitizeReason(game);
            return game;
        } catch (RuntimeException e) {
            Log.warn("Failed to parse game JSON from database: " + id, e);
            return null;
        }
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
