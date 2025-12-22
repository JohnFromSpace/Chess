package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;

import java.util.ArrayList;
import java.util.List;

public class RulesEngine {
    public boolean sameColor(char a, char b) {
        if (a == '.' || a == 0 || b == '.' || b == 0) return false;
        return Character.isUpperCase(a) == Character.isUpperCase(b);
    }

    public Board copyBoard(Board b) {
        return b.copy();
    }

    public boolean isKingInCheck(Board b, boolean whiteKing) {
        char king = whiteKing ? 'K' : 'k';
        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (b.get(r, c) == king) {
                    kr = r;
                    kc = c;
                    break;
                }
            }
        }
        if (kr < 0) return true; // king missing => illegal position => treat as "in check"
        return isSquareAttacked(b, kr, kc, !whiteKing);
    }

    public boolean isSquareAttacked(Board b, int row, int col, boolean byWhite) {
        // Pawn attacks (reverse lookup)
        int pr = byWhite ? row + 1 : row - 1;
        if (pr >= 0 && pr < 8) {
            int pc1 = col - 1;
            int pc2 = col + 1;
            char pawn = byWhite ? 'P' : 'p';
            if (pc1 >= 0 && pc1 < 8 && b.get(pr, pc1) == pawn) return true;
            if (pc2 >= 0 && pc2 < 8 && b.get(pr, pc2) == pawn) return true;
        }

        // Knight attacks
        int[][] KN = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                { 1, -2}, { 1, 2}, { 2, -1}, { 2, 1}
        };
        char n = byWhite ? 'N' : 'n';
        for (int[] d : KN) {
            int r = row + d[0], c = col + d[1];
            if (b.inside(r, c) && b.get(r, c) == n) return true;
        }

        // King adjacent attacks
        char k = byWhite ? 'K' : 'k';
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (b.inside(r, c) && b.get(r, c) == k) return true;
            }
        }

        // Rook/Queen (orthogonal)
        if (rayAttacks(b, row, col, byWhite, -1,  0, 'R', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite,  1,  0, 'R', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite,  0, -1, 'R', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite,  0,  1, 'R', 'Q')) return true;

        // Bishop/Queen (diagonal)
        if (rayAttacks(b, row, col, byWhite, -1, -1, 'B', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite, -1,  1, 'B', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite,  1, -1, 'B', 'Q')) return true;
        if (rayAttacks(b, row, col, byWhite,  1,  1, 'B', 'Q')) return true;

        return false;
    }

    private boolean rayAttacks(Board b, int row, int col, boolean byWhite, int dr, int dc, char piece, char queen) {
        char p1 = byWhite ? piece : Character.toLowerCase(piece);
        char p2 = byWhite ? queen : Character.toLowerCase(queen);

        int r = row + dr, c = col + dc;
        while (b.inside(r, c)) {
            char x = b.get(r, c);
            if (x != '.' && x != 0) return (x == p1 || x == p2);
            r += dr;
            c += dc;
        }
        return false;
    }

    public boolean isLegalMove(Board board, Move move) {
        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) return false;

        char piece = board.get(move.fromRow, move.fromCol);
        if (piece == '.' || piece == 0) return false;

        char dst = board.get(move.toRow, move.toCol);
        if ((dst != '.' && dst != 0) && sameColor(piece, dst)) return false;

        boolean white = Character.isUpperCase(piece);
        char t = Character.toLowerCase(piece);

        return switch (t) {
            case 'p' -> isNormalPawnMove(board, move, white);
            case 'n' -> isKnightMove(move);
            case 'b' -> isBishopMove(board, move);
            case 'r' -> isRookMove(board, move);
            case 'q' -> isQueenMove(board, move);
            case 'k' -> isKingMove(move); // castling handled in Game-aware method
            default -> false;
        };
    }

    public boolean isLegalMove(Game game, Board board, Move move) {
        if (game == null) {
            // fallback to board-only behavior
            return isLegalMove(board, move);
        }
        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) return false;

        char piece = board.get(move.fromRow, move.fromCol);
        if (piece == '.' || piece == 0) return false;

        boolean white = Character.isUpperCase(piece);
        char t = Character.toLowerCase(piece);

        char dst = board.get(move.toRow, move.toCol);
        if ((dst != '.' && dst != 0) && sameColor(piece, dst)) return false;

        // Castling attempt: e1g1/e1c1/e8g8/e8c8
        if (t == 'k' && move.fromCol == 4 && move.fromRow == move.toRow && (move.toCol == 6 || move.toCol == 2)) {
            boolean kingSide = (move.toCol == 6);
            return isLegalCastle(game, board, white, kingSide);
        }

        // En passant
        if (t == 'p' && isEnPassantCapture(game, board, move, white)) return true;

        // Promotion (allowed with 4 chars -> auto-queen, or 5 chars -> chosen piece)
        if (t == 'p' && ((white && move.toRow == 0) || (!white && move.toRow == 7))) {
            // must be a normal pawn move to last rank (forward or capture)
            return isNormalPawnMove(board, move, white);
        }

        // Normal move
        return isLegalMove(board, move);
    }

    public void applyMove(Board board, Game game, Move move, boolean updateState) {
        char piece = board.get(move.fromRow, move.fromCol);
        boolean white = Character.isUpperCase(piece);
        char t = Character.toLowerCase(piece);
        char dst = board.get(move.toRow, move.toCol);

        // Clear en passant each ply (only set by a pawn double-step)
        if (updateState && game != null) {
            game.enPassantRow = -1;
            game.enPassantCol = -1;
        }

        // CASTLING
        if (game != null && t == 'k' && move.fromCol == 4 && move.fromRow == move.toRow && (move.toCol == 6 || move.toCol == 2)) {
            int row = white ? 7 : 0;
            boolean kingSide = (move.toCol == 6);

            // King move
            board.set(row, 4, '.');
            board.set(row, kingSide ? 6 : 2, white ? 'K' : 'k');

            // Rook move
            if (kingSide) {
                board.set(row, 7, '.');
                board.set(row, 5, white ? 'R' : 'r');
            } else {
                board.set(row, 0, '.');
                board.set(row, 3, white ? 'R' : 'r');
            }

            if (updateState) {
                if (white) { game.wK = false; game.wQ = false; }
                else { game.bK = false; game.bQ = false; }
            }
            return;
        }

        // EN PASSANT CAPTURE
        if (game != null && t == 'p' && isEnPassantCapture(game, board, move, white)) {
            int capRow = white ? move.toRow + 1 : move.toRow - 1;
            board.set(capRow, move.toCol, '.'); // remove captured pawn
            board.set(move.fromRow, move.fromCol, '.');
            board.set(move.toRow, move.toCol, piece);
            return;
        }

        // If capturing a rook on its original square -> affect castling rights
        if (updateState && game != null && (dst == 'R' || dst == 'r')) {
            if (move.toRow == 7 && move.toCol == 0) game.wQ = false; // a1 rook captured
            if (move.toRow == 7 && move.toCol == 7) game.wK = false; // h1 rook captured
            if (move.toRow == 0 && move.toCol == 0) game.bQ = false; // a8 rook captured
            if (move.toRow == 0 && move.toCol == 7) game.bK = false; // h8 rook captured
        }

        // Remove from source
        board.set(move.fromRow, move.fromCol, '.');

        // Pawn double-step => set enPassant target
        if (updateState && game != null && t == 'p') {
            int dir = white ? -1 : 1;
            int startRow = white ? 6 : 1;
            if (move.fromRow == startRow && move.toRow == startRow + 2 * dir && move.fromCol == move.toCol) {
                game.enPassantRow = move.fromRow + dir; // square passed over
                game.enPassantCol = move.fromCol;
            }
        }

        // Update castling rights on king/rook move
        if (updateState && game != null) {
            if (t == 'k') {
                if (white) { game.wK = false; game.wQ = false; }
                else { game.bK = false; game.bQ = false; }
            } else if (t == 'r') {
                if (white && move.fromRow == 7 && move.fromCol == 0) game.wQ = false;
                if (white && move.fromRow == 7 && move.fromCol == 7) game.wK = false;
                if (!white && move.fromRow == 0 && move.fromCol == 0) game.bQ = false;
                if (!white && move.fromRow == 0 && move.fromCol == 7) game.bK = false;
            }
        }

        // Promotion placement
        if (t == 'p' && ((white && move.toRow == 0) || (!white && move.toRow == 7))) {
            char promo = (move.promotion == null) ? 'q' : Character.toLowerCase(move.promotion);
            char placed = switch (promo) {
                case 'q' -> 'Q';
                case 'r' -> 'R';
                case 'b' -> 'B';
                case 'n' -> 'N';
                default -> 'Q';
            };
            board.set(move.toRow, move.toCol, white ? placed : Character.toLowerCase(placed));
        } else {
            board.set(move.toRow, move.toCol, piece);
        }
    }

    // =========================
    // Mate/stalemate helper
    // =========================

    /**
     * Full legal-move existence check (includes special moves if Game is provided),
     * and filters out moves that leave own king in check.
     */
    public boolean hasAnyLegalMove(Game game, Board board, boolean whiteToMove) {
        List<Move> moves = generateAllPseudoMoves(game, board, whiteToMove);
        for (Move m : moves) {
            if (!isLegalMove(game, board, m)) continue;

            Board test = board.copy();
            // simulation: do NOT update rights/enPassant
            applyMove(test, game, m, false);

            if (!isKingInCheck(test, whiteToMove)) return true;
        }
        return false;
    }

    /**
     * Backward-compatible overload (no Game state -> no castling/enPassant history).
     * Still checks normal moves + king safety.
     */
    public boolean hasAnyLegalMove(Board board, boolean whiteToMove) {
        List<Move> moves = generateAllPseudoMoves(null, board, whiteToMove);
        for (Move m : moves) {
            if (!isLegalMove(board, m)) continue;

            Board test = board.copy();
            // apply naive (no special updates possible)
            applyMoveNaive(test, m);

            if (!isKingInCheck(test, whiteToMove)) return true;
        }
        return false;
    }

    // =========================
    // Internal: pseudo move generation
    // =========================

    private List<Move> generateAllPseudoMoves(Game game, Board board, boolean whiteToMove) {
        List<Move> out = new ArrayList<>();

        for (int fr = 0; fr < 8; fr++) {
            for (int fc = 0; fc < 8; fc++) {
                char piece = board.get(fr, fc);
                if (piece == '.' || piece == 0) continue;

                boolean white = Character.isUpperCase(piece);
                if (white != whiteToMove) continue;

                char t = Character.toLowerCase(piece);

                switch (t) {
                    case 'p' -> genPawnMoves(game, board, out, fr, fc, white);
                    case 'n' -> genKnightMoves(board, out, fr, fc);
                    case 'b' -> genSlidingMoves(board, out, fr, fc, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
                    case 'r' -> genSlidingMoves(board, out, fr, fc, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
                    case 'q' -> genSlidingMoves(board, out, fr, fc, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}});
                    case 'k' -> genKingMoves(game, board, out, fr, fc, white);
                }
            }
        }
        return out;
    }

    private void genPawnMoves(Game game, Board board, List<Move> out, int fr, int fc, boolean white) {
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;
        int lastRow = white ? 0 : 7;

        // forward 1
        int r1 = fr + dir;
        if (board.inside(r1, fc) && isEmpty(board.get(r1, fc))) {
            if (r1 == lastRow) {
                // promotion candidates
                out.add(new Move(fr, fc, r1, fc, 'q'));
                out.add(new Move(fr, fc, r1, fc, 'r'));
                out.add(new Move(fr, fc, r1, fc, 'b'));
                out.add(new Move(fr, fc, r1, fc, 'n'));
                // also allow "no suffix" style; isLegalMove(Game,...) accepts it
                out.add(new Move(fr, fc, r1, fc, null));
            } else {
                out.add(new Move(fr, fc, r1, fc, null));
            }
        }

        // forward 2 from start
        int r2 = fr + 2 * dir;
        if (fr == startRow && board.inside(r2, fc) && isEmpty(board.get(r1, fc)) && isEmpty(board.get(r2, fc))) {
            out.add(new Move(fr, fc, r2, fc, null));
        }

        // captures
        for (int dc : new int[]{-1, 1}) {
            int tc = fc + dc;
            int tr = fr + dir;
            if (!board.inside(tr, tc)) continue;

            char dst = board.get(tr, tc);
            if (!isEmpty(dst) && (Character.isUpperCase(dst) != white)) {
                if (tr == lastRow) {
                    out.add(new Move(fr, fc, tr, tc, 'q'));
                    out.add(new Move(fr, fc, tr, tc, 'r'));
                    out.add(new Move(fr, fc, tr, tc, 'b'));
                    out.add(new Move(fr, fc, tr, tc, 'n'));
                    out.add(new Move(fr, fc, tr, tc, null));
                } else {
                    out.add(new Move(fr, fc, tr, tc, null));
                }
            }
        }

        // en passant candidates (only if game != null)
        if (game != null && game.enPassantRow >= 0 && game.enPassantCol >= 0) {
            int tr = fr + dir;
            if (tr == game.enPassantRow && Math.abs(game.enPassantCol - fc) == 1) {
                out.add(new Move(fr, fc, game.enPassantRow, game.enPassantCol, null));
            }
        }
    }

    private void genKnightMoves(Board board, List<Move> out, int fr, int fc) {
        int[][] KN = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                { 1, -2}, { 1, 2}, { 2, -1}, { 2, 1}
        };
        for (int[] d : KN) {
            int tr = fr + d[0], tc = fc + d[1];
            if (!board.inside(tr, tc)) continue;
            out.add(new Move(fr, fc, tr, tc, null));
        }
    }

    private void genSlidingMoves(Board board, List<Move> out, int fr, int fc, int[][] dirs) {
        for (int[] d : dirs) {
            int tr = fr + d[0], tc = fc + d[1];
            while (board.inside(tr, tc)) {
                out.add(new Move(fr, fc, tr, tc, null));
                if (!isEmpty(board.get(tr, tc))) break;
                tr += d[0];
                tc += d[1];
            }
        }
    }

    private void genKingMoves(Game game, Board board, List<Move> out, int fr, int fc, boolean white) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int tr = fr + dr, tc = fc + dc;
                if (board.inside(tr, tc)) out.add(new Move(fr, fc, tr, tc, null));
            }
        }

        // castling candidates (only if game != null)
        if (game != null) {
            // e1g1 / e1c1 or e8g8 / e8c8
            if (white && fr == 7 && fc == 4) {
                out.add(new Move(7, 4, 7, 6, null));
                out.add(new Move(7, 4, 7, 2, null));
            } else if (!white && fr == 0 && fc == 4) {
                out.add(new Move(0, 4, 0, 6, null));
                out.add(new Move(0, 4, 0, 2, null));
            }
        }
    }

    private boolean isNormalPawnMove(Board board, Move m, boolean white) {
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;

        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;

        char dst = board.get(m.toRow, m.toCol);

        // forward 1
        if (dc == 0 && dr == dir && isEmpty(dst)) return true;

        // forward 2 from start
        if (dc == 0 && m.fromRow == startRow && dr == 2 * dir) {
            int midRow = m.fromRow + dir;
            return isEmpty(dst) && isEmpty(board.get(midRow, m.fromCol));
        }

        // capture
        if (Math.abs(dc) == 1 && dr == dir && !isEmpty(dst)) {
            char src = board.get(m.fromRow, m.fromCol);
            return !sameColor(src, dst);
        }

        return false;
    }

    private boolean isKnightMove(Move m) {
        int dr = Math.abs(m.toRow - m.fromRow);
        int dc = Math.abs(m.toCol - m.fromCol);
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
    }

    private boolean isBishopMove(Board board, Move m) {
        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;
        if (Math.abs(dr) != Math.abs(dc) || dr == 0) return false;
        return isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
    }

    private boolean isRookMove(Board board, Move m) {
        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;
        if (!((dr == 0) ^ (dc == 0))) return false;
        return isPathClear(board, m.fromRow, m.fromCol, m.toRow, m.toCol);
    }

    private boolean isQueenMove(Board board, Move m) {
        return isBishopMove(board, m) || isRookMove(board, m);
    }

    private boolean isKingMove(Move m) {
        int dr = Math.abs(m.toRow - m.fromRow);
        int dc = Math.abs(m.toCol - m.fromCol);
        return dr <= 1 && dc <= 1 && !(dr == 0 && dc == 0);
    }

    private boolean isPathClear(Board board, int fr, int fc, int tr, int tc) {
        int dr = Integer.compare(tr, fr);
        int dc = Integer.compare(tc, fc);
        int r = fr + dr, c = fc + dc;
        while (r != tr || c != tc) {
            if (!isEmpty(board.get(r, c))) return false;
            r += dr;
            c += dc;
        }
        return true;
    }

    private boolean isEnPassantCapture(Game game, Board board, Move m, boolean white) {
        if (game.enPassantRow != m.toRow || game.enPassantCol != m.toCol) return false;

        char piece = board.get(m.fromRow, m.fromCol);
        if (Character.toLowerCase(piece) != 'p') return false;

        int dir = white ? -1 : 1;
        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;

        // pawn moves diagonally 1 to an empty square
        if (dr != dir || Math.abs(dc) != 1) return false;
        if (!isEmpty(board.get(m.toRow, m.toCol))) return false;

        // captured pawn is behind target square
        int capRow = white ? m.toRow + 1 : m.toRow - 1;
        char cap = board.get(capRow, m.toCol);
        return cap == (white ? 'p' : 'P');
    }

    private boolean isLegalCastle(Game game, Board board, boolean white, boolean kingSide) {
        int row = white ? 7 : 0;

        // Must have rights
        if (white) {
            if (kingSide && !game.wK) return false;
            if (!kingSide && !game.wQ) return false;
        } else {
            if (kingSide && !game.bK) return false;
            if (!kingSide && !game.bQ) return false;
        }

        // King and rook must be in place
        if (board.get(row, 4) != (white ? 'K' : 'k')) return false;
        if (kingSide) {
            if (board.get(row, 7) != (white ? 'R' : 'r')) return false;
        } else {
            if (board.get(row, 0) != (white ? 'R' : 'r')) return false;
        }

        // Squares between must be empty
        if (kingSide) {
            if (!isEmpty(board.get(row, 5)) || !isEmpty(board.get(row, 6))) return false;
        } else {
            if (!isEmpty(board.get(row, 1)) || !isEmpty(board.get(row, 2)) || !isEmpty(board.get(row, 3))) return false;
        }

        // King cannot be in check, and cannot pass through attacked squares
        if (isKingInCheck(board, white)) return false;

        if (kingSide) {
            if (isSquareAttacked(board, row, 5, !white)) return false;
            if (isSquareAttacked(board, row, 6, !white)) return false;
        } else {
            if (isSquareAttacked(board, row, 3, !white)) return false;
            if (isSquareAttacked(board, row, 2, !white)) return false;
        }

        return true;
    }

    private boolean isEmpty(char x) {
        return x == '.' || x == 0;
    }

    // =========================
    // Naive apply (used only by hasAnyLegalMove(Board,...))
    // =========================

    private void applyMoveNaive(Board board, Move move) {
        char piece = board.get(move.fromRow, move.fromCol);
        board.set(move.fromRow, move.fromCol, '.');

        // very basic promotion handling in naive mode (auto queen)
        if (Character.toLowerCase(piece) == 'p' && (move.toRow == 0 || move.toRow == 7)) {
            boolean white = Character.isUpperCase(piece);
            char q = white ? 'Q' : 'q';
            board.set(move.toRow, move.toCol, q);
        } else {
            board.set(move.toRow, move.toCol, piece);
        }
    }
}