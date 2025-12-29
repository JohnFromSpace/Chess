package com.example.chess.server;

import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.*;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.fs.ServerStateStore;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;

public class ServerMain {

    public static void main(String[] args) {
        int port = 5000;

        Path dataDir = Path.of("data");
        FileStores stores = new FileStores(dataDir);

        ServerStateStore stateStore = new ServerStateStore(dataDir);
        long lastDownAtMs = stateStore.estimateLastDownAtMs(stateStore.read());

        String instanceId = UUID.randomUUID().toString();
        ServerHeartbeatService heartbeat = new ServerHeartbeatService(stateStore, instanceId);
        heartbeat.start();

        UserRepository userRepo = new UserRepository(stores);
        GameRepository gameRepo = stores;

        StatsService stats = new StatsService(gameRepo);
        ClockService clocks = new ClockService();

        StatsAndRatingService statsAndElo = new StatsAndRatingService(userRepo);
        MoveService moves = new MoveService(gameRepo, clocks, statsAndElo);

        moves.recoverOngoingGames(stores.loadAllGames(), lastDownAtMs);

        MatchmakingService matchmaking = new MatchmakingService(moves, clocks);
        OnlineUserRegistry online = new OnlineUserRegistry();

        GameCoordinator coordinator = new GameCoordinator(matchmaking, moves, stats, online);
        AuthService auth = new AuthService(userRepo);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                heartbeat.close();
            } catch (Exception e) {
                System.err.println("Failed to close heartbeat: " + e.getMessage());
            }
            try {
                heartbeat.markGracefulShutdown();
            } catch (Exception e) {
                System.err.println("Failed to shut down gracefully: " + e.getMessage());
            }
        }, "shutdown-hook"));

        System.out.println("Chess server starting on port " + port + " ...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();
                ClientHandler handler = new ClientHandler(client, auth, coordinator, moves);
                Thread t = new Thread(handler, "Client-" + client.getPort());
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}