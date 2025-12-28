package com.example.chess.common.pieces;

import com.example.chess.common.board.Color;

import java.util.Optional;

public final class PieceFactory {
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

    public static Piece fromCharOrNull(char c) {
        return fromChar(c).orElse(null);
    }

    public static Piece promotionPiece(Color color, Character promotion) {
        char p = (promotion == null) ? 'q' : Character.toLowerCase(promotion);
        return switch (p) {
            case 'q' -> new Queen(color);
            case 'r' -> new Rook(color);
            case 'b' -> new Bishop(color);
            case 'n' -> new Knight(color);
            default  -> new Queen(color);
        };
    }
}