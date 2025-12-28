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
        if (!inside(row, col)) return null;
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
        String ascii = toPrettyString();
        StringBuilder sb = new StringBuilder(ascii.length());
        for (int i = 0; i < ascii.length(); i++) {
            char c = ascii.charAt(i);
            sb.append(switch (c) {
                case 'K' -> "\u2654"; case 'Q' -> "\u2655"; case 'R' -> "\u2656";
                case 'B' -> "\u2657"; case 'N' -> "\u2658"; case 'P' -> "\u2659";
                case 'k' -> "\u265A"; case 'q' -> "\u265B"; case 'r' -> "\u265C";
                case 'b' -> "\u265D"; case 'n' -> "\u265E"; case 'p' -> "\u265F";
                default -> String.valueOf(c);
            });
        }
        return sb.toString();
    }
}