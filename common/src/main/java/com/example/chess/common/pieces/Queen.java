package com.example.chess.common.pieces;

import com.example.chess.common.GameModels;
import com.example.chess.common.board.Color;

public final class Queen extends Piece {
    public Queen(Color color) { super(color); }

    @Override
    public char toChar() {
        return getColor() == Color.WHITE ? 'Q' : 'q';
    }

    @Override
    public boolean canMove(GameModels.Board board, GameModels.Move m) {
        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);
        boolean ok = (dx == 0 || dy == 0 || dx == dy);
        if (!ok) return false;
        return isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
    }
}