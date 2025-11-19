package com.example.chess.client.net;

import com.example.chess.common.Msg;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class ClientConnection implements Closeable {

    private final String host;
    private final int port;
    private final Gson gson = new Gson();

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private Thread readerThread;
    private volatile boolean running;

    // For synchronous waiting on replies if we want later
    private final BlockingQueue<JsonObject> incoming = new LinkedBlockingQueue<>();

    // Optional callback for all messages
    private Consumer<JsonObject> onMessage;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setOnMessage(Consumer<JsonObject> onMessage) {
        this.onMessage = onMessage;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        running = true;

        readerThread = new Thread(this::readerLoop, "Client-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readerLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    if (obj != null) {
                        incoming.offer(obj);
                        if (onMessage != null) {
                            onMessage.accept(obj);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            running = false;
        }
    }

    public String nextCorrId() {
        return UUID.randomUUID().toString();
    }

    public void send(JsonObject obj) throws IOException {
        String line = Msg.jsonLine(obj);
        out.write(line);
        out.flush();
    }

    public JsonObject takeMessage() throws InterruptedException {
        return incoming.take();
    }

    @Override
    public void close() throws IOException {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}

