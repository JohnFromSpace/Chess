package com.example.chess.client;

import com.example.chess.common.GameModels.Board;
import com.example.chess.common.GameModels.Move;
import com.example.chess.client.net.ClientConnection;
import com.example.chess.common.Msg;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {
    private static String currentGameId = null;
    private static String myColor = null;
    private static Board currentBoard = null;

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
            System.out.println("10) View my statistics");
            System.out.println("11) List my games");
            System.out.println("12) View game details & replay");
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
                    case "10" -> doGetStats(conn);
                    case "11" -> doListGames(conn);
                    case "12" -> doViewGameDetails(conn, scanner);
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

            currentBoard = new Board();

            System.out.println("\n[SERVER] Game started! gameId= " + currentGameId + ", you are " +
                    myColor + ", opponent= " +
                    msg.get("opponent").getAsString());

            printBoard(currentBoard);
        } else if("gameOver".equals(type)) {
            System.out.println("\n[SERVER] Game over: " + msg);

            currentGameId = null;
            myColor = null;
        } else if("drawOffered".equals(type)) {
            System.out.println("\n[SERVER] Draw offered by: " + msg.get("from").getAsString());
            System.out.println("Use 7) Accept draw or 8) Decline draw.");

        } else if("move".equals(type)) {
            handleMoveMessage(msg);
        } else if ("getStatsOk".equals(type)) {
            handleStatsMessage(msg);
        } else if("listGamesOk".equals(type)) {
            handleGamesListMessage(msg);
        } else if("gameDetailsOk".equals(type)) {
            handleGameDetailsMessage(msg);
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

    private static void printBoard(Board board) {
        if(board == null) {
            System.out.println("(empty board)");
            return;
        }

        System.out.println();
        for(int row = 0; row < 8; row++) {
            int rank = 8 - row;
            System.out.println(rank + " ");
            for(int col = 0; col < 8; col++) {
                char piece = board.get(row, col);
                if(piece == 0) {
                    piece = '.';
                }
                System.out.println(piece + " ");
            }
            System.out.println();
        }

        System.out.println("    a b c d e f g h");
        System.out.println();
    }

    private static void handleMoveMessage(JsonObject msg) {
        System.out.println("\n[SERVER] Move: " + msg);

        if(currentBoard == null) {
            return;
        }

        if(!msg.has("move")) {
            return;
        }

        String moveStr = msg.get("move").getAsString();

        try {
            Move m = Move.parse(moveStr);

            char piece = currentBoard.get(m.fromRow, m.fromCol);
            if(piece == 0 || piece == '.') {
                System.out.println("(warning: on piece in source square locally)");
                return;
            }

            currentBoard.set(m.toRow, m.toCol, piece);
            currentBoard.set(m.fromRow, m.fromCol, '.');

            printBoard(currentBoard);

            if(myColor != null) {
                boolean whiteInCheck = msg.has("whiteInCheck") &&
                        msg.get("whiteInCheck").getAsBoolean();
                boolean blackInCheck = msg.has("blackInCheck") &&
                        msg.get("blackInCheck").getAsBoolean();

                boolean myKingInCheck = ("white".equals(myColor) && whiteInCheck) ||
                        ("black".equals(myColor) && blackInCheck);

                if(myKingInCheck) {
                    System.out.println(">>> YOUR KING IS IN CHECK.! <<<");
                } else if (whiteInCheck || blackInCheck) {
                    System.out.println(">>> OPPONENT'S KING IS IN CHEKC! <<<");
                }
            }
        } catch (Exception e) {
            System.out.println("(failed to apply locally: " + e.getMessage() + ")");
        }
    }

    private static void doGetStats(ClientConnection conn) throws IOException {
        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("getStats", corrId);
        conn.send(msg);
        System.out.println("Requested stats (corrId=" + corrId + ").");
    }

    private static void doListGames(ClientConnection conn) throws IOException {
        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("listGames", corrId);
        conn.send(msg);
        System.out.println("Requested games list (corrId=" + corrId + ").");
    }

    private static void doViewGameDetails(ClientConnection conn, Scanner scanner) throws IOException {
        System.out.print("Enter gameId to inspect: ");
        String gameId = scanner.nextLine().trim();
        if (gameId.isEmpty()) {
            System.out.println("GameId cannot be empty.");
            return;
        }

        String corrId = conn.nextCorrId();
        JsonObject msg = Msg.obj("getGameDetails", corrId);
        msg.addProperty("gameId", gameId);
        conn.send(msg);
        System.out.println("Requested game details (corrId=" + corrId + ").");
    }

    private static void handleStatsMessage(JsonObject msg) {
        System.out.println("\n=== Your Statistics ===");
        JsonObject u = msg.getAsJsonObject("user");
        if (u == null) {
            System.out.println("(no user object)");
            return;
        }
        String username = u.get("username").getAsString();
        String name = u.get("name").getAsString();
        int played = u.get("played").getAsInt();
        int won = u.get("won").getAsInt();
        int drawn = u.has("drawn") ? u.get("drawn").getAsInt() : 0;
        int rating = u.get("rating").getAsInt();

        System.out.println("User:    " + username + " (" + name + ")");
        System.out.println("Played:  " + played);
        System.out.println("Won:     " + won);
        System.out.println("Drawn:   " + drawn);
        System.out.println("Lost:    " + (played - won - drawn));
        System.out.println("Rating:  " + rating);
        System.out.println("=======================");
    }

    private static void handleGamesListMessage(JsonObject msg) {
        System.out.println("\n=== Your Games ===");
        if (!msg.has("games")) {
            System.out.println("(no games)");
            return;
        }
        var arr = msg.getAsJsonArray("games");
        if (arr.size() == 0) {
            System.out.println("(no games yet)");
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject g = arr.get(i).getAsJsonObject();
            String id = g.get("id").getAsString();
            long createdAt = g.get("createdAt").getAsLong();
            String opponent = g.get("opponent").getAsString();
            String color = g.get("color").getAsString();
            String result = g.get("result").getAsString();
            String reason = g.has("reason") && !g.get("reason").isJsonNull()
                    ? g.get("reason").getAsString() : "";

            System.out.printf("%2d) %s vs %s (%s)  result=%s  reason=%s  createdAt=%d%n",
                    i + 1, color.equals("white") ? "You" : opponent,
                    color.equals("white") ? opponent : "You",
                    color,
                    result,
                    reason,
                    createdAt);
            System.out.println("    id=" + id);
        }
        System.out.println("==================");
    }

    private static void handleGameDetailsMessage(JsonObject msg) {
        System.out.println("\n=== Game Details ===");
        JsonObject g = msg.getAsJsonObject("game");
        if (g == null) {
            System.out.println("(no game object)");
            return;
        }
        String id = g.get("id").getAsString();
        String whiteUser = g.get("whiteUser").getAsString();
        String blackUser = g.get("blackUser").getAsString();
        String result = g.get("result").getAsString();
        String reason = g.has("reason") ? g.get("reason").getAsString() : "";
        long createdAt = g.get("createdAt").getAsLong();

        System.out.println("Id:      " + id);
        System.out.println("White:   " + whiteUser);
        System.out.println("Black:   " + blackUser);
        System.out.println("Result:  " + result + " (" + reason + ")");
        System.out.println("Created: " + createdAt);

        var movesArr = g.getAsJsonArray("moves");
        System.out.println("\nMoves:");
        Board replayBoard = new Board();
        if (movesArr != null) {
            for (int i = 0; i < movesArr.size(); i++) {
                String moveStr = movesArr.get(i).getAsString();
                System.out.printf("%3d. %s%n", i + 1, moveStr);

                try {
                    Move m = Move.parse(moveStr);
                    char piece = replayBoard.get(m.fromRow, m.fromCol);
                    if (piece != 0 && piece != '.') {
                        replayBoard.set(m.toRow, m.toCol, piece);
                        replayBoard.set(m.fromRow, m.fromCol, '.');
                    }
                } catch (Exception e) {
                    System.out.println("   (failed to apply move locally: " + e.getMessage() + ")");
                }
            }
        }

        System.out.println("\nFinal position:");
        printBoard(replayBoard);
        System.out.println("====================");
    }
}