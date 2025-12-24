package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.pieces.*;

public class AttackService {

    public boolean isKingInCheck(Board b, boolean whiteKing) {
        Color kingColor = whiteKing ? Color.WHITE : Color.BLACK;

        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                var p = b.getPieceAt(r, c);
                if (p instanceof King && p.getColor() == kingColor) {
                    kr = r; kc = c;
                    break;
                }
            }
            if (kr != -1) break;
        }
        if (kr == -1) return false;

        return isSquareAttacked(b, kr, kc, kingColor.opposite());
    }

    public boolean isSquareAttacked(Board b, int row, int col, Color byColor) {
        boolean byWhite = (byColor == Color.WHITE);

        // pawn attacks
        int pr = byWhite ? row + 1 : row - 1;
        for (int dc : new int[]{-1, 1}) {
            int pc = col + dc;
            if (b.inside(pr, pc)) {
                var p = b.getPieceAt(pr, pc);
                if (p instanceof Pawn && p.getColor() == byColor) return true;
            }
        }

        // knight attacks
        int[][] KN = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : KN) {
            int r = row + d[0], c = col + d[1];
            if (b.inside(r, c)) {
                var p = b.getPieceAt(r, c);
                if (p instanceof Knight && p.getColor() == byColor) return true;
            }
        }

        // king adjacency
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc2 = -1; dc2 <= 1; dc2++) {
                if (dr == 0 && dc2 == 0) continue;
                int r = row + dr, c = col + dc2;
                if (b.inside(r, c)) {
                    var p = b.getPieceAt(r, c);
                    if (p instanceof King && p.getColor() == byColor) return true;
                }
            }
        }

        // rook/queen rays
        if (rayAttacks(b, row, col, byColor, -1,  0, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1,  0, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  0, -1, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  0,  1, Rook.class, Queen.class)) return true;

        // bishop/queen rays
        if (rayAttacks(b, row, col, byColor, -1, -1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor, -1,  1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1, -1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1,  1, Bishop.class, Queen.class)) return true;

        return false;
    }

    @SafeVarargs
    private final boolean rayAttacks(Board b, int row, int col, Color byColor,
                                     int dr, int dc, Class<? extends Piece>... allowed) {
        int r = row + dr, c = col + dc;
        while (b.inside(r, c)) {
            var x = b.getPieceAt(r, c);
            if (x != null) {
                if (x.getColor() != byColor) return false;
                for (Class<? extends Piece> k : allowed) if (k.isInstance(x)) return true;
                return false;
            }
            r += dr;
            c += dc;
        }
        return false;
    }
}