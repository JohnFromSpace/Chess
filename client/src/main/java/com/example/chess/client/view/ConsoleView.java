package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.Scanner;

public class ConsoleView {

    private final Scanner in;
    private final PrintStream out;

    public ConsoleView(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
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

    public void showMove(String move, boolean whiteInCheck, boolean blackInCheck) {
        out.printf("Move played: %s%n", move);
        if (whiteInCheck) out.println("White is in check.");
        if (blackInCheck) out.println("Black is in check.");
    }

    public void showGameOver(String result, String reason) {
        out.printf("Game over: %s (%s)%n", result, reason);
    }

    public int askInt(String prompt) {
        while (true) {
            out.print(prompt);
            String line = in.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.println("Please enter a number.");
            }
        }
    }
}