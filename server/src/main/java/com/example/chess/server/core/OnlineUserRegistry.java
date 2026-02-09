package com.example.chess.server.core;

import com.example.chess.server.client.ClientHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OnlineUserRegistry {
    private final ConcurrentMap<String, ClientHandler> online = new ConcurrentHashMap<>();

    public void markOnline(String username, ClientHandler handler) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Empty username.");
        if (handler == null) throw new IllegalArgumentException("There is no client handler.");

        ClientHandler prev = online.putIfAbsent(username, handler);
        if (prev != null && prev != handler) {
            throw new IllegalArgumentException("User '" + username + "' is already logged in.");
        }
    }

    public void markOffline(String username, ClientHandler handler) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Empty username.");
        if (handler == null) throw new IllegalArgumentException("There is no client handler.");
        online.remove(username, handler);
    }

    public int onlineCount() {
        return online.size();
    }
}
