package com.example.chess.client;

import com.google.gson.JsonObject;

public interface ClientMessageListener {
    void onMessage(JsonObject msg);
}

