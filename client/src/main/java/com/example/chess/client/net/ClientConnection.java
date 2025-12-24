package com.example.chess.client.net;

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

    private volatile Consumer<ResponseMessage> pushHandler = m -> {};

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Thread readerThread;

    private final Map<String, CompletableFuture<StatusMessage>> pending = new ConcurrentHashMap<>();

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    public void setPushHandler(Consumer<ResponseMessage> h) {
        this.pushHandler = (h == null) ? (m -> {}) : h;
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Message msg = MessageCodec.fromJson(line);

                if (msg instanceof ResponseMessage resp) {
                    // correlated response -> complete the waiting future
                    if (resp.corrId != null) {
                        CompletableFuture<StatusMessage> fut = pending.remove(resp.corrId);
                        if (fut != null) {
                            fut.complete(StatusMessage.from(resp));
                            continue;
                        }
                    }

                    // async push
                    Consumer<ResponseMessage> ph = pushHandler;
                    if (ph != null) ph.accept(resp);
                }
            }
        } catch (IOException e) {
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

    public CompletableFuture<StatusMessage> login(String username, String password) {
        return sendAndWait(new RequestMessage("login", UUID.randomUUID().toString(),
                Map.of("username", username, "password", password)));
    }

    public CompletableFuture<StatusMessage> register(String username, String name, String password) {
        return sendAndWait(new RequestMessage("register", UUID.randomUUID().toString(),
                Map.of("username", username, "name", name, "password", password)));
    }

    public CompletableFuture<StatusMessage> requestGame() {
        return sendAndWait(new RequestMessage("requestGame", UUID.randomUUID().toString(), Map.of()));
    }

    public CompletableFuture<StatusMessage> makeMove(String gameId, String move) {
        return sendAndWait(new RequestMessage("makeMove", UUID.randomUUID().toString(),
                Map.of("gameId", gameId, "move", move)));
    }

    public CompletableFuture<StatusMessage> offerDraw(String gameId) {
        return sendAndWait(new RequestMessage("offerDraw", UUID.randomUUID().toString(),
                Map.of("gameId", gameId)));
    }

    public CompletableFuture<StatusMessage> resign(String gameId) {
        return sendAndWait(new RequestMessage("resign", UUID.randomUUID().toString(),
                Map.of("gameId", gameId)));
    }

    public CompletableFuture<StatusMessage> getStats() {
        return sendAndWait(RequestMessage.of("getStats"));
    }

    public CompletableFuture<StatusMessage> listGames() {
        return sendAndWait(RequestMessage.of("listGames"));
    }

    public CompletableFuture<StatusMessage> getGameDetails(String gameId) {
        return sendAndWait(RequestMessage.of("getGameDetails").with("gameId", gameId));
    }

    public CompletableFuture<StatusMessage> logout() {
        return sendAndWait(RequestMessage.of("logout"));
    }
}