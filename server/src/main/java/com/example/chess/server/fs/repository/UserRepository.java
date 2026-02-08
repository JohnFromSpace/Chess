package com.example.chess.server.fs.repository;

import com.example.chess.common.UserModels;
import com.example.chess.common.UserModels.User;
import com.example.chess.server.db.Db;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.util.Log;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class UserRepository {
    private final Db db;
    private final FileStores backups;

    public UserRepository(Db db, FileStores backups) {
        this.db = db;
        this.backups = backups;
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();

        String sql = """
                SELECT username, name, pass_hash, played, won, drawn, lost, rating
                FROM users
                WHERE username = ?
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read user from database.", e);
        }
    }

    public void saveUser(User user) throws IOException {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("User or username is null/blank.");
        }

        String sql = """
                INSERT INTO users (username, name, pass_hash, played, won, drawn, lost, rating)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (username) DO UPDATE SET
                    name = EXCLUDED.name,
                    pass_hash = EXCLUDED.pass_hash,
                    played = EXCLUDED.played,
                    won = EXCLUDED.won,
                    drawn = EXCLUDED.drawn,
                    lost = EXCLUDED.lost,
                    rating = EXCLUDED.rating,
                    updated_at = now()
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindUser(ps, user);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save user to database.", e);
        }

        backupAllUsers();
    }

    public User createUser(String username, String name, String passHash) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        String sql = """
                INSERT INTO users (username, name, pass_hash, played, won, drawn, lost, rating)
                VALUES (?, ?, ?, 0, 0, 0, 0, 1200)
                """;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, name == null ? "" : name);
            ps.setString(3, passHash == null ? "" : passHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Username is already taken.");
            }
            throw new IOException("Failed to create user.", e);
        }

        User created = new User();
        created.setUsername(username);
        created.setName(name);
        created.setPassHash(passHash);

        backupAllUsers();
        return created;
    }

    public void updateTwoUsers(String usernameA,
                               String usernameB,
                               BiConsumer<User, User> mutator) throws IOException {
        if (usernameA == null || usernameA.isBlank()) {
            throw new IllegalArgumentException("Missing username.");
        }
        if (usernameB == null || usernameB.isBlank()) {
            throw new IllegalArgumentException("Missing username.");
        }
        if (usernameA.equals(usernameB)) {
            throw new IllegalArgumentException("Usernames must be different.");
        }
        if (mutator == null) throw new IllegalArgumentException("Missing user mutator.");

        String sql = """
                SELECT username, name, pass_hash, played, won, drawn, lost, rating
                FROM users
                WHERE username = ? OR username = ?
                ORDER BY username
                FOR UPDATE
                """;
        String update = """
                UPDATE users
                SET name = ?, pass_hash = ?, played = ?, won = ?, drawn = ?, lost = ?, rating = ?, updated_at = now()
                WHERE username = ?
                """;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                Map<String, User> users = new LinkedHashMap<>();

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, usernameA);
                    ps.setString(2, usernameB);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            User u = mapUser(rs);
                            users.put(u.getUsername(), u);
                        }
                    }
                }

                User a = users.get(usernameA);
                User b = users.get(usernameB);
                if (a == null || b == null) {
                    throw new IllegalArgumentException("Missing user in store.");
                }

                mutator.accept(a, b);

                try (PreparedStatement ps = c.prepareStatement(update)) {
                    bindUserUpdate(ps, a);
                    ps.executeUpdate();
                    bindUserUpdate(ps, b);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (SQLException ex) {
                    Log.warn("Failed to roll back user update.", ex);
                }
                if (e instanceof SQLException se) {
                    throw new IOException("Failed to update users.", se);
                }
                if (e instanceof RuntimeException re) throw re;
                throw new IOException("Failed to update users.", e);
            }
        }

        backupAllUsers();
    }

    private void backupAllUsers() {
        if (backups == null) return;
        try {
            backups.writeAllUsers(loadAllUsers());
        } catch (RuntimeException | IOException e) {
            Log.warn("Failed to update users.json backup.", e);
        }
    }

    private Map<String, User> loadAllUsers() {
        String sql = """
                SELECT username, name, pass_hash, played, won, drawn, lost, rating
                FROM users
                ORDER BY username
                """;
        Map<String, User> out = new LinkedHashMap<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = mapUser(rs);
                out.put(u.getUsername(), u);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load users for backup.", e);
        }
        return out;
    }

    private static void bindUser(PreparedStatement ps, User user) throws SQLException {
        UserModels.Stats s = user.stats == null ? new UserModels.Stats() : user.stats;
        ps.setString(1, user.getUsername());
        ps.setString(2, user.getName() == null ? "" : user.getName());
        ps.setString(3, user.getPassHash() == null ? "" : user.getPassHash());
        ps.setInt(4, s.getPlayed());
        ps.setInt(5, s.getWon());
        ps.setInt(6, s.getDrawn());
        ps.setInt(7, s.getLost());
        ps.setInt(8, s.getRating());
    }

    private static void bindUserUpdate(PreparedStatement ps, User user) throws SQLException {
        UserModels.Stats s = user.stats == null ? new UserModels.Stats() : user.stats;
        ps.setString(1, user.getName() == null ? "" : user.getName());
        ps.setString(2, user.getPassHash() == null ? "" : user.getPassHash());
        ps.setInt(3, s.getPlayed());
        ps.setInt(4, s.getWon());
        ps.setInt(5, s.getDrawn());
        ps.setInt(6, s.getLost());
        ps.setInt(7, s.getRating());
        ps.setString(8, user.getUsername());
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUsername(rs.getString("username"));
        u.setName(rs.getString("name"));
        u.setPassHash(rs.getString("pass_hash"));

        UserModels.Stats stats = new UserModels.Stats();
        stats.setPlayed(rs.getInt("played"));
        stats.setWon(rs.getInt("won"));
        stats.setDrawn(rs.getInt("drawn"));
        stats.setLost(rs.getInt("lost"));
        stats.setRating(rs.getInt("rating"));
        u.stats = stats;
        return u;
    }
}
