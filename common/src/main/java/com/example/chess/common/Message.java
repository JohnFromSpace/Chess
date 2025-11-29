package com.example.chess.common;

import com.google.gson.JsonObject;

public class Message {
    public String type;
    public String corrId;
    public JsonObject payload;

    public Message(String type, String corrId, JsonObject payload) {
        this.type = type;
        this.corrId = corrId;
        this.payload = payload;
    }
}