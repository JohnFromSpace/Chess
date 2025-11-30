package com.example.chess.client;

import com.example.chess.common.GameModels.Board;

public class ClientMain {
    private static String currentGameId = null;
    private static String myColor = null;
    private static Board currentBoard = null;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        System.out.println("Connecting to chess server at " + host + ":" + port + " ...");

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        try {
            ChessClient client = new ChessClient(host, port);
            client.run();
        } catch (Exception e) {
            System.err.println("Failed to start client: " + e.getMessage());
        }
    }
}