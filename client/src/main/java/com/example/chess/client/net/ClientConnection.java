package com.example.chess.client.net;

import com.example.chess.common.message.MessageCodec;
import com.example.chess.common.message.Message;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.common.message.StatusMessage;
import com.example.chess.client.security.Tls;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientConnection implements AutoCloseable {

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

    public void start() throws Exception {
        boolean tls = Boolean.parseBoolean(System.getProperty("chess.tls.enabled", "true"));
        socket = tls ? Tls.createClientSocket(host, port) : new Socket(host, port);

        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);

        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
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
                    if (resp.getCorrId() != null) {
                        CompletableFuture<StatusMessage> fut = pending.remove(resp.getCorrId());
                        if (fut != null) {
                            fut.complete(StatusMessage.from(resp));
                            continue;
                        }
                    }

                    Consumer<ResponseMessage> ph = pushHandler;
                    if (ph != null) ph.accept(resp);
                }
            }
        } catch (IOException e) {
            com.example.chess.client.util.Log.warn("Client read loop stopped.", e);
            pending.values().forEach(f -> f.completeExceptionally(e));
            pending.clear();
        } finally {
            close();
        }
    }

    public CompletableFuture<StatusMessage> sendAndWait(RequestMessage msg) {
        String corrId = msg.getCorrId();
        if (corrId == null || corrId.isBlank()) {
            corrId = UUID.randomUUID().toString();
            msg = new RequestMessage(msg.getType(), corrId, msg.getPayload());
        }

        CompletableFuture<StatusMessage> fut = new CompletableFuture<>();
        pending.put(corrId, fut);

        try {
            if (!isOpen()) throw new IOException("Connection closed.");
            String json = MessageCodec.toJson(msg);

            out.write(json);
            out.flush();
        } catch (IOException e) {
            pending.remove(corrId);
            fut.completeExceptionally(e);
            com.example.chess.client.util.Log.warn("Failed to send request " + corrId, e);
        }
        return fut;
    }

    @Override
    public void close() {
        // complete any waiters
        IOException closedEx = new IOException("Connection closed.");
        pending.values().forEach(f -> f.completeExceptionally(closedEx));
        pending.clear();

        // close streams/socket
        try { if (in != null) in.close(); } catch (Exception e) {com.example.chess.client.util.Log.warn("Failed to close input stream: ", e);}
        try { if (out != null) out.close(); } catch (Exception e) {com.example.chess.client.util.Log.warn("Failed to close output stream: ", e);}
        try { if (socket != null) socket.close(); } catch (Exception e) {com.example.chess.client.util.Log.warn("Failed to close current socket: ", e);}

        // stop reader thread if needed
        try {
            if (readerThread != null) readerThread.interrupt();
        } catch (Exception e) {
            com.example.chess.client.util.Log.warn("Failed to interrupt reader (thread): ", e);
        }

        try {
            if(readerThread != null && Thread.currentThread() != readerThread) {
                readerThread.join(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Current thread failed to wait for 500 milliseconds: " + e.getMessage());
        }
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

    public CompletableFuture<StatusMessage> acceptDraw(String gameId) {
        return sendAndWait(RequestMessage.of("acceptDraw").with("gameId", gameId));
    }

    public CompletableFuture<StatusMessage> declineDraw(String gameId) {
        return sendAndWait(RequestMessage.of("declineDraw").with("gameId", gameId));
    }
}
