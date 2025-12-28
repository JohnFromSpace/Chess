package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleView {

    private final Scanner in;
    private final PrintStream out;

    public ConsoleView(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
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

    public String askLine(String prompt) {
        out.print(prompt);
        return in.nextLine();
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
        out.println(toUnicodePrettyBoard(boardText));
    }

    public void clearScreen() {
        out.print("\u001B[2J\u001B[H");
        out.flush();
    }

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp) {

        String pretty = (boardText == null) ? "" : toUnicodePrettyBoard(boardText);
        String[] b = pretty.split("\n", -1);

        int width = 0;
        for (String line : b) width = Math.max(width, line.length());

        List<String> side = new ArrayList<>();
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
            sb.append(toUnicode(s.charAt(0))).append(' ');
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "-" : out;
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
            default -> String.valueOf(c);
        };
    }

    private static String squareFor(int rank, int fileIdx) {
        boolean light = ((rank + fileIdx) % 2 == 0);
        return light ? "\u25A1" : "\u25A0"; // □ / ■
    }

    private static boolean isRankLine(String s) {
        if (s == null) return false;
        String t = s.stripLeading();
        if (t.isEmpty()) return false;
        char ch = t.charAt(0);
        if (ch < '1' || ch > '8') return false;
        String[] parts = t.split("\\s+");
        return parts.length >= 10 && parts[0].matches("[1-8]") && parts[parts.length - 1].matches("[1-8]");
    }

    private static String toUnicodePrettyBoard(String ascii) {
        if (ascii == null || ascii.isBlank()) return "";

        StringBuilder out = new StringBuilder(ascii.length() + 64);
        String[] lines = ascii.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("a b c d e f g h") || trimmed.startsWith("a b c")) {
                out.append("   a b c d e f g h\n");
                continue;
            }

            if (isRankLine(line)) {
                String t = line.stripLeading();
                String[] parts = t.split("\\s+");

                int rank = Integer.parseInt(parts[0]);

                out.append(rank).append("  ");
                for (int file = 0; file < 8; file++) {
                    String cell = parts[file + 1];

                    String rendered;
                    if (cell == null || cell.isBlank() || ".".equals(cell) ||
                            "⬜".equals(cell) || "⬛".equals(cell) ||
                            "□".equals(cell) || "■".equals(cell)) {

                        rendered = squareFor(rank, file);

                    } else if (cell.length() == 1 && "PNBRQKpnbrqk".indexOf(cell.charAt(0)) >= 0) {
                        rendered = toUnicode(cell.charAt(0));
                    } else {
                        rendered = cell;
                    }

                    out.append(rendered);
                    if (file != 7) out.append(' ');
                }
                out.append("  ").append(rank).append('\n');
                continue;
            }

            out.append(line).append('\n');
        }

        String s = out.toString();
        if (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        return s;
    }
}