package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public final class Rook extends Piece {
    public Rook(Color color) { super(color); }

    @Override
    public char toChar() { return getColor() == Color.WHITE ? 'R' : 'r'; }

    @Override
    public boolean canMove(Board board, Move m) {
        int dr = m.getToRow() - m.getFromRow();
        int dc = m.getToCol() - m.getFromCol();
        if ((dr == 0) == (dc == 0)) return false; // must be straight and non-zero
        return board.isPathClear(m.getFromRow(), m.getFromCol(), m.getToRow(), m.getToCol());
    }
}