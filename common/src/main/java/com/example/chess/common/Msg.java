package com.example.chess.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;

public final class Msg {
    private static final Gson G = new Gson();

    private Msg() {}

    public static String nextId() {
        return UUID.randomUUID().toString();
    }

    public static String jsonLine(JsonObject o) {
        return G.toJson(o) + "\n";
    }

    public static JsonObject obj(String type, String corrId) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        if (corrId != null) {
            o.addProperty("corrId", corrId);
        }
        return o;
    }
}


