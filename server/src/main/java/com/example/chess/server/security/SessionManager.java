package com.example.chess.server.security;

import com.example.chess.common.UserModels;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionManager {

    private static final SecureRandom RNG = new SecureRandom();

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlMs;

    public SessionManager(long ttlMs) {
        if (ttlMs <= 0) throw new IllegalArgumentException("ttlMs must be > 0");
        this.ttlMs = ttlMs;
    }

    public String issue(UserModels.User user) {
        Objects.requireNonNull(user, "user");
        String token = newToken();
        long exp = System.currentTimeMillis() + ttlMs;
        sessions.put(token, new Session(user, exp));
        return token;
    }

    public UserModels.User requireValid(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing auth token.");
        Session s = sessions.get(token);
        if (s == null) throw new IllegalArgumentException("Invalid session.");
        if (System.currentTimeMillis() > s.expiresAtMs()) {
            sessions.remove(token);
            throw new IllegalArgumentException("Session expired.");
        }
        return s.user();
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        sessions.remove(token);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Session(UserModels.User user, long expiresAtMs) {}
}