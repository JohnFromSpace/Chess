package com.example.chess.server;

import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.*;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.fs.FileStores;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.fs.repository.UserRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class ServerMain {

    public static void main(String[] args) {
        int port = 5000;

        Path dataDir = Path.of("data");
        FileStores stores = new FileStores(dataDir);

        UserRepository userRepo = new UserRepository(stores);
        GameRepository gameRepo = stores;

        StatsService stats = new StatsService(gameRepo);
        ClockService clocks = new ClockService();
        MoveService moves = new MoveService(gameRepo, clocks);
        MatchmakingService matchmaking = new MatchmakingService(moves, clocks);
        OnlineUserRegistry online = new OnlineUserRegistry();

        GameCoordinator coordinator = new GameCoordinator(matchmaking, moves, stats, online);
        AuthService auth = new AuthService(userRepo);

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