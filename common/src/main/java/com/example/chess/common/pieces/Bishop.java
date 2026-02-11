package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public final class Bishop extends Piece {
    public Bishop(Color color) { super(color); }

    @Override
    public char toChar() { return getColor() == Color.WHITE ? 'B' : 'b'; }

    @Override
    public boolean canMove(Board board, Move m) {
        int dx = Math.abs(m.getToRow() - m.getFromRow());
        int dy = Math.abs(m.getToCol() - m.getFromCol());
        if (dx == 0 || dx != dy) return false;
        return board.isPathClear(m.getFromRow(), m.getFromCol(), m.getToRow(), m.getToCol());
    }
}