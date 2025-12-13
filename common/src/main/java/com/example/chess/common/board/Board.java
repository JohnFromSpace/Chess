package com.example.chess.common.board;

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
        return row>=0 && row<8 && col>=0 && col<8;
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
            int rank = 8-r;
            sb.append(rank).append(' ');
            for (int c = 0; c < 8; c++) sb.append(squares[r][c]).append(' ');
            sb.append(rank).append('\n');
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }
}