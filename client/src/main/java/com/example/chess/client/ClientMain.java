package com.example.chess.client;

import com.example.chess.client.controller.ClientController;
import com.example.chess.client.model.ClientModel;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleView;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        ClientModel model = new ClientModel();
        ConsoleView view = new ConsoleView(new Scanner(System.in), System.out);

        try {
            // Create connection with no listener yet
            ClientConnection connection = new ClientConnection(host, port, null);

            // Controller will act as the listener (implements ClientMessageListener)
            ClientController controller = new ClientController(model, view, connection);

            // IMPORTANT: add this method in ClientConnection if you don't have it:
            // public void setListener(ClientMessageListener listener) { this.listener = listener; }
            connection.setListener(controller);

            // Start network loop and then run the UI loop
            connection.start();
            controller.run();

        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
    }
}

