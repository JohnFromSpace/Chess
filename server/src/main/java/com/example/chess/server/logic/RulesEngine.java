package com.example.chess.server.logic;

import com.example.chess.common.GameModels.Board;
import com.example.chess.common.GameModels.Move;
import com.example.chess.common.pieces.Piece;
import com.example.chess.common.pieces.PieceFactory;

public class RulesEngine {

    public boolean sameColor(char a, char b) {
        if (a == '.' || b == '.' || a == 0 || b == 0) return false;
        return Character.isUpperCase(a) == Character.isUpperCase(b);
    }

    public Board copyBoard(Board b) {
        Board nb = new Board();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                nb.squares[row][col] = b.squares[row][col];
            }
        }
        return nb;
    }

    public boolean isLegalMove(Board board, Move m) {
        char src = board.get(m.fromRow, m.fromCol);
        if (src == '.' || src == 0) return false;

        Piece piece = PieceFactory.fromChar(src).orElse(null);
        if (piece == null) return false;

        // can't capture own piece
        char dest = board.get(m.toRow, m.toCol);
        if (dest != '.' && dest != 0 && sameColor(src, dest)) return false;

        return piece.canMove(board, m);
    }

    public boolean isKingInCheck(Board board, boolean isWhiteKing) {
        char king = isWhiteKing ? 'K' : 'k';
        int kr = -1, kc = -1;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board.get(r, c) == king) {
                    kr = r;
                    kc = c;
                    break;
                }
            }
        }

        if (kr == -1) return true;

        return isSquareAttacked(board, kr, kc, !isWhiteKing);
    }

    public boolean hasAnyLegalMove(Board board, boolean forWhite) {
        for (int fromRow = 0; fromRow < 8; fromRow++) {
            for (int fromCol = 0; fromCol < 8; fromCol++) {
                char src = board.get(fromRow, fromCol);
                if (src == '.' || src == 0) continue;

                boolean pieceIsWhite = Character.isUpperCase(src);
                if (pieceIsWhite != forWhite) continue;

                for (int toRow = 0; toRow < 8; toRow++) {
                    for (int toCol = 0; toCol < 8; toCol++) {
                        if (fromRow == toRow && fromCol == toCol) continue;

                        Move m = new Move();
                        m.fromRow = fromRow;
                        m.fromCol = fromCol;
                        m.toRow = toRow;
                        m.toCol = toCol;

                        // piece movement rules (via Piece classes)
                        if (!isLegalMove(board, m)) continue;

                        // simulate move
                        Board test = copyBoard(board);
                        test.set(m.toRow, m.toCol, src);
                        test.set(m.fromRow, m.fromCol, '.');

                        // must not leave king in check
                        if (!isKingInCheck(test, forWhite)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isSquareAttacked(Board board, int row, int col, boolean byWhite) {
        // rooks & queens (orthogonal)
        int[][] rookDirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : rookDirs) {
            int r = row + d[0], c = col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (p != '.' && p != 0) {
                    if (byWhite && Character.isUpperCase(p) && (p == 'R' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) && (p == 'r' || p == 'q')) return true;
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }

        // bishops & queens (diagonals)
        int[][] bishopDirs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : bishopDirs) {
            int r = row + d[0], c = col + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (p != '.' && p != 0) {
                    if (byWhite && Character.isUpperCase(p) && (p == 'B' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) && (p == 'b' || p == 'q')) return true;
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }

        // knights
        int[][] knightMoves = {
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
        };
        for (int[] m : knightMoves) {
            int r = row + m[0], c = col + m[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board.get(r, c);
                if (byWhite && p == 'N') return true;
                if (!byWhite && p == 'n') return true;
            }
        }

        // pawns (attacking direction)
        int dir = byWhite ? -1 : 1;
        int pawnRow = row + dir;

        if (pawnRow >= 0 && pawnRow < 8) {
            if (col - 1 >= 0) {
                char p = board.get(pawnRow, col - 1);
                if (byWhite && p == 'P') return true;
                if (!byWhite && p == 'p') return true;
            }
            if (col + 1 < 8) {
                char p = board.get(pawnRow, col + 1);
                if (byWhite && p == 'P') return true;
                if (!byWhite && p == 'p') return true;
            }
        }

        // adjacent king
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                    char p = board.get(r, c);
                    if (byWhite && p == 'K') return true;
                    if (!byWhite && p == 'k') return true;
                }
            }
        }

        return false;
    }
}