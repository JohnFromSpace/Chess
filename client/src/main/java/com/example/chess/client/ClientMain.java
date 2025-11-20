package com.example.chess.client;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.common.Msg;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {
    private static String currentGameId = null;
    private static String myColor = null;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        System.out.println("Connecting to chess server at " + host + ":" + port + " ...");

        try (ClientConnection conn = new ClientConnection(host, port)) {
            conn.setOnMessage(ClientMain::handleAsyncMessage);
            conn.connect();
            System.out.println("Connected.");

            runMainMenu(conn);

        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
        }
    }

    private static void runMainMenu(ClientConnection conn) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("=== Chess Client ===");
            System.out.println("1) Register");
            System.out.println("2) Login");
            System.out.println("3) Ping server");
            System.out.println("4) Request game");
            System.out.println("5) Make move");
            System.out.println("6) Offer draw");
            System.out.println("7) Accept draw");
            System.out.println("8) Decline draw");
            System.out.println("9) Resign");
            System.out.println("0) Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> doRegister(conn, scanner);
                    case "2" -> doLogin(conn, scanner);
                    case "3" -> doPing(conn);
                    case "4" -> doRequestGame(conn);
                    case "5" -> doMakeMove(conn, scanner);
                    case "6" -> doOfferDraw(conn);
                    case "7" -> doRespondDraw(conn, true);
                    case "8" -> doRespondDraw(conn, false);
                    case "9" -> doResign(conn);
                    case "0" -> {
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (IOException e) {
                System.err.println("Network error: " + e.getMessage());
                return;
            }
        }
    }

    private static void doRegister(ClientConnection conn, Scanner scanner) throws IOException {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("register", corrId);
        msg.addProperty("username", username);
        msg.addProperty("name", name);
        msg.addProperty("password", password);
        conn.send(msg);

        System.out.println("Register request sent (corrId=" + corrId + ").");
        System.out.println("Check server response in the console.");
    }

    private static void doLogin(ClientConnection conn, Scanner scanner) throws IOException {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("login", corrId);
        msg.addProperty("username", username);
        msg.addProperty("password", password);
        conn.send(msg);

        System.out.println("Login request sent (corrId=" + corrId + ").");
    }

    private static void doPing(ClientConnection conn) throws IOException {
        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("ping", corrId);
        conn.send(msg);
        System.out.println("Ping sent (corrId=" + corrId + ").");
    }

    private static void doRequestGame(ClientConnection conn) throws IOException {
         String corrId = conn.nextCorrId();
         JsonObject msg = Msg.obj("requestGame", corrId);
         conn.send(msg);
         System.out.println("Requesting a game (corrId= " + corrId + ").");
    }

    private static void handleAsyncMessage(JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : "(no type)";

        if("gameStarted".equals(type)) {
            currentGameId = msg.get("gameId").getAsString();
            myColor = msg.get("color").getAsString();
            System.out.println("\n[SERVER] Game started! gameId= " + currentGameId + ", you are " +
                    myColor + ", opponent= " +
                    msg.get("opponent").getAsString());
        } else if("gameOver".equals(type)) {
            System.out.println("\n[SEREVER] Game over: " + msg);
            currentGameId = null;
            myColor = null;
        } else if("drawOffered".equals(type)) {
            System.out.println("\n[SERVER] Draw offered by: " + msg.get("from").getAsString());
            System.out.println("Use 7) Accept draw or 8) Decline draw.");
        } else if("move".equals(type)) {
            System.out.println("\n[SERVER] Move: " + msg);
        } else {
            System.out.println("\n[SERVER] " + type + ": " + msg);
        }

        System.out.print("> ");
    }

    private static void doMakeMove(ClientConnection conn, Scanner scanner) throws IOException {
        if (currentGameId == null) {
            System.out.println("You are not in a game.");
            return;
        }
        System.out.print("Enter move (e.g., e2e4): ");
        String move = scanner.nextLine().trim();

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("move", corrId);
        msg.addProperty("gameId", currentGameId);
        msg.addProperty("move", move);
        conn.send(msg);

        System.out.println("Move sent (corrId=" + corrId + ").");
    }

    private static void doOfferDraw(ClientConnection conn) throws IOException {
        if (currentGameId == null) {
            System.out.println("You are not in a game.");
            return;
        }

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("offerDraw", corrId);
        msg.addProperty("gameId", currentGameId);
        conn.send(msg);

        System.out.println("Draw offer sent.");
    }

    private static void doRespondDraw(ClientConnection conn, boolean accepted) throws IOException {
        if (currentGameId == null) {
            System.out.println("You are not in a game.");
            return;
        }

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("respondDraw", corrId);
        msg.addProperty("gameId", currentGameId);
        msg.addProperty("accepted", accepted);
        conn.send(msg);

        System.out.println(accepted ? "Draw accepted." : "Draw declined.");
    }

    private static void doResign(ClientConnection conn) throws IOException {
        if (currentGameId == null) {
            System.out.println("You are not in a game.");
            return;
        }

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("resign", corrId);
        msg.addProperty("gameId", currentGameId);
        conn.send(msg);

        System.out.println("Resigned.");
    }
}

