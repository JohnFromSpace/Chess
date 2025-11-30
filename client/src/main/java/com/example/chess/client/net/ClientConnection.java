package com.example.chess.client.net;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.common.Msg;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;

public class ClientConnection implements Runnable {
    private final String host;
    private final int port;
    private final ClientMessageListener listener;

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private final Gson gson = new Gson();

    private volatile boolean running = false;

    public ClientConnection(String host, int port, ClientMessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        running = true;
        Thread t = new Thread(this, "client-connection");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                handleIncomingLine(line);
            }
        } catch (IOException e) {
            System.err.println("Connection to server lost: " + e.getMessage());
        } finally {
            running = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleIncomingLine(String line) {
        try {
            JsonObject msg = gson.fromJson(line, JsonObject.class);
            if (msg == null || !msg.has("type")) {
                System.out.println("[SERVER RAW] " + line);
                return;
            }
            listener.onMessage(msg);
        } catch (Exception e) {
            System.err.println("Failed to parse server message: " + e.getMessage());
            System.err.println("Raw line: " + line);
        }
    }

    public synchronized void send(JsonObject msg) {
        try {
            String line = Msg.jsonLine(msg);
            out.write(line);
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send message to server: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}

