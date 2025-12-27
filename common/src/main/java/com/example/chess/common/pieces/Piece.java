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

    public abstract boolean canMove(Board board, Move move);

    protected final boolean isEnemy(Piece other) {
        return other != null && other.color != this.color;
    }
}