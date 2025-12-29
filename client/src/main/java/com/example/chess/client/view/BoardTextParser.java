package com.example.chess.client.view;

final class BoardTextParser {

    private BoardTextParser() {}

    static char[][] tryParse(String boardText) {
        if (boardText == null) return null;

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
            catch (Exception e) { continue; }

            if (rank < 1 || rank > 8) continue;

            int start = 1;

            // If there's a right-side rank label (e.g. "... 8"), ignore it implicitly by reading t[1..8].
            if (t.length < start + 8) continue;

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

        // Empty squares from different formats
        if (s.equals(".") || s.equals("..") || s.equals("...") || s.equals("##")) return '.';

        // Already a piece char (P,k,...)
        if (s.length() == 1) {
            char c = s.charAt(0);
            if ("KQRBNPkqrbnp".indexOf(c) >= 0) return c;
        }

        // Unicode chess pieces (U+2654..U+265F)
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