package com.example.chess.common.pieces;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;

public final class Rook extends Piece {
    public Rook(Color color) { super(color); }

    @Override
    public char toChar() {
        return getColor() == Color.WHITE ? 'R' : 'r';
    }

    @Override
    public boolean canMove(Board board, Move m) {
        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);
        if (!(dx == 0 || dy == 0)) return false;
        return isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
    }
}