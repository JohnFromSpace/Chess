package com.example.chess.common.pieces;

import com.example.chess.common.board.Color;

import java.util.Optional;

public final class PieceFactory {
    private PieceFactory() {}

    public static Optional<Piece> fromChar(char c) {
        if (c == '.' || c == 0) return Optional.empty();

        Color color = Character.isUpperCase(c) ? Color.WHITE : Color.BLACK;
        char p = Character.toLowerCase(c);

        return switch (p) {
            case 'p' -> Optional.of(new Pawn(color));
            case 'n' -> Optional.of(new Knight(color));
            case 'b' -> Optional.of(new Bishop(color));
            case 'r' -> Optional.of(new Rook(color));
            case 'q' -> Optional.of(new Queen(color));
            case 'k' -> Optional.of(new King(color));
            default -> Optional.empty();
        };
    }
}