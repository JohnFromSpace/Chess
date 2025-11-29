package com.example.chess.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class MessageCodec {
    private static final Gson GSON = new Gson();

    public static Message fromJsonLine(String line) {
        JsonObject root = GSON.fromJson(line, JsonObject.class);
        String type = root.get("type").getAsString();
        String corrId = root.has("corrId") ? root.get("corrId").getAsString() : null;

        JsonObject payload = new JsonObject();
        for (String key : root.keySet()) {
            if (!key.equals("type") && !key.equals("corrId")) {
                payload.add(key, root.get(key));
            }
        }
        return new Message(type, corrId, payload);
    }

    public static String toJsonLine(Message msg) {
        JsonObject root = new JsonObject();
        root.addProperty("type", msg.type);
        if (msg.corrId != null) {
            root.addProperty("corrId", msg.corrId);
        }
        if (msg.payload != null) {
            for (String key : msg.payload.keySet()) {
                root.add(key, msg.payload.get(key));
            }
        }
        return GSON.toJson(root) + "\n";
    }
}