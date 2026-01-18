package com.example.chess.common.board;

import com.example.chess.common.pieces.Piece;
import com.example.chess.common.pieces.PieceFactory;

import java.util.Arrays;

public class Board {
    public final char[][] squares = new char[8][8];

    public Board() {
        for (int r = 0; r < 8; r++) Arrays.fill(squares[r], '.');
    }

    public static Board initial() {
        Board b = new Board();
        b.squares[0] = "rnbqkbnr".toCharArray();
        b.squares[1] = "pppppppp".toCharArray();
        b.squares[6] = "PPPPPPPP".toCharArray();
        b.squares[7] = "RNBQKBNR".toCharArray();
        return b;
    }

    public char get(int row, int col) { return squares[row][col]; }
    public void set(int row, int col, char piece) { squares[row][col] = piece; }

    public boolean inside(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public Piece getPieceAt(int row, int col) {
        if (!inside(row, col)) throw new IllegalArgumentException("Incorrect positions [rol/col].");
        return PieceFactory.fromCharOrNull(get(row, col));
    }

    public Piece getPieceAt(Square sq) {
        return getPieceAt(sq.row, sq.col);
    }

    public void setPieceAt(int row, int col, Piece piece) {
        if (!inside(row, col)) return;
        set(row, col, piece == null ? '.' : piece.toChar());
    }

    public boolean isEmptyAt(int row, int col) {
        if (!inside(row, col)) return true;
        return get(row, col) == '.';
    }

    public boolean isPathClear(int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int r = fromRow + dRow;
        int c = fromCol + dCol;

        while (r != toRow || c != toCol) {
            if (!isEmptyAt(r, c)) return false;
            r += dRow;
            c += dCol;
        }
        return true;
    }

    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < 8; r++) b.squares[r] = Arrays.copyOf(this.squares[r], 8);
        return b;
    }

    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");
        for (int r = 0; r < 8; r++) {
            int rank = 8 - r;
            sb.append(rank).append(' ');
            for (int c = 0; c < 8; c++) sb.append(squares[r][c]).append(' ');
            sb.append(rank).append('\n');
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }

    public String toUnicodePrettyString() {
        final String LIGHT = "...";
        final String DARK  = "##";

        int cellW = 1;
        cellW = Math.max(cellW, displayWidth(LIGHT));
        cellW = Math.max(cellW, displayWidth(DARK));

        for (char pc : new char[]{'K','Q','R','B','N','P','k','q','r','b','n','p'}) {
            cellW = Math.max(cellW, displayWidth(pieceUnicode(pc)));
        }

        StringBuilder sb = new StringBuilder();

        sb.append("   ");
        for (int c = 0; c < 8; c++) {
            char file = (char) ('a' + c);
            sb.append(padRight(String.valueOf(file), cellW));
        }
        sb.append('\n');

        for (int r = 0; r < 8; r++) {
            int rank = 8 - r;
            sb.append(String.format("%2d ", rank));

            for (int c = 0; c < 8; c++) {
                char pc = squares[r][c];

                final String cell;
                if (pc == '.') {
                    boolean dark = ((r + c) % 2 == 1);
                    cell = dark ? DARK : LIGHT;
                } else {
                    cell = pieceUnicode(pc);
                }

                sb.append(padRight(cell, cellW));
            }

            sb.append(String.format(" %2d", rank));
            sb.append('\n');
        }

        sb.append("   ");
        for (int c = 0; c < 8; c++) {
            char file = (char) ('a' + c);
            sb.append(padRight(String.valueOf(file), cellW));
        }
        sb.append('\n');

        return sb.toString();
    }

    private static String pieceUnicode(char c) {
        return switch (c) {
            case 'K' -> "\u2654"; case 'Q' -> "\u2655"; case 'R' -> "\u2656";
            case 'B' -> "\u2657"; case 'N' -> "\u2658"; case 'P' -> "\u2659";
            case 'k' -> "\u265A"; case 'q' -> "\u265B"; case 'r' -> "\u265C";
            case 'b' -> "\u265D"; case 'n' -> "\u265E"; case 'p' -> "\u265F";
            default  -> String.valueOf(c);
        };
    }

    private static String padRight(String s, int targetWidth) {
        int w = displayWidth(s);
        if (w >= targetWidth) return s;
        return s + " ".repeat(targetWidth - w);
    }

    private static int displayWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
                continue;
            }

            width += isWide(cp) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(int cp) {
        if (cp == 0x2B1B || cp == 0x2B1C) return true;

        if (cp >= 0x1F300 && cp <= 0x1FAFF) return true;

        if (cp >= 0x1100 && cp <= 0x115F) return true;
        if (cp >= 0x2E80 && cp <= 0xA4CF) return true;
        if (cp >= 0xAC00 && cp <= 0xD7A3) return true;
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;
        if (cp >= 0xFE10 && cp <= 0xFE6F) return true;
        if (cp >= 0xFF00 && cp <= 0xFF60) return true;

        return false;
    }
}