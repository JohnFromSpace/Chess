package com.example.chess.client.view;

import java.util.List;

final class ChessGlyphs {

    private ChessGlyphs() {}

    static String pieceCell(char p) {
        // Keep your 2-char cell: glyph + space
        return pieceToUnicode(p) + " ";
    }

    static String pieceToUnicode(char c) {
        return switch (c) {
            case 'K' -> "\u2654"; case 'Q' -> "\u2655"; case 'R' -> "\u2656";
            case 'B' -> "\u2657"; case 'N' -> "\u2658"; case 'P' -> "\u2659";
            case 'k' -> "\u265A"; case 'q' -> "\u265B"; case 'r' -> "\u265C";
            case 'b' -> "\u265D"; case 'n' -> "\u265E"; case 'p' -> "\u265F";
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