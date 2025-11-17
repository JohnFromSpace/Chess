package com.example.chess.server;

import com.example.chess.common.GameModels;
import com.example.chess.server.fs.FileStores;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class ServerMain {

    public static void main(String[] args) {
        int port = 5000;
        Path dataDir = Path.of("data");

        FileStores fileStores = new FileStores(dataDir);
        AuthService authService = new AuthService(fileStores);
        GameCoordinator gameCoordinator = new GameCoordinator(fileStores);

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

