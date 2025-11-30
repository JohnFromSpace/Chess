package com.example.chess.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Message {
    public String type;
    public String corrId;
    public JsonObject data = new JsonObject();

    public Message() {}

    public Message(String type, String corrId, JsonObject data) {
        this.type = type;
        this.corrId = corrId;
        this.data = data;
    }

    public Message(String type, String corrId) {
        this.type = type;
        this.corrId = corrId;
    }

    public static Message of(String type, String corrId) {
        return new Message(type, corrId);
    }

    public Message put(String key, String value) {
        data.addProperty(key, value);
        return this;
    }

    public Message put(String key, int value) {
        data.addProperty(key, value);
        return this;
    }

    public Message put(String key, boolean value) {
        data.addProperty(key, value);
        return this;
    }

    public Message put(String key, JsonElement value) {
        data.add(key, value);
        return this;
    }

    public String getRequiredString(String field) {
        if (!data.has(field) || data.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return data.get(field).getAsString();
    }

    public int getIntOrDefault(String field, int def) {
        if (!data.has(field) || data.get(field).isJsonNull()) {
            return def;
        }
        return data.get(field).getAsInt();
    }

    public boolean getBooleanOrDefault(String field, boolean def) {
        if (!data.has(field) || data.get(field).isJsonNull()) {
            return def;
        }
        return data.get(field).getAsBoolean();
    }
}