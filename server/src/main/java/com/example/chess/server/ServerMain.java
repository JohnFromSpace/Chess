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
import com.example.chess.server.util.PrometheusMetricsServer;
import com.example.chess.server.security.Tls;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.SocketException;
import java.util.concurrent.*;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        Log.init();

        ServerConfig config = ServerConfig.load();
        FileStores stores = new FileStores(config.dataDir);

        ServerStateStore stateStore = new ServerStateStore(config.dataDir);
        ServerState prevState = readServerState(stateStore);
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
            PrometheusMetricsServer prometheus = new PrometheusMetricsServer(metrics);
            prometheus.start();

            GameCoordinator coordinator = new GameCoordinator(matchmaking, moves, stats, online);
            AuthService auth = new AuthService(userRepo);

            ServerSocket serverSocket = createServerSocket(config.port, config.tls, config.tlsClientAuth);
            ThreadPoolExecutor clientPool = createClientPool(config.coreThreads, config.maxThreads, config.queueCapacity);

            AtomicBoolean running = new AtomicBoolean(true);

            String instanceId = java.util.UUID.randomUUID().toString();

            ServerHeartbeatService heartBeat = startHeartbeat(stateStore, instanceId);
            registerShutdownHook(running, serverSocket, clientPool, heartBeat, metricsReporter, prometheus);

            Log.info("Chess server starting on port: " + config.port + " ...");
            config.logSummary();

            acceptLoop(running, serverSocket, clientPool, auth, coordinator, moves, metrics);
        }
    }

    private static ServerState readServerState(ServerStateStore stateStore) {
        try {
            return stateStore.read();
        } catch (RuntimeException e) {
            Log.warn("Failed to read server state, starting fresh.", e);
            return null;
        }
    }

    private static ServerSocket createServerSocket(int port, boolean tls, boolean needClientAuth) throws IOException {
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
        if (serverSocket instanceof SSLServerSocket ssl) {
            ssl.setNeedClientAuth(needClientAuth);
        }
        return serverSocket;
    }

    private static ThreadPoolExecutor createClientPool(int core, int max, int queueCap) {
        return new ThreadPoolExecutor(
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
    }

    private static ServerHeartbeatService startHeartbeat(ServerStateStore stateStore, String instanceId) {
        ServerHeartbeatService heartBeat = new ServerHeartbeatService(stateStore, instanceId);
        heartBeat.start();
        return heartBeat;
    }

    private static void registerShutdownHook(AtomicBoolean running,
                                             ServerSocket serverSocket,
                                             ThreadPoolExecutor clientPool,
                                             ServerHeartbeatService heartBeat,
                                             ServerMetricsReporter metricsReporter,
                                             PrometheusMetricsServer prometheus) {
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
            try {
                prometheus.close();
            } catch (RuntimeException e) {
                Log.warn("Failed to stop Prometheus metrics server.", e);
            }
            Log.shutdown();
        }, "server.shutdown"));
    }

    private static void acceptLoop(AtomicBoolean running,
                                   ServerSocket serverSocket,
                                   ThreadPoolExecutor clientPool,
                                   AuthService auth,
                                   GameCoordinator coordinator,
                                   MoveService moves,
                                   ServerMetrics metrics) throws IOException {
        while (running.get()) {
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
                if (running.get()) {
                    Log.warn("Server socket error in accept().", se);
                }
                break;
            } catch (IOException ioException) {
                Log.warn("I/O error in accept().", ioException);
            }
        }
    }

}
