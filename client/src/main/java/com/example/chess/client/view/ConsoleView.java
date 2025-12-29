package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class ConsoleView {

    private final ConsoleInput in;
    private final PrintStream out;

    private static final String DARK  = "##";
    private static final String LIGHT = "...";

    public ConsoleView(ConsoleInput in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void showMessage(String msg) {
        out.println(msg);
    }

    public void showError(String msg) {
        out.println("ERROR: " + msg);
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

    public String askLineResponsive(String prompt,
                                    long pollEveryMs,
                                    Runnable pump,
                                    BooleanSupplier shouldAbort) {

        out.print(prompt);
        out.flush();

        while (true) {
            if (shouldAbort != null && shouldAbort.getAsBoolean()) return null;

            String line = in.pollLine(pollEveryMs);
            if (line != null) return line;

            if (pump != null) pump.run();
        }
    }

    public int askIntResponsive(String prompt,
                                long pollEveryMs,
                                Runnable pump,
                                BooleanSupplier shouldAbort) {

        while (true) {
            String line = askLineResponsive(prompt, pollEveryMs, pump, shouldAbort);
            if (line == null) return Integer.MIN_VALUE;

            String t = line.trim();
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                out.println("Please enter a number.");
            }
        }
    }

    public String askLine(String prompt) {
        return askLineResponsive(prompt, 1_000_000L, null, () -> false);
    }

    public int askInt(String prompt) {
        int v = askIntResponsive(prompt, 1_000_000L, null, () -> false);
        return v == Integer.MIN_VALUE ? 0 : v;
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

        int boardCols = 0;
        for (String line : b) boardCols = Math.max(boardCols, displayWidth(line));

        final int GAP = 10;

        List<String> side = new ArrayList<>();
        side.add("Captured by YOU: " + renderPieces(capturedByYou));
        side.add("Captured by OPP: " + renderPieces(capturedByOpp));
        side.add("Promotion: q/r/b/n (not limited by captured pieces)");

        int rows = Math.max(b.length, side.size());
        for (int i = 0; i < rows; i++) {
            String left = (i < b.length) ? b[i] : "";
            String right = (i < side.size()) ? side.get(i) : "";

            out.print(left);

            int pad = (boardCols - displayWidth(left)) + GAP;
            if (!right.isBlank()) {
                out.print(" ".repeat(Math.max(1, pad)));
                out.print(right);
            }
            out.println();
        }
    }

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp) {
        showBoardWithCaptured(boardText, capturedByYou, capturedByOpp, true);
    }

    private static String renderBoard(String boardText, boolean isWhitePerspective) {
        char[][] grid = tryParseAsciiBoard(boardText);
        if (grid == null) return boardText;

        int[] files = isWhitePerspective
                ? new int[]{0,1,2,3,4,5,6,7}
                : new int[]{7,6,5,4,3,2,1,0};

        int startRank = isWhitePerspective ? 8 : 1;
        int endRank   = isWhitePerspective ? 1 : 8;
        int step      = isWhitePerspective ? -1 : 1;

        StringBuilder sb = new StringBuilder();

        for (int rank = startRank; ; rank += step) {
            int row = 8 - rank;

            sb.append(rank).append("  ");

            for (int i = 0; i < 8; i++) {
                int file = files[i];
                char pc = grid[row][file];

                if (pc == '.') {
                    boolean dark = ((rank + file) % 2 == 1);
                    sb.append(dark ? DARK : LIGHT);
                } else {
                    sb.append(pieceAscii(pc));
                }
            }

            sb.append('\n');
            if (rank == endRank) break;
        }

        appendFilesHeaderBottom(sb, files);
        return sb.toString();
    }

    private static String pieceAscii(char p) {
        String u = switch (p) {
            case 'K' -> "\u2654"; case 'Q' -> "\u2655"; case 'R' -> "\u2656";
            case 'B' -> "\u2657"; case 'N' -> "\u2658"; case 'P' -> "\u2659";
            case 'k' -> "\u265A"; case 'q' -> "\u265B"; case 'r' -> "\u265C";
            case 'b' -> "\u265D"; case 'n' -> "\u265E"; case 'p' -> "\u265F";
            default  -> "?";
        };

        return u + " ";
    }


    private static void appendFilesHeaderBottom(StringBuilder sb, int[] files) {
        sb.append("   "); // matches "rank + two spaces" prefix
        for (int i = 0; i < 8; i++) {
            char fileChar = (char)('a' + files[i]);
            sb.append(fileChar);

            if (i <= 2) sb.append("  ");
            else if (i == 3 || i == 4) sb.append(" ");
            else sb.append("  ");
        }
        sb.append('\n');
    }

    private static char[][] tryParseAsciiBoard(String boardText) {
        if (boardText == null) return null;

        char[][] grid = new char[8][8];
        int found = 0;

        for (String raw : boardText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Must start with a rank number (1..8)
            if (!Character.isDigit(line.charAt(0))) continue;

            String[] t = line.split("\\s+");
            if (t.length < 9) continue; // need at least: rank + 8 cells

            int rank;
            try { rank = Integer.parseInt(t[0]); }
            catch (Exception e) { continue; }

            if (rank < 1 || rank > 8) continue;

            int start = 1;

            // If the line contains a right-side rank label, it's the last token and numeric.
            // Example: "8  ♜ ♞ ...  8"
            // In that case cells are t[1]..t[8].
            // If not, we still read t[1]..t[8].
            if (t.length >= 10) {
                // If last token is numeric, ignore it.
                String last = t[t.length - 1];
                if (last.chars().allMatch(Character::isDigit)) {
                    // ok, cells still start at 1
                }
            }

            for (int f = 0; f < 8; f++) {
                String cellTok = t[start + f];
                grid[8 - rank][f] = normalizeCellToPieceChar(cellTok);
            }

            found++;
        }

        return found == 8 ? grid : null;
    }

    private static char normalizeCellToPieceChar(String tok) {
        if (tok == null) return '.';
        String s = tok.trim();
        if (s.isEmpty()) return '.';

        // Empty squares from different formats
        if (s.equals(".") || s.equals("..") || s.equals("...") || s.equals("##")) return '.';

        // If it's already a piece char like 'P', 'k', etc.
        if (s.length() == 1) {
            char c = s.charAt(0);
            if ("KQRBNPkqrbnp".indexOf(c) >= 0) return c;
        }

        // Unicode chess pieces (U+2654..U+265F)
        int cp = s.codePointAt(0);
        return switch (cp) {
            case 0x2654 -> 'K'; // ♔
            case 0x2655 -> 'Q'; // ♕
            case 0x2656 -> 'R'; // ♖
            case 0x2657 -> 'B'; // ♗
            case 0x2658 -> 'N'; // ♘
            case 0x2659 -> 'P'; // ♙
            case 0x265A -> 'k'; // ♚
            case 0x265B -> 'q'; // ♛
            case 0x265C -> 'r'; // ♜
            case 0x265D -> 'b'; // ♝
            case 0x265E -> 'n'; // ♞
            case 0x265F -> 'p'; // ♟
            default -> '.';
        };
    }

    private static String renderPieces(List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : pieces) {
            if (s == null || s.isBlank()) continue;
            char c = s.trim().charAt(0);
            sb.append(pieceToUnicode(c)).append(' ');
        }
        return sb.isEmpty() ? "-" : sb.toString().trim();
    }

    private static String pieceToUnicode(char c) {
        return switch (c) {
            case 'K' -> "\u2654 "; case 'Q' -> "\u2655 "; case 'R' -> "\u2656 ";
            case 'B' -> "\u2657 "; case 'N' -> "\u2658 "; case 'P' -> "\u2659 ";
            case 'k' -> "\u265A "; case 'q' -> "\u265B "; case 'r' -> "\u265C ";
            case 'b' -> "\u265D "; case 'n' -> "\u265E "; case 'p' -> "\u265F ";
            default -> String.valueOf(c);
        };
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) continue;

            w += isWide(cp) ? 2 : 1;
        }
        return w;
    }

    private static boolean isWide(int cp) {
        if (cp == 0x2B1B || cp == 0x2B1C) return true;
        return cp >= 0x1F300 && cp <= 0x1FAFF;
    }
}