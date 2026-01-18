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

        int dx = m.getToCol() - m.getFromCol();
        int dy = m.getToRow() - m.getFromRow();

        Piece dest = board.getPieceAt(m.getToRow(), m.getToCol());

        if (dx == 0) {
            if (dy == dir && dest == null) return true;

            if (m.getFromRow() == startRow && dy == 2 * dir) {
                int midRow = m.getFromRow() + dir;
                return board.getPieceAt(midRow, m.getFromCol()) == null && dest == null;
            }
            return false;
        }

        if (Math.abs(dx) == 1 && dy == dir) {
            return isEnemy(dest);
        }

        return false;
    }
}