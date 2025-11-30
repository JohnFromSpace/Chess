package com.example.chess.server;

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
        FileStores fileStores = new FileStores(dataDir);

        UserRepository userRepository = fileStores;
        GameRepository gameRepository = fileStores;

        AuthService authService = new AuthService(userRepository);
        GameCoordinator gameCoordinator = new GameCoordinator(userRepository, gameRepository);

        System.out.println("Chess server starting on port " + port + " ...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("Client connected: " + client.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(client, authService, gameCoordinator);
                Thread t = new Thread(handler, "Client-" + client.getPort());
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}

