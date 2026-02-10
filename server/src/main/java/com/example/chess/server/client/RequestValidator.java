package com.example.chess.server.client;

import com.example.chess.common.message.RequestMessage;

import java.util.regex.Pattern;

final class RequestValidator {
    private static final int MAX_TYPE_LENGTH = 32;
    private static final int MAX_CORR_ID_LENGTH = 128;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MIN_PASSWORD_LENGTH =
            Integer.parseInt(System.getProperty("chess.validation.password.min", "6"));
    private static final int MAX_PASSWORD_LENGTH =
            Integer.parseInt(System.getProperty("chess.validation.password.max", "128"));
    private static final int MAX_GAME_ID_LENGTH = 64;
    private static final int MAX_MOVE_LENGTH = 16;

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]*$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private RequestValidator() {}

    static boolean isValidType(String type) {
        if (type == null) return false;
        String t = type.trim();
        if (t.isEmpty() || t.length() > MAX_TYPE_LENGTH) return false;
        if (!TYPE_PATTERN.matcher(t).matches()) return false;
        return !containsControl(t);
    }

    static boolean isValidCorrId(String corrId) {
        if (corrId == null) return true;
        String c = corrId.trim();
        if (c.isEmpty() || c.length() > MAX_CORR_ID_LENGTH) return false;
        return !containsControl(c);
    }

    static String requireUsername(RequestMessage req) {
        String username = reqStr(req, "username", MAX_USERNAME_LENGTH);
        if (username.length() < MIN_USERNAME_LENGTH) throw new IllegalArgumentException("Username too short.");
        if (!USERNAME_PATTERN.matcher(username).matches())
            throw new IllegalArgumentException("Username has invalid characters.");
        return username;
    }

    static String requireName(RequestMessage req) {
        String name = reqStr(req, "name", MAX_NAME_LENGTH);
        if (containsControl(name)) throw new IllegalArgumentException("Name contains invalid characters.");
        return name;
    }

    static String requirePassword(RequestMessage req, boolean enforceMin) {
        String password = reqStr(req, "password", MAX_PASSWORD_LENGTH);
        if (enforceMin && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password too short.");
        }
        if (containsControl(password)) throw new IllegalArgumentException("Password contains invalid characters.");
        return password;
    }

    static String requireGameId(RequestMessage req) {
        String gameId = reqStr(req, "gameId", MAX_GAME_ID_LENGTH);
        if (!GAME_ID_PATTERN.matcher(gameId).matches())
            throw new IllegalArgumentException("Invalid gameId.");
        return gameId;
    }

    static String requireMove(RequestMessage req) {
        String move = reqStr(req, "move", MAX_MOVE_LENGTH);
        if (containsControl(move)) throw new IllegalArgumentException("Invalid move.");
        return move;
    }

    private static String reqStr(RequestMessage m, String key) {
        if (m == null || m.getPayload() == null) throw new IllegalArgumentException("Missing payload.");
        Object v = m.getPayload().get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        if (!(v instanceof String)) throw new IllegalArgumentException("Expected string field: " + key);
        String s = ((String) v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Blank field: " + key);
        return s;
    }

    private static String reqStr(RequestMessage m, String key, int maxLen) {
        String s = reqStr(m, key);
        if (maxLen > 0 && s.length() > maxLen) throw new IllegalArgumentException("Field too long: " + key);
        return s;
    }

    private static boolean containsControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) return true;
        }
        return false;
    }
}
