package com.example.chess.common.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MessageCodec {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    public static String toJson(Message m) {
        return GSON.toJson(m) + "\n";
    }

    public static String toJsonLine(Message m) { return toJson(m); }
    public static Message fromJsonLine(String line) { return fromJson(line); }

    public static Message fromJson(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        String type = obj.get("type").getAsString();
        String corrId = obj.has("corrId") && !obj.get("corrId").isJsonNull()
                ? obj.get("corrId").getAsString()
                : null;

        boolean looksLikeResponse = obj.has("error") || "error".equals(type);

        Map<String, Object> payload = obj.has("payload") && !obj.get("payload").isJsonNull()
                ? GSON.fromJson(obj.get("payload"), MAP_TYPE)
                : new HashMap<>();

        if (looksLikeResponse) {
            boolean err = obj.has("error") && !obj.get("error").isJsonNull() && obj.get("error").getAsBoolean();
            String msg = obj.has("message") && !obj.get("message").isJsonNull()
                    ? obj.get("message").getAsString()
                    : null;
            return new ResponseMessage(type, corrId, err, msg, payload);
        }

        return new RequestMessage(type, corrId, payload);
    }
}