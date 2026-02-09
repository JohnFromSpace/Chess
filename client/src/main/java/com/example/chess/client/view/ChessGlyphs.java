package com.example.chess.client.view;

import java.util.List;

final class ChessGlyphs {

    private ChessGlyphs() {}

    static String pieceCell(char p) {
        return pieceToUnicode(p) + " ";
    }

    static String pieceToUnicode(char c) {
        return switch (c) {
            case 'K' -> "♔"; case 'Q' -> "♕"; case 'R' -> "♖";
            case 'B' -> "♗"; case 'N' -> "♘"; case 'P' -> "♙";
            case 'k' -> "♚"; case 'q' -> "♛"; case 'r' -> "♜";
            case 'b' -> "♝"; case 'n' -> "♞"; case 'p' -> "♟";
            default  -> "?";
        };
    }

    static String renderCaptured(List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : pieces) {
            if (s == null || s.isBlank()) continue;
            char c = s.trim().charAt(0);
            sb.append(pieceToUnicode(c)).append(' ');
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "-" : out;
    }
}
