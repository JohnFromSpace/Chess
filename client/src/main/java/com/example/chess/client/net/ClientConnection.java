package com.example.chess.client.net;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.common.MessageCodec;
import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;
import com.example.chess.common.proto.StatusMessage;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientConnection {

    private final String host;
    private final int port;
    private volatile ClientMessageListener listener;

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Thread readerThread;

    private final Map<String, CompletableFuture<StatusMessage>> pending = new ConcurrentHashMap<>();

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

    public void setListener(ClientMessageListener listener) {
        this.listener = listener;
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Message msg = MessageCodec.fromJson(line);

                if (msg instanceof ResponseMessage resp) {
                    StatusMessage status = StatusMessage.from(resp);

                    // correlated response -> complete the waiting future
                    if (resp.corrId != null) {
                        CompletableFuture<StatusMessage> fut = pending.remove(resp.corrId);
                        if (fut != null) {
                            fut.complete(status);
                            continue;
                        }
                    }

                    // async push -> deliver to listener
                    ClientMessageListener l = listener;
                    if (l != null) {
                        l.onMessage(status);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("ClientConnection readLoop error: " + e.getMessage());
            e.printStackTrace();
            pending.values().forEach(f -> f.completeExceptionally(e));
            pending.clear();
        }
    }

    public CompletableFuture<StatusMessage> sendAndWait(RequestMessage msg) {
        String corrId = msg.corrId;
        if (corrId == null || corrId.isBlank()) {
            corrId = UUID.randomUUID().toString();
            msg = new RequestMessage(msg.type, corrId, msg.payload);
        }

        CompletableFuture<StatusMessage> fut = new CompletableFuture<>();
        pending.put(corrId, fut);

        try {
            String json = MessageCodec.toJson(msg); // includes trailing newline
            synchronized (out) {
                out.write(json);
                out.flush();
            }
        } catch (IOException e) {
            pending.remove(corrId);
            fut.completeExceptionally(e);
        }

        return fut;
    }

    public void setPushHandler(Consumer<ResponseMessage> h) {
        Consumer<ResponseMessage> pushHandler = (h == null) ? (m -> {
        }) : h;
    }
}