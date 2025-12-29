package com.example.chess.client;

import com.example.chess.client.controller.ClientController;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleInput;
import com.example.chess.client.view.ConsoleView;

import java.io.IOException;

public class ClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        ConsoleInput input = new ConsoleInput(System.in);
        ConsoleView view = new ConsoleView(input, System.out);

        try {
            ClientConnection connection = new ClientConnection(host, port);
            connection.start();

            ClientController controller = new ClientController(connection, view);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    controller.shutdownGracefully();
                }
                finally {
                    try {
                        input.close();
                    }
                    catch (Exception ex) {
                        System.err.println("Failed to close stream: " + ex.getMessage());
                    }
                }
            }, "client-shutdown"));
            controller.run();
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            try { input.close(); } catch (Exception ex) {
                throw new RuntimeException("Failed to close input: " + ex);
            }
        }
    }
}
