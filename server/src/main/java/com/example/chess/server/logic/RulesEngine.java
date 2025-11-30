package com.example.chess.server.logic;

import com.example.chess.common.GameModels.Board;
import com.example.chess.common.GameModels.Move;

public class RulesEngine {
    public boolean sameColor(char a, char b) {
        if (a == '.' || b == '.' || a == 0 || b == 0) {
            return false;
        }
        boolean whiteA = Character.isUpperCase(a);
        boolean whiteB = Character.isUpperCase(b);
        return whiteA == whiteB;
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

    public boolean isLegalMoveForPiece(Board board, char piece, Move m, boolean isWhite) {
        char p = Character.toLowerCase(piece);

        int dx = Math.abs(m.toCol - m.fromCol);
        int dy = Math.abs(m.toRow - m.fromRow);

        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        return switch (p) {
            case 'p' -> isLegalPawnMove(board, m, isWhite);
            case 'n' -> adx * adx + ady * ady == 5;
            case 'b' -> {
                if (adx != ady) yield false;
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'r' -> {
                if (!(dx == 0 || dy == 0)) yield false;
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'q' -> {
                if (!(dx == 0 || dy == 0 || adx == ady)) yield false;
                yield isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
            }
            case 'k' -> adx <= 1 && ady <= 1;
            default -> false;
        };
    }

    private boolean isLegalPawnMove(Board board, Move m, boolean isWhite) {
        int dir = isWhite ? -1 : 1;
        int startRow = isWhite ? 6 : 1;

        int dx = m.toCol - m.fromCol;
        int dy = m.toRow - m.fromRow;

        char dest = board.get(m.toRow, m.toCol);

        // forward moves
        if (dx == 0) {
            // single push
            if (dy == dir && dest == '.') {
                return true;
            }
            // double push from start
            if (m.fromRow == startRow && dy == 2 * dir) {
                int midRow = m.fromRow + dir;
                return board.get(midRow, m.fromCol) == '.' && dest == '.';
            }
            return false;
        }

        // captures
        if (Math.abs(dx) == 1 && dy == dir) {
            return dest != '.' && !sameColor(board.get(m.fromRow, m.fromCol), dest);
        }

        return false;
    }

    private boolean isPathClear(Board board, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int r = fromRow + dRow;
        int c = fromCol + dCol;

        while (r != toRow || c != toCol) {
            char p = board.get(r, c);
            if (p != '.' && p != 0) return false;
            r += dRow;
            c += dCol;
        }
        return true;
    }

    public boolean isKingInCheck(Board board, boolean isWhite) {
        char king = isWhite ? 'K' : 'k';
        int kr = -1;
        int kc = -1;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (board.get(row, col) == king) {
                    kr = row;
                    kc = col;
                }
            }
        }

        if (kr == -1) {
            // treat as in check (broken board)
            return true;
        }

        return isSquareAttacked(board, kr, kc, !isWhite);
    }

    public boolean hasAnyLegalMove(Board board, boolean forWhite) {
        for (int fromRow = 0; fromRow < 8; fromRow++) {
            for (int fromCol = 0; fromCol < 8; fromCol++) {
                char piece = board.get(fromRow, fromCol);
                if (piece == '.' || piece == 0) continue;

                boolean pieceIsWhite = Character.isUpperCase(piece);
                if (pieceIsWhite != forWhite) continue;

                for (int toRow = 0; toRow < 8; toRow++) {
                    for (int toCol = 0; toCol < 8; toCol++) {
                        if (fromRow == toRow && fromCol == toCol) continue;

                        char dest = board.get(toRow, toCol);
                        // can't capture own piece
                        if (dest != '.' && dest != 0 && sameColor(piece, dest)) continue;

                        Move m = new Move();
                        m.fromRow = fromRow;
                        m.fromCol = fromCol;
                        m.toRow = toRow;
                        m.toCol = toCol;

                        // geometric & occupancy rules
                        if (!isLegalMoveForPiece(board, piece, m, forWhite)) continue;

                        // simulate on a copy and check king safety
                        Board test = copyBoard(board);
                        test.set(m.toRow, m.toCol, piece);
                        test.set(m.fromRow, m.fromCol, '.');

                        if (!isKingInCheck(test, forWhite)) {
                            // found at least one legal move
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
                    if (byWhite && Character.isUpperCase(p) &&
                            (p == 'R' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) &&
                            (p == 'r' || p == 'q')) return true;
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
                    if (byWhite && Character.isUpperCase(p) &&
                            (p == 'B' || p == 'Q')) return true;
                    if (!byWhite && Character.isLowerCase(p) &&
                            (p == 'b' || p == 'q')) return true;
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

        // pawns (attacking towards us)
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