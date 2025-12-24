package com.example.chess.client.ui;

import com.example.chess.common.UserModels;

import java.util.Map;

public final class ProfileScreenUserMapper {
    private ProfileScreenUserMapper() {}

    @SuppressWarnings("unchecked")
    public static UserModels.User userFromPayload(Map<String, Object> payload) {
        if (payload == null) return null;
        Object userObj = payload.get("user");
        if (!(userObj instanceof Map<?, ?> um)) return null;

        UserModels.User u = new UserModels.User();
        u.username = str(um.get("username"));
        u.name     = str(um.get("name"));

        if (u.stats == null) u.stats = new UserModels.Stats();
        u.stats.played = intVal(um.get("played"));
        u.stats.won    = intVal(um.get("won"));
        u.stats.lost   = intVal(um.get("lost"));
        u.stats.drawn  = intVal(um.get("drawn"));
        u.stats.rating = intValOr(um.get("rating"), 1200);

        return u;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }

    private static int intValOr(Object o, int def) {
        int v = intVal(o);
        return v == 0 ? def : v;
    }
}