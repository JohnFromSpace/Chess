package com.example.chess.common.pieces;

import com.example.chess.common.board.Color;

public final class Pawn extends Piece {
    public Pawn(Color color) { super(color); }

    @Override
    public char toChar() {
        return getColor() == Color.WHITE ? 'P' : 'p';
    }

    @Override
    public boolean canMove(GameModels.Board board, GameModels.Move m) {
        int dir = (getColor() == Color.WHITE) ? -1 : 1;
        int startRow = (getColor() == Color.WHITE) ? 6 : 1;

        int dx = m.toCol - m.fromCol;
        int dy = m.toRow - m.fromRow;

        char dest = board.get(m.toRow, m.toCol);
        char src = board.get(m.fromRow, m.fromCol);

        // forward
        if (dx == 0) {
            if (dy == dir && isEmpty(dest)) return true;

            if (m.fromRow == startRow && dy == 2 * dir) {
                int midRow = m.fromRow + dir;
                return isEmpty(board.get(midRow, m.fromCol)) && isEmpty(dest);
            }
            return false;
        }

        // capture
        if (Math.abs(dx) == 1 && dy == dir) {
            return !isEmpty(dest) && !sameColor(src, dest);
        }

        return false;
    }
}
