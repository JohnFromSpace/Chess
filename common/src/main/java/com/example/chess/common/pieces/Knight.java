package com.example.chess.common.pieces;

import com.example.chess.common.GameModels;
import com.example.chess.common.board.Color;

public final class Knight extends Piece {
    public Knight(Color color) { super(color); }

    @Override
    public char toChar() {
        return getColor() == Color.WHITE ? 'N' : 'n';
    }

    @Override
    public boolean canMove(GameModels.Board board, GameModels.Move m) {
        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);
        return dx * dx + dy * dy == 5;
    }
}