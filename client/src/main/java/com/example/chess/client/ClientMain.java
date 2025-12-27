package com.example.chess.client;

import com.example.chess.client.controller.ClientController;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        ConsoleView view = new ConsoleView(new Scanner(System.in), System.out);

        try {
            ClientConnection connection = new ClientConnection(host, port);
            connection.start();

            ClientController controller = new ClientController(connection, view);

            Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdownGracefully, "client-shutdown"));

            controller.run();

        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
    }
}