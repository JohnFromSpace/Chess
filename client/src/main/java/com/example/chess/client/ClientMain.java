package com.example.chess.client;

import com.example.chess.client.controller.ClientController;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.view.ConsoleInput;
import com.example.chess.client.view.ConsoleView;
import com.example.chess.client.util.Log;

public class ClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        ConsoleInput input = new ConsoleInput(System.in);
        ConsoleView view = new ConsoleView(input, System.out);

        ClientConnection connection = null;
        ClientController controller = null;

        try {
            connection = new ClientConnection(host, port);
            connection.start();

            controller = new ClientController(connection, view);

            ClientController finalController = controller;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    finalController.shutdownGracefully();
                }
                finally {
                    try {
                        input.close();
                    }
                    catch (Exception ex) {
                        Log.warn("Failed to close stream.", ex);
                    }
                }
            }, "client-shutdown"));
            controller.run();
        } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
           Log.warn("Client interrupted", null);
        } catch (Exception e) {
            Log.warn("Failed to start client.", e);
        } finally {
            if(controller != null) {
                controller.shutdownGracefully();
            } else if(connection != null) {
                connection.close();
            }
        }
        try {
            input.close();
        } catch (Exception ex) {
            Log.warn("Failed to close stream.", ex);
        }
    }
}
