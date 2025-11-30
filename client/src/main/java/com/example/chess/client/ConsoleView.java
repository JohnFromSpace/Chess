package com.example.chess.client;

import com.example.chess.common.GameModels.Board;

import java.util.Scanner;

public class ConsoleView {

    private final Scanner scanner = new Scanner(System.in);

    public void showWelcome() {
        System.out.println("=== Chess Client ===");
    }

    public void showMainMenu() {
        System.out.println();
        System.out.println("1) Register");
        System.out.println("2) Login");
        System.out.println("3) View my statistics");
        System.out.println("4) Request game");
        System.out.println("5) Offer draw");
        System.out.println("6) Resign");
        System.out.println("7) List my games");
        System.out.println("8) Replay a game");
        System.out.println("0) Exit");
        System.out.print("Choice: ");
    }

    public int readInt() {
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException ex) {
                System.out.print("Please enter a number: ");
            }
        }
    }

    public String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    public void showError(String message) {
        System.out.println();
        System.out.println(">>> ERROR: " + message);
        System.out.println();
    }

    public void showInfo(String message) {
        System.out.println();
        System.out.println(message);
        System.out.println();
    }

    public void showGameStarted(String color, String opponent) {
        System.out.println();
        System.out.println("=== New game started ===");
        System.out.println("You are: " + color);
        System.out.println("Opponent: " + opponent);
        System.out.println();
    }

    public void showMove(String move, boolean whiteInCheck, boolean blackInCheck) {
        System.out.println("Move: " + move);
        if (whiteInCheck || blackInCheck) {
            System.out.println("Check!");
        }
    }

    public void showGameOver(String result, String reason) {
        System.out.println();
        System.out.println("=== Game over ===");
        System.out.println("Result: " + result + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")"));
        System.out.println();
    }

    public void showDrawOffered(String from) {
        System.out.println();
        System.out.println("Opponent " + from + " has offered a draw.");
        System.out.println("Use menu to accept or decline.");
        System.out.println();
    }

    public void printBoard(Board board) {
        if (board == null) {
            System.out.println("[No board loaded]");
            return;
        }
        System.out.println("   +------------------------+");
        for (int row = 0; row < 8; row++) {
            System.out.print((8 - row) + "  | ");
            for (int col = 0; col < 8; col++) {
                char p = board.squares[row][col];
                if (p == 0) p = '.';
                System.out.print(p + " ");
            }
            System.out.println("|");
        }
        System.out.println("   +------------------------+");
        System.out.println("      a b c d e f g h");
    }
}

