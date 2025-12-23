package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public abstract class Piece {
    private final Color color;

    protected Piece(Color color) {
        this.color = color;
    }

    public final Color getColor() { return color; }

    public final boolean isWhite() { return color == Color.WHITE; }
    public final boolean isBlack() { return color == Color.BLACK; }

    public abstract char toChar();

    // “normal movement” only (castling / en passant / promotion are handled in RulesEngine)
    public abstract boolean canMove(Board board, Move move);

    protected final boolean sameColor(Piece other) {
        return other != null && other.color == this.color;
    }

    protected final boolean isEnemy(Piece other) {
        return other != null && other.color != this.color;
    }

    protected static boolean isEmpty(Piece p) { return p == null; }

    protected static boolean isPathClear(Board board, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int r = fromRow + dRow;
        int c = fromCol + dCol;

        while (r != toRow || c != toCol) {
            if (!isEmpty(board.getPieceAt(r, c))) return false;
            r += dRow;
            c += dCol;
        }
        return true;
    }
}