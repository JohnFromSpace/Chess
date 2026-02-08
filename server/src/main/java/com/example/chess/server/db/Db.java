package com.example.chess.server.db;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    private final String url;
    private final String user;
    private final String password;

    public Db(String url, String user, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing chess.db.url system property.");
        }
        this.url = url;
        this.user = user;
        this.password = password;
        loadDriver();
    }

    public static Db fromSystemProperties() {
        String url = System.getProperty("chess.db.url");
        String user = System.getProperty("chess.db.user", "");
        String password = System.getProperty("chess.db.password", "");
        return new Db(url, user, password);
    }

    public Connection open() throws SQLException {
        if (user == null || user.isBlank()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, password);
    }

    public void migrate() {
        String dbUser = (user == null || user.isBlank()) ? null : user;
        String dbPass = (password == null || password.isBlank()) ? null : password;
        Flyway.configure()
                .dataSource(url, dbUser, dbPass)
                .load()
                .migrate();
    }

    private static void loadDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found on classpath.", e);
        }
    }
}
