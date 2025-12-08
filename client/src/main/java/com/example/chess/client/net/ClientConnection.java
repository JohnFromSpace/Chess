package com.example.chess.client.net;

import com.example.chess.client.ClientMessageListener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ClientConnection {

    private final String host;
    private final int port;
    private ClientMessageListener listener;
    private final Gson gson = new Gson();

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Thread readerThread;

    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();

    public ClientConnection(String host, int port, ClientMessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to stop", e);
        }
    }

    public void setListener(ClientMessageListener listener) {
        this.listener = listener;
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                JsonObject msg;
                try {
                    msg = gson.fromJson(line, JsonObject.class);
                } catch (Exception ex) {
                    continue;
                }

                String corrId = msg.has("corrId") && !msg.get("corrId").isJsonNull()
                        ? msg.get("corrId").getAsString()
                        : null;

                if (corrId != null) {
                    CompletableFuture<JsonObject> fut = pending.remove(corrId);
                    if (fut != null) {
                        fut.complete(msg);
                        continue;
                    }
                }

                if (listener != null) {
                    listener.onMessage(msg);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read", e);
        } finally {
            IOException ex = new IOException("Connection closed");
            for (CompletableFuture<JsonObject> f : pending.values()) {
                f.completeExceptionally(ex);
            }
            pending.clear();
        }
    }

    public synchronized void send(JsonObject msg) {
        try {
            String line = gson.toJson(msg) + "\n";
            out.write(line);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send to server", e);
        }
    }

    public CompletableFuture<JsonObject> sendAndWait(JsonObject msg) {
        String corrId;
        if (msg.has("corrId") && !msg.get("corrId").isJsonNull()) {
            corrId = msg.get("corrId").getAsString();
        } else {
            corrId = UUID.randomUUID().toString();
            msg.addProperty("corrId", corrId);
        }

        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(corrId, fut);
        send(msg);
        return fut;
    }
}


