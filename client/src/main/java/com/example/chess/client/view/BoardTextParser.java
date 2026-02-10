package com.example.chess.client.view;

import com.example.chess.client.util.Log;

final class BoardTextParser {

    private BoardTextParser() {}

    static char[][] tryParse(String boardText) {
        if (boardText == null) throw new IllegalArgumentException("There is no board to be printed out.");

        char[][] grid = new char[8][8];
        int found = 0;

        for (String raw : boardText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!Character.isDigit(line.charAt(0))) continue;

            String[] t = line.split("\\s+");
            if (t.length < 9) continue;

            int rank;
            try { rank = Integer.parseInt(t[0]); }
            catch (Exception e) {
                Log.warn("Failed to parse board rank: " + t[0], e);
                continue;
            }

            if (rank < 1 || rank > 8) continue;

            int start = 1;

            for (int f = 0; f < 8; f++) {
                grid[8 - rank][f] = normalizeCellToPieceChar(t[start + f]);
            }

            found++;
        }

        return found == 8 ? grid : null;
    }

    private static char normalizeCellToPieceChar(String tok) {
        if (tok == null) return '.';
        String s = tok.trim();
        if (s.isEmpty()) return '.';

        if (s.equals(".") || s.equals("..") || s.equals("...") || s.equals("##")) return '.';

        if (s.length() == 1) {
            char c = s.charAt(0);
            if ("KQRBNPkqrbnp".indexOf(c) >= 0) return c;
        }

        int cp = s.codePointAt(0);
        return switch (cp) {
            case 0x2654 -> 'K'; case 0x2655 -> 'Q'; case 0x2656 -> 'R';
            case 0x2657 -> 'B'; case 0x2658 -> 'N'; case 0x2659 -> 'P';
            case 0x265A -> 'k'; case 0x265B -> 'q'; case 0x265C -> 'r';
            case 0x265D -> 'b'; case 0x265E -> 'n'; case 0x265F -> 'p';
            default -> '.';
        };
    }
}
