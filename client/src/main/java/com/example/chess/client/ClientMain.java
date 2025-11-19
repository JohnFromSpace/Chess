package com.example.chess.client;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.common.Msg;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

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
            System.out.println("0) Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> doRegister(conn, scanner);
                    case "2" -> doLogin(conn, scanner);
                    case "3" -> doPing(conn);
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

    private static void handleAsyncMessage(JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : "(no type)";
        System.out.println("\n[SERVER] " + type + ": " + msg);
        System.out.print("> ");
    }
}

