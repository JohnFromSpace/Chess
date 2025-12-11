package com.example.chess.client.net;

import com.example.chess.client.ClientMessageListener;
import com.example.chess.common.MessageCodec;
import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;
import com.google.gson.Gson;

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
    private final Map<String, CompletableFuture<ResponseMessage>> pending = new ConcurrentHashMap<>();

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

                if ((msg instanceof ResponseMessage resp) && (resp.corrId != null)) {
                    CompletableFuture<ResponseMessage> fut = pending.remove(resp.corrId);
                    if (fut != null) {
                        fut.complete(resp);
                    } else {
                        // async notification, if you have such (info, gameStarted, move, etc.)
                        handleAsync(resp);
                    }
                } else {
                    // server-pushed messages without corrId
                    handleAsync(msg);
                }

                if (msg == null) throw new AssertionError("Empty message!");
                String corrId = msg.has("corrId") && !msg.get("corrId").isJsonNull()
                        ? msg.get("corrId").getAsString()
                        : null;

                if (corrId != null) {
                    CompletableFuture<ResponseMessage> fut = pending.remove(corrId);
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
            for (CompletableFuture<ResponseMessage> f : pending.values()) {
                f.completeExceptionally(ex);
            }
            pending.clear();
        }
    }

    public CompletableFuture<ResponseMessage> sendAndWait(RequestMessage msg) {
        String corrId = msg.corrId;
        if (corrId == null) {
            corrId = UUID.randomUUID().toString();
            msg = new RequestMessage(msg.type, corrId, msg.payload);
        }

        CompletableFuture<ResponseMessage> fut = new CompletableFuture<>();
        pending.put(corrId, fut);

        try {
            String json = MessageCodec.toJson(msg);
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
}


