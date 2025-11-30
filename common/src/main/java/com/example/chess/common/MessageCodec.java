package com.example.chess.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public class MessageCodec {
    private static final Gson GSON = new Gson();

    private MessageCodec() {}

    public static String toJsonLine(Message m) {
        JsonObject root = new JsonObject();
        root.addProperty("type", m.type);
        if (m.corrId != null) {
            root.addProperty("corrId", m.corrId);
        }

        for (Map.Entry<String, JsonElement> e : m.data.entrySet()) {
            root.add(e.getKey(), e.getValue());
        }

        return GSON.toJson(root) + "\n";
    }

    public static Message fromJsonLine(String line) {
        JsonObject root = GSON.fromJson(line, JsonObject.class);
        if (root == null || !root.has("type")) {
            throw new IllegalArgumentException("Missing 'type' field.");
        }

        Message m = new Message();
        m.type = root.get("type").getAsString();
        if (root.has("corrId")) {
            m.corrId = root.get("corrId").getAsString();
        }

        root.remove("type");
        root.remove("corrId");
        m.data = root;

        return m;
    }
}