package com.example.chess.client.net;

import com.example.chess.common.message.MessageCodec;
import com.example.chess.common.message.Message;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.common.message.StatusMessage;

import java.io.*;
import java.net.Socket;
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

    public void start() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

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
                    if (resp.corrId != null) {
                        CompletableFuture<StatusMessage> fut = pending.remove(resp.corrId);
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
            pending.values().forEach(f -> f.completeExceptionally(e));
            pending.clear();
        } finally {
            // ensure resources are gone
            try { close(); } catch (Exception exception) {
                System.err.println("Failed to release resources: " + exception.getMessage());
            }
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
            if (!isOpen()) throw new IOException("Connection closed.");
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

    @Override
    public void close() {
        // complete any waiters
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Connection closed.")));
        pending.clear();

        // close streams/socket
        try { if (in != null) in.close(); } catch (Exception e) {System.err.println("Failed to close input stream: " + e.getMessage());}
        try { if (out != null) out.close(); } catch (Exception e) {System.err.println("Failed to close output stream: " + e.getMessage());}
        try { if (socket != null) socket.close(); } catch (Exception e) {System.err.println("Failed to close current socket: " + e.getMessage());}

        // stop reader thread if needed
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception e) {System.err.println("Failed to interrupt reader (thread): " + e.getMessage());}
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