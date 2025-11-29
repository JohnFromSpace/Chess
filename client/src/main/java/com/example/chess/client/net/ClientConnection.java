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
            while ((line = in.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) continue;

                JsonObject msg = gson.fromJson(line, JsonObject.class);
                String type = msg.get("type").getAsString();

                if("error".equals(type)) {
                    String message = msg.has("message") ?
                            msg.get("message").getAsString() :
                            "Unknown error from server.";
                } else if ("info".equals(type)) {
                    String message = msg.has("message") ?
                            msg.get("message").getAsString() :
                            "";
                } else {
                    System.out.println("[SERVER] " + msg);
                }
            }
        } catch (IOException e) {
            System.out.println("Connection to server closed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error reading from server: " + e.getMessage());
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

