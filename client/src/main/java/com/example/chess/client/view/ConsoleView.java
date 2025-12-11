package com.example.chess.client.view;

import com.example.chess.common.GameModels.Board;

import java.io.PrintStream;
import java.util.Scanner;

public class ConsoleView {

    private final Scanner in;
    private final PrintStream out;
    private Scanner scanner;

    public ConsoleView(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public int showMainMenu(boolean loggedIn, boolean hasActiveGame) {
        out.println();
        out.println("=== Chess ===");
        if (!loggedIn) {
            out.println("1) Register");
            out.println("2) Login");
            out.println("0) Exit");
        } else if (!hasActiveGame) {
            out.println("1) Request game");
            out.println("2) Show my stats");
            out.println("3) List my games");
            out.println("4) Replay game");
            out.println("0) Logout");
        } else {
            out.println("1) Make move");
            out.println("2) Offer draw");
            out.println("3) Resign");
            out.println("0) Leave game (client side only)");
        }
        out.print("Choice: ");
        while (true) {
            String line = in.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.print("Enter a number: ");
            }
        }
    }

    public String askLine(String prompt) {
        out.print(prompt);
        return in.nextLine();
    }

    public void showMessage(String msg) {
        out.println(msg);
    }

    public void showError(String msg) {
        out.println("[ERROR] " + msg);
    }

    public void showGameStarted(String color, String opponent) {
        out.printf("Game started. You are %s vs %s.%n", color, opponent);
    }

    public void showMove(String move, boolean whiteInCheck, boolean blackInCheck) {
        out.printf("Move played: %s%n", move);
        if (whiteInCheck) {
            out.println("White is in check.");
        }
        if (blackInCheck) {
            out.println("Black is in check.");
        }
    }

    public void showGameOver(String result, String reason) {
        out.printf("Game over: %s (%s)%n", result, reason);
    }

    public void showDrawOffered(String from) {
        out.printf("%s offered a draw.%n", from);
    }

    public int askInt(String string) {
        while (true) {
            System.out.println(string);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }
}