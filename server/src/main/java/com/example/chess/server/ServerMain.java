package com.example.chess.server;

import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.*;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.fs.ServerState;
import com.example.chess.server.fs.ServerStateStore;
import com.example.chess.server.fs.repository.UserRepository;
import com.example.chess.server.util.Log;
import com.example.chess.server.util.ServerMetrics;
import com.example.chess.server.util.ServerMetricsReporter;
import com.example.chess.server.security.Tls;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

import javax.net.ssl.SSLServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.SocketException;
import java.util.concurrent.*;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        Log.init();
        int port = parseInt("chess.server.port", 5000);

        Path dataDir = Path.of(System.getProperty("chess.data.dir", "data"));
        FileStores stores = new FileStores(dataDir);

        ServerStateStore stateStore = new ServerStateStore(dataDir);
        ServerState prevState;
        try {
            prevState = stateStore.read();
        } catch (RuntimeException e) {
            Log.warn("Failed to read server state, starting fresh.", e);
            prevState = null;
        }
        long lastDownAtMs = stateStore.estimateLastDownAtMs(prevState);

        UserRepository userRepo = new UserRepository(stores);

        StatsService stats = new StatsService(stores);
        ClockService clocks = new ClockService();

        StatsAndRatingService statsAndElo = new StatsAndRatingService(userRepo);
        try (MoveService moves = new MoveService(stores, clocks, statsAndElo)) {

            moves.recoverOngoingGames(stores.loadAllGames(), lastDownAtMs);

            MatchmakingService matchmaking = new MatchmakingService(moves);
            OnlineUserRegistry online = new OnlineUserRegistry();
            ServerMetrics metrics = new ServerMetrics(online::onlineCount, matchmaking::queueSize, moves::activeGameCount);
            ServerMetricsReporter metricsReporter = new ServerMetricsReporter(metrics);
            metricsReporter.start();

            GameCoordinator coordinator = new GameCoordinator(matchmaking, moves, stats, online);
            AuthService auth = new AuthService(userRepo);

            boolean tls = parseBoolean("chess.tls.enabled", false);
            ServerSocket serverSocket;
            if (tls) {
                try {
                    serverSocket = Tls.createServerSocket(port);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to init TLS server socket", e);
                }
            } else {
                serverSocket = new ServerSocket(port);
            }
            if(serverSocket instanceof SSLServerSocket ssl) {
                boolean needClientAuth = parseBoolean("chess.tls.clientAuth", false);
                ssl.setNeedClientAuth(needClientAuth);
            }

            int core     = parseInt("chess.server.threads.core", 8);
            int max      = parseInt("chess.server.threads.max", 64);
            int queueCap = parseInt("chess.server.queue.capacity", 256);
            int maxLineChars = parseInt("chess.socket.maxLineChars", 16384);
            int readTimeoutMs = parseInt("chess.socket.readTimeoutMs", 60000);

            ThreadPoolExecutor clientPool = new ThreadPoolExecutor(
                    core,
                    max,
                    60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(queueCap),
                    r -> {
                        Thread t = new Thread(r, "client-handler");
                        t.setDaemon(false);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );

            AtomicBoolean running = new AtomicBoolean(true);

            String instanceId = java.util.UUID.randomUUID().toString();

            ServerHeartbeatService heartBeat = new ServerHeartbeatService(stateStore, instanceId);
            heartBeat.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
               running.set(false);

               try {
                   serverSocket.close();
               } catch (IOException e) {
                   throw new RuntimeException("Failed to close server socket.", e);
               }

               clientPool.shutdown();
               try {
                   heartBeat.markGracefulShutdown();
               } catch (RuntimeException e) {
                   throw new RuntimeException("Failed to close heartbeat.", e);
               }
               try {
                   heartBeat.close();
               } catch (RuntimeException e) {
                   Log.warn("Failed to stop heartbeat scheduler.", e);
               }
               try {
                   metricsReporter.close();
               } catch (RuntimeException e) {
                   Log.warn("Failed to stop metrics reporter.", e);
               }
               Log.shutdown();
            }, "server.shutdown"));

            Log.info("Chess server starting on port: " + port + " ...");
            logConfigSummary(dataDir, port, tls, core, max, queueCap, maxLineChars, readTimeoutMs);

            while(running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setKeepAlive(true);

                    try {
                        clientPool.execute(new ClientHandler(clientSocket, auth, coordinator, moves, metrics));
                    } catch (RejectedExecutionException rex) {
                        try {
                            clientSocket.close();
                        } catch (RuntimeException e) {
                            throw new RuntimeException("Failed to close socket.", e);
                        }
                        Log.warn("Rejected client connection server overloaded.", rex);
                    }
                } catch (SocketException se) {
                    if(running.get()) {
                        Log.warn("Server socket error in accept().", se);
                    }
                    break;
                } catch (IOException ioException) {
                    Log.warn("I/O error in accept().", ioException);
                }
            }
        }
    }

    private static int parseInt(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Log.warn("Invalid integer for " + key + ": " + raw + " (using default " + defaultValue + ")", e);
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String key, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        return Boolean.parseBoolean(raw.trim());
    }

    private static void logConfigSummary(Path dataDir,
                                         int port,
                                         boolean tls,
                                         int core,
                                         int max,
                                         int queueCap,
                                         int maxLineChars,
                                         int readTimeoutMs) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Config summary: ");
        sb.append("dataDir=").append(dataDir.toAbsolutePath());
        sb.append(", port=").append(port);
        sb.append(", tls=").append(tls);
        sb.append(", threads=").append(core).append("/").append(max);
        sb.append(", queueCap=").append(queueCap);
        sb.append(", socket.maxLineChars=").append(maxLineChars);
        sb.append(", socket.readTimeoutMs=").append(readTimeoutMs);
        Log.info(sb.toString());
    }
}
