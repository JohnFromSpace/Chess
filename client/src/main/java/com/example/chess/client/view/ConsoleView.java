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

    public int askInt(String prompt, int min, int max) {
        while (true) {
            out.print(prompt);
            String s = in.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) throw new NumberFormatException();
                return v;
            } catch (NumberFormatException e) {
                out.println("Please enter a number between " + min + " and " + max + ".");
            }
        }
    }

    public String askLine(String prompt) {
        out.print(prompt);
        return in.nextLine();
    }

    public void println(String s) { out.println(s); }

    public void showBoard(String boardText) {
        out.println(renderBoard(boardText, true));
    }

    public void showBoard(String boardText, boolean whitePerspective) {
        out.println(renderBoard(boardText, whitePerspective));
    }

    public void showBoardWithCaptured(String boardText,
                                      boolean whitePerspective,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp) {

        String rendered = renderBoard(boardText, whitePerspective);
        String[] b = rendered.split("\\R");

        List<String> side = new ArrayList<>();
        side.add("Captured by YOU: " + renderPieces(capturedByYou));
        side.add("Captured by OPP: " + renderPieces(capturedByOpp));
        side.add("Promotion: q/r/b/n (not limited by captured pieces)");

        int width = 0;
        for (String line : b) width = Math.max(width, line.length());

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
            sb.append(pieceToUnicode(s.charAt(0))).append(' ');
        }
        return sb.toString().trim();
    }

    private static String pieceToUnicode(char c) {
        return switch (c) {
            case 'K' -> "\u2654"; case 'Q' -> "\u2655"; case 'R' -> "\u2656";
            case 'B' -> "\u2657"; case 'N' -> "\u2658"; case 'P' -> "\u2659";
            case 'k' -> "\u265A"; case 'q' -> "\u265B"; case 'r' -> "\u265C";
            case 'b' -> "\u265D"; case 'n' -> "\u265E"; case 'p' -> "\u265F";
            default -> String.valueOf(c);
        };
    }

    private static String emptySquareSymbol(int rank, int fileIndex) {
        boolean dark = ((rank + fileIndex) % 2 == 1);
        return dark ? "\u2B1B" : "\u2B1C";
    }

    private static char[][] tryParseAsciiBoard(String boardText) {
        if (boardText == null) return null;

        char[][] grid = new char[8][8];
        int found = 0;

        for (String raw : boardText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!Character.isDigit(line.charAt(0))) continue;

            String[] t = line.split("\\s+");
            if (t.length < 10) continue;

            int rank;
            try {
                rank = Integer.parseInt(t[0]);
            } catch (Exception e) {
                continue;
            }
            if (rank < 1 || rank > 8) continue;

            for (int f = 0; f < 8; f++) {
                grid[8 - rank][f] = t[f + 1].charAt(0);
            }
            found++;
        }

        return found == 8 ? grid : null;
    }

    private static String renderBoard(String boardText, boolean isWhitePerspective) {
        char[][] grid = tryParseAsciiBoard(boardText);
        if (grid == null) return boardText;

        int[] files = isWhitePerspective
                ? new int[]{0,1,2,3,4,5,6,7}
                : new int[]{7,6,5,4,3,2,1,0};

        StringBuilder sb = new StringBuilder();

        sb.append("   ");
        for (int i = 0; i < 8; i++) {
            sb.append((char)('a' + files[i]));
            if (i < 7) sb.append("  ");
        }
        sb.append('\n');

        int startRank = isWhitePerspective ? 8 : 1;
        int endRank = isWhitePerspective ? 1 : 8;
        int step = isWhitePerspective ? -1 : 1;

        for (int rank = startRank; ; rank += step) {
            int row = 8 - rank;

            sb.append(rank).append("  ");
            for (int i = 0; i < 8; i++) {
                int file = files[i];
                char p = grid[row][file];
                sb.append(p == '.' ? emptySquareSymbol(rank, file) : pieceToUnicode(p));
                if (i < 7) sb.append(' ');
            }
            sb.append("  ").append(rank).append('\n');

            if (rank == endRank) break;
        }

        sb.append("   ");
        for (int i = 0; i < 8; i++) {
            sb.append((char)('a' + files[i]));
            if (i < 7) sb.append("  ");
        }
        sb.append('\n');

        return sb.toString();
    }
}