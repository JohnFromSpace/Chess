package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public final class Queen extends Piece {
    public Queen(Color color) { super(color); }

    @Override
    public char toChar() { return getColor() == Color.WHITE ? 'Q' : 'q'; }

    @Override
    public boolean canMove(Board board, Move m) {
        int dx = Math.abs(m.getToCol() - m.getFromCol());
        int dy = Math.abs(m.getToRow() - m.getFromRow());

        boolean ok = (dx == 0 || dy == 0 || dx == dy);
        if (!ok || (dx == 0 && dy == 0)) return false;

        return board.isPathClear(m.getFromRow(), m.getFromCol(), m.getToRow(), m.getToCol());
    }
}