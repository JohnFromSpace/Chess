package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.List;
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

    public void showBoard(String boardText) {
        if (boardText == null || boardText.isBlank()) {
            out.println("(no board)");
            return;
        }
        out.println(boardText);
    }

    public void clearScreen() {
        out.print("\u001B[2J\u001B[H");
        out.flush();
    }

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp) {
        boardText = toUnicodeBoardText(boardText);

        String[] b = boardText.split("\n", -1);

        int width = 0;
        for (String line : b) width = Math.max(width, line.length());

        List<String> side = new java.util.ArrayList<>();
        side.add("Captured by YOU: " + renderPieces(capturedByYou));
        side.add("Captured by OPP: " + renderPieces(capturedByOpp));
        side.add("Promotion: q/r/b/n (not limited by captured pieces)");

        int rows = Math.max(b.length, side.size());
        for (int i = 0; i < rows; i++) {
            String left = (i < b.length) ? b[i] : "";
            String right = (i < side.size()) ? side.get(i) : "";
            out.printf("%-" + width + "s   %s%n", left, right);
        }
    }

    private static String renderPieces(List<String> pcs) {
        if (pcs == null || pcs.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : pcs) {
            if (s == null || s.isBlank()) continue;
            char c = s.charAt(0);
            sb.append(toUnicode(c)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String toUnicode(char c) {
        return switch (c) {
            case 'P' -> "\u2659";
            case 'N' -> "\u2658";
            case 'B' -> "\u2657";
            case 'R' -> "\u2656";
            case 'Q' -> "\u2655";
            case 'K' -> "\u2654";
            case 'p' -> "\u265F";
            case 'n' -> "\u265E";
            case 'b' -> "\u265D";
            case 'r' -> "\u265C";
            case 'q' -> "\u265B";
            case 'k' -> "\u265A";
            default  -> String.valueOf(c);
        };
    }

    private String toUnicodePrettyBoard(String ascii) {
        StringBuilder out = new StringBuilder();
        for (String line : ascii.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (!trimmed.isEmpty() && Character.isDigit(trimmed.charAt(0))) {
                String[] t = trimmed.split("\\s+");
                out.append(String.format("%2s ", t[0]));
                for (int i = 1; i <= 8; i++) {
                    char pc = t[i].charAt(0);
                    out.append(String.format("%2s ", mapPieceCharToUnicode(pc)));
                }
                out.append(String.format("%2s", t[9]));
            }
            else if (trimmed.startsWith("a ")) {
                out.append("   a  b  c  d  e  f  g  h");
            }
            else {
                out.append(line);
            }
            out.append('\n');
        }
        return out.toString();
    }
}