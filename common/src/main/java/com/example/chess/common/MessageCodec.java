package com.example.chess.common;

import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MessageCodec {
    private static final Gson GSON = new Gson();

    public static String toJson(Message m) {
        return GSON.toJson(m) + "\n";
    }

    public static Message fromJson(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        String type = obj.get("type").getAsString();
        String corrId = obj.has("corrId") ? obj.get("corrId").getAsString() : null;

        if (obj.has("error") || "error".equals(type)) {
            boolean err = obj.has("error") && obj.get("error").getAsBoolean();
            String msg = obj.has("message") ? obj.get("message").getAsString() : null;
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> payload = obj.has("payload")
                    ? GSON.fromJson(obj.get("payload"), mapType)
                    : new HashMap<>();
            return new ResponseMessage(type, corrId, err, msg, payload);
        } else {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> payload = obj.has("payload")
                    ? GSON.fromJson(obj.get("payload"), mapType)
                    : new java.util.HashMap<>();
            return new RequestMessage(type, corrId, payload);
        }
    }
}