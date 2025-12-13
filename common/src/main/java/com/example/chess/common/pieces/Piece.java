package com.example.chess.common.pieces;

import com.example.chess.common.board.Color;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;

public abstract class Piece {
    private final Color color;

    protected Piece(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public abstract char toChar();

    public abstract boolean canMove(Board board, Move move);

    protected static boolean isEmpty(char c) {
        return c == '.' || c == 0;
    }

    protected static boolean sameColor(char a, char b) {
        if (isEmpty(a) || isEmpty(b)) return false;
        return Character.isUpperCase(a) == Character.isUpperCase(b);
    }

    protected static boolean isPathClear(Board board, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int r = fromRow + dRow;
        int c = fromCol + dCol;

        while (r != toRow || c != toCol) {
            char p = board.get(r, c);
            if (!isEmpty(p)) return false;
            r += dRow;
            c += dCol;
        }
        return true;
    }
}