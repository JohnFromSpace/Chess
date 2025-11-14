package com.example.chess.server;

import com.example.chess.common.Msg;
import com.example.chess.common.UserModels.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final Gson gson = new Gson();

    private BufferedReader in;
    private BufferedWriter out;
    private User currentUser;

    public ClientHandler(Socket socket, AuthService authService) {
        this.socket = socket;
        this.authService = authService;
    }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                handleLine(line);
            }
        } catch (IOException e) {
            // client disconnected or IO problem
            // just end the thread
        }
    }

    private void handleLine(String line) {
        JsonObject msg;
        try {
            msg = gson.fromJson(line, JsonObject.class);
        } catch (Exception e) {
            sendError(null, "Invalid JSON.");
            return;
        }
        if (msg == null || !msg.has("type")) {
            sendError(null, "Missing 'type' field.");
            return;
        }

        String type = msg.get("type").getAsString();
        String corrId = msg.has("corrId") ? msg.get("corrId").getAsString() : null;

        try {
            switch (type) {
                case "ping" -> handlePing(corrId);
                case "register" -> handleRegister(corrId, msg);
                case "login" -> handleLogin(corrId, msg);
                default -> sendError(corrId, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException ex) {
            // business validation errors
            sendError(corrId, ex.getMessage());
        } catch (Exception ex) {
            sendError(corrId, "Internal server error.");
        }
    }

    private void handlePing(String corrId) {
        JsonObject o = Msg.obj("pong", corrId);
        send(o);
    }

    private void handleRegister(String corrId, JsonObject msg) {
        String username = getRequiredString(msg, "username");
        String name = getRequiredString(msg, "name");
        String password = getRequiredString(msg, "password");

        User user = authService.register(username, name, password);

        JsonObject o = Msg.obj("registerOk", corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        o.add("user", u);
        send(o);
    }

    private void handleLogin(String corrId, JsonObject msg) {
        String username = getRequiredString(msg, "username");
        String password = getRequiredString(msg, "password");

        User user = authService.login(username, password);
        this.currentUser = user;

        JsonObject o = Msg.obj("loginOk", corrId);
        JsonObject u = new JsonObject();
        u.addProperty("username", user.username);
        u.addProperty("name", user.name);
        u.addProperty("played", user.stats.played);
        u.addProperty("won", user.stats.won);
        u.addProperty("rating", user.stats.rating);
        o.add("user", u);
        send(o);
    }

    private String getRequiredString(JsonObject obj, String field) {
        if (!obj.has(field)) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return obj.get(field).getAsString();
    }

    private void sendError(String corrId, String message) {
        JsonObject o = Msg.obj("error", corrId);
        o.addProperty("message", message);
        send(o);
    }

    private synchronized void send(JsonObject o) {
        try {
            String line = Msg.jsonLine(o);
            out.write(line);
            out.flush();
        } catch (IOException e) {
            // client is disconnected
        }
    }
}

