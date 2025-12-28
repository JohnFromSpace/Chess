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
        out.println("ERROR: " + msg);
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

    public void showGameOver(String result, String reason) {
        String r = (reason == null || reason.isBlank()) ? "" : (" (" + reason + ")");
        showMessage("\n=== Game Over ===");
        showMessage("Result: " + result + r);
        showMessage("");
    }

    public void clearScreen() {
        out.print("\u001B[2J\u001B[H");
        out.flush();
    }

    public void showBoard(String boardText) {
        showBoard(boardText, true);
    }

    public void showBoard(String boardText, boolean isWhitePerspective) {
        if (boardText == null || boardText.isBlank()) {
            out.println("(no board)");
            return;
        }
        out.println(renderBoard(boardText, isWhitePerspective));
    }

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp,
                                      boolean isWhitePerspective) {

        String renderedBoard = renderBoard(boardText, isWhitePerspective).stripTrailing();
        String[] b = renderedBoard.split("\n", -1);

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

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp) {
        showBoardWithCaptured(boardText, capturedByYou, capturedByOpp, true);
    }

    // ------------------------------------------------------------
    // BOARD LAYOUT FIX:
    // - Use big squares ⬛⬜
    // - Print cells with NO spaces between them
    // - Pad pieces to 2-column cells (piece + ' ')
    // ------------------------------------------------------------

    private static final String DARK_SQ = "\u2B1B";  // ⬛
    private static final String LIGHT_SQ = "\u2B1C"; // ⬜

    private static String renderBoard(String boardText, boolean isWhitePerspective) {
        char[][] grid = tryParseAsciiBoard(boardText);
        if (grid == null) return boardText;

        int[] files = isWhitePerspective
                ? new int[]{0,1,2,3,4,5,6,7}
                : new int[]{7,6,5,4,3,2,1,0};

        StringBuilder sb = new StringBuilder();

        appendFilesHeader(sb, files);

        int startRank = isWhitePerspective ? 8 : 1;
        int endRank = isWhitePerspective ? 1 : 8;
        int step = isWhitePerspective ? -1 : 1;

        for (int rank = startRank; ; rank += step) {
            int row = 8 - rank;

            sb.append(rank).append("  ");

            for (int i = 0; i < 8; i++) {
                int file = files[i];
                char p = grid[row][file];

                sb.append(renderCell(p, rank, file)); // <-- NO delimiter spaces
            }

            sb.append("  ").append(rank).append('\n');

            if (rank == endRank) break;
        }

        appendFilesHeader(sb, files);
        return sb.toString();
    }

    private static void appendFilesHeader(StringBuilder sb, int[] files) {
        // Each cell is visually 2 columns wide => print "a " per file
        sb.append("   ");
        for (int i = 0; i < 8; i++) {
            sb.append((char)('a' + files[i])).append(' ');
        }
        sb.append('\n');
    }

    private static String renderCell(char p, int rank, int fileIndex) {
        if (p == '.') {
            return emptySquareSymbol(rank, fileIndex); // ⬛/⬜ already visually wide
        }
        // Chess piece glyphs are usually visually narrower => pad to 2-column cell
        return pieceToUnicode(p) + " ";
    }

    private static String emptySquareSymbol(int rank, int fileIndex) {
        boolean dark = ((rank + fileIndex) % 2 == 1);
        return dark ? DARK_SQ : LIGHT_SQ;
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
            try { rank = Integer.parseInt(t[0]); } catch (Exception e) { continue; }
            if (rank < 1 || rank > 8) continue;

            for (int f = 0; f < 8; f++) {
                grid[8 - rank][f] = t[f + 1].charAt(0);
            }
            found++;
        }

        return found == 8 ? grid : null;
    }

    private static String renderPieces(List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : pieces) {
            if (s == null || s.isBlank()) continue;
            char c = s.trim().charAt(0);
            sb.append(pieceToUnicode(c)).append(' ');
        }
        return sb.length() == 0 ? "-" : sb.toString().trim();
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
}