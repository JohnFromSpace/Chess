package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public final class Pawn extends Piece {
    public Pawn(Color color) { super(color); }

    @Override
    public char toChar() { return getColor() == Color.WHITE ? 'P' : 'p'; }

    @Override
    public boolean canMove(Board board, Move m) {
        int dir = (getColor() == Color.WHITE) ? -1 : 1;
        int startRow = (getColor() == Color.WHITE) ? 6 : 1;

        int dx = m.toCol - m.fromCol;
        int dy = m.toRow - m.fromRow;

        Piece dest = board.getPieceAt(m.toRow, m.toCol);

        // forward
        if (dx == 0) {
            if (dy == dir && isEmpty(dest)) return true;

            if (m.fromRow == startRow && dy == 2 * dir) {
                int midRow = m.fromRow + dir;
                return isEmpty(board.getPieceAt(midRow, m.fromCol)) && isEmpty(dest);
            }
            return false;
        }

        // capture
        if (Math.abs(dx) == 1 && dy == dir) {
            return dest != null && isEnemy(dest);
        }

        return false;
    }
}