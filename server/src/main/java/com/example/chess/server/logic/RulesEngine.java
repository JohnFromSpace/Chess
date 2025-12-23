package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.*;

import java.util.ArrayList;
import java.util.List;

public class RulesEngine {

    // Kept only for backwards compatibility (try not to use it anymore)
    @Deprecated
    public boolean sameColor(char a, char b) {
        return Character.isUpperCase(a) == Character.isUpperCase(b);
    }

    public boolean isLegalMove(Board board, Move move) {
        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) return false;
        if (move.fromRow == move.toRow && move.fromCol == move.toCol) return false;

        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return false;

        Piece dst = board.getPieceAt(move.toRow, move.toCol);
        if (dst != null && dst.getColor() == piece.getColor()) return false;

        return piece.canMove(board, move);
    }

    public boolean isLegalMove(Game game, Board board, Move move) {
        if (game == null) return isLegalMove(board, move);

        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) return false;
        if (move.fromRow == move.toRow && move.fromCol == move.toCol) return false;

        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return false;

        Piece dst = board.getPieceAt(move.toRow, move.toCol);
        if (dst != null && dst.getColor() == piece.getColor()) return false;

        Color mover = piece.getColor();

        // Castling attempt (e1g1/e1c1/e8g8/e8c8)
        if (piece instanceof King && move.fromCol == 4 && move.fromRow == move.toRow && (move.toCol == 6 || move.toCol == 2)) {
            boolean kingSide = (move.toCol == 6);
            return isLegalCastle(game, board, mover, kingSide);
        }

        // En passant capture
        if (piece instanceof Pawn && isEnPassantCapture(game, board, move, mover)) return true;

        // Promotion: allow both with suffix (q/r/b/n) or without suffix (= auto-queen on apply)
        if (piece instanceof Pawn && ((mover == Color.WHITE && move.toRow == 0) || (mover == Color.BLACK && move.toRow == 7))) {
            return piece.canMove(board, move);
        }

        // Normal move
        return piece.canMove(board, move);
    }

    public void applyMove(Board board, Game game, Move move, boolean updateState) {
        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return;

        Color mover = piece.getColor();
        Piece dst = board.getPieceAt(move.toRow, move.toCol);

        // Clear EP each ply (only set by pawn double-step)
        if (updateState && game != null) {
            game.enPassantRow = -1;
            game.enPassantCol = -1;
        }

        // CASTLING
        if (game != null && piece instanceof King && move.fromCol == 4 && move.fromRow == move.toRow && (move.toCol == 6 || move.toCol == 2)) {
            int row = (mover == Color.WHITE) ? 7 : 0;
            boolean kingSide = (move.toCol == 6);

            // move king
            board.setPieceAt(row, 4, null);
            board.setPieceAt(row, kingSide ? 6 : 2, piece);

            // move rook
            if (kingSide) {
                Piece rook = board.getPieceAt(row, 7);
                board.setPieceAt(row, 7, null);
                board.setPieceAt(row, 5, rook);
            } else {
                Piece rook = board.getPieceAt(row, 0);
                board.setPieceAt(row, 0, null);
                board.setPieceAt(row, 3, rook);
            }

            if (updateState && game != null) {
                if (mover == Color.WHITE) { game.wK = false; game.wQ = false; }
                else { game.bK = false; game.bQ = false; }
            }
            return;
        }

        // EN PASSANT CAPTURE
        if (game != null && piece instanceof Pawn && isEnPassantCapture(game, board, move, mover)) {
            int capRow = (mover == Color.WHITE) ? move.toRow + 1 : move.toRow - 1;

            // remove captured pawn
            board.setPieceAt(capRow, move.toCol, null);

            // move pawn
            board.setPieceAt(move.fromRow, move.fromCol, null);
            board.setPieceAt(move.toRow, move.toCol, piece);
            return;
        }

        // If capturing a rook on its original square -> affect castling rights
        if (updateState && game != null && dst instanceof Rook) {
            if (move.toRow == 7 && move.toCol == 0) game.wQ = false; // a1 rook captured
            if (move.toRow == 7 && move.toCol == 7) game.wK = false; // h1 rook captured
            if (move.toRow == 0 && move.toCol == 0) game.bQ = false; // a8 rook captured
            if (move.toRow == 0 && move.toCol == 7) game.bK = false; // h8 rook captured
        }

        // Remove from source
        board.setPieceAt(move.fromRow, move.fromCol, null);

        // Pawn double-step => set enPassant target (square passed over)
        if (updateState && game != null && piece instanceof Pawn) {
            int dir = (mover == Color.WHITE) ? -1 : 1;
            int startRow = (mover == Color.WHITE) ? 6 : 1;
            if (move.fromRow == startRow && move.toRow == startRow + 2 * dir && move.fromCol == move.toCol) {
                game.enPassantRow = move.fromRow + dir;
                game.enPassantCol = move.fromCol;
            }
        }

        // Update castling rights on king/rook move
        if (updateState && game != null) {
            if (piece instanceof King) {
                if (mover == Color.WHITE) { game.wK = false; game.wQ = false; }
                else { game.bK = false; game.bQ = false; }
            } else if (piece instanceof Rook) {
                if (mover == Color.WHITE && move.fromRow == 7 && move.fromCol == 0) game.wQ = false;
                if (mover == Color.WHITE && move.fromRow == 7 && move.fromCol == 7) game.wK = false;
                if (mover == Color.BLACK && move.fromRow == 0 && move.fromCol == 0) game.bQ = false;
                if (mover == Color.BLACK && move.fromRow == 0 && move.fromCol == 7) game.bK = false;
            }
        }

        // Promotion placement
        if (piece instanceof Pawn && ((mover == Color.WHITE && move.toRow == 0) || (mover == Color.BLACK && move.toRow == 7))) {
            Piece promoted = PieceFactory.promotionPiece(mover, move.promotion);
            board.setPieceAt(move.toRow, move.toCol, promoted);
        } else {
            board.setPieceAt(move.toRow, move.toCol, piece);
        }
    }

    public boolean hasAnyLegalMove(Game game, Board board, boolean whiteToMove) {
        List<Move> moves = generateAllPseudoMoves(game, board, whiteToMove);
        for (Move m : moves) {
            if (!isLegalMove(game, board, m)) continue;

            Board test = board.copy();
            applyMove(test, game, m, false);

            if (!isKingInCheck(test, whiteToMove)) return true;
        }
        return false;
    }

    public boolean isKingInCheck(Board b, boolean whiteKing) {
        Color kingColor = whiteKing ? Color.WHITE : Color.BLACK;

        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = b.getPieceAt(r, c);
                if (p instanceof King && p.getColor() == kingColor) {
                    kr = r; kc = c;
                    break;
                }
            }
            if (kr != -1) break;
        }
        if (kr == -1) return false; // should not happen

        return isSquareAttacked(b, kr, kc, kingColor.opposite());
    }

    private boolean isEnPassantCapture(Game game, Board board, Move m, Color mover) {
        if (game.enPassantRow != m.toRow || game.enPassantCol != m.toCol) return false;

        Piece piece = board.getPieceAt(m.fromRow, m.fromCol);
        if (!(piece instanceof Pawn) || piece.getColor() != mover) return false;

        int dir = (mover == Color.WHITE) ? -1 : 1;
        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;

        // pawn moves diagonally 1 to an empty square
        if (dr != dir || Math.abs(dc) != 1) return false;
        if (!board.isEmptyAt(m.toRow, m.toCol)) return false;

        // captured pawn is behind target square
        int capRow = (mover == Color.WHITE) ? m.toRow + 1 : m.toRow - 1;
        Piece cap = board.getPieceAt(capRow, m.toCol);
        return (cap instanceof Pawn) && cap.getColor() == mover.opposite();
    }

    private boolean isLegalCastle(Game game, Board board, Color mover, boolean kingSide) {
        boolean white = (mover == Color.WHITE);
        int row = white ? 7 : 0;

        // Rights
        if (kingSide) {
            if (white && !game.wK) return false;
            if (!white && !game.bK) return false;
        } else {
            if (white && !game.wQ) return false;
            if (!white && !game.bQ) return false;
        }

        Piece king = board.getPieceAt(row, 4);
        if (!(king instanceof King) || king.getColor() != mover) return false;

        Piece rook = board.getPieceAt(row, kingSide ? 7 : 0);
        if (!(rook instanceof Rook) || rook.getColor() != mover) return false;

        // Squares between must be empty
        if (kingSide) {
            if (!board.isEmptyAt(row, 5) || !board.isEmptyAt(row, 6)) return false;
        } else {
            if (!board.isEmptyAt(row, 1) || !board.isEmptyAt(row, 2) || !board.isEmptyAt(row, 3)) return false;
        }

        // King cannot be in check, and cannot pass through attacked squares
        if (isKingInCheck(board, white)) return false;

        if (kingSide) {
            if (isSquareAttacked(board, row, 5, mover.opposite())) return false;
            if (isSquareAttacked(board, row, 6, mover.opposite())) return false;
        } else {
            if (isSquareAttacked(board, row, 3, mover.opposite())) return false;
            if (isSquareAttacked(board, row, 2, mover.opposite())) return false;
        }

        return true;
    }

    private boolean isSquareAttacked(Board b, int row, int col, Color byColor) {
        boolean byWhite = (byColor == Color.WHITE);

        // Pawn attacks
        int pr = byWhite ? row + 1 : row - 1;
        for (int dc : new int[]{-1, 1}) {
            int pc = col + dc;
            if (b.inside(pr, pc)) {
                Piece p = b.getPieceAt(pr, pc);
                if (p instanceof Pawn && p.getColor() == byColor) return true;
            }
        }

        // Knight attacks
        int[][] KN = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : KN) {
            int r = row + d[0], c = col + d[1];
            if (b.inside(r, c)) {
                Piece p = b.getPieceAt(r, c);
                if (p instanceof Knight && p.getColor() == byColor) return true;
            }
        }

        // King adjacent
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc2 = -1; dc2 <= 1; dc2++) {
                if (dr == 0 && dc2 == 0) continue;
                int r = row + dr, c = col + dc2;
                if (b.inside(r, c)) {
                    Piece p = b.getPieceAt(r, c);
                    if (p instanceof King && p.getColor() == byColor) return true;
                }
            }
        }

        // Rook/Queen (orthogonal)
        if (rayAttacks(b, row, col, byColor, -1,  0, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1,  0, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  0, -1, Rook.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  0,  1, Rook.class, Queen.class)) return true;

        // Bishop/Queen (diagonal)
        if (rayAttacks(b, row, col, byColor, -1, -1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor, -1,  1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1, -1, Bishop.class, Queen.class)) return true;
        if (rayAttacks(b, row, col, byColor,  1,  1, Bishop.class, Queen.class)) return true;

        return false;
    }

    @SafeVarargs
    private final boolean rayAttacks(Board b, int row, int col, Color byColor, int dr, int dc, Class<? extends Piece>... allowed) {
        int r = row + dr, c = col + dc;
        while (b.inside(r, c)) {
            Piece x = b.getPieceAt(r, c);
            if (x != null) {
                if (x.getColor() != byColor) return false;
                for (Class<? extends Piece> k : allowed) {
                    if (k.isInstance(x)) return true;
                }
                return false;
            }
            r += dr;
            c += dc;
        }
        return false;
    }

    private List<Move> generateAllPseudoMoves(Game game, Board board, boolean whiteToMove) {
        List<Move> out = new ArrayList<>();
        Color mover = whiteToMove ? Color.WHITE : Color.BLACK;

        for (int fr = 0; fr < 8; fr++) {
            for (int fc = 0; fc < 8; fc++) {
                Piece piece = board.getPieceAt(fr, fc);
                if (piece == null || piece.getColor() != mover) continue;

                if (piece instanceof Pawn) genPawnMoves(game, board, out, fr, fc, mover);
                else if (piece instanceof Knight) genKnightMoves(board, out, fr, fc, mover);
                else if (piece instanceof Bishop) genSlidingMoves(board, out, fr, fc, mover, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
                else if (piece instanceof Rook) genSlidingMoves(board, out, fr, fc, mover, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
                else if (piece instanceof Queen) genSlidingMoves(board, out, fr, fc, mover, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}});
                else if (piece instanceof King) genKingMoves(game, board, out, fr, fc, mover);
            }
        }
        return out;
    }

    private void genPawnMoves(Game game, Board board, List<Move> out, int fr, int fc, Color mover) {
        int dir = (mover == Color.WHITE) ? -1 : 1;
        int startRow = (mover == Color.WHITE) ? 6 : 1;
        int lastRow = (mover == Color.WHITE) ? 0 : 7;

        int r1 = fr + dir;

        // forward 1
        if (board.inside(r1, fc) && board.isEmptyAt(r1, fc)) {
            if (r1 == lastRow) {
                out.add(new Move(fr, fc, r1, fc, 'q'));
                out.add(new Move(fr, fc, r1, fc, 'r'));
                out.add(new Move(fr, fc, r1, fc, 'b'));
                out.add(new Move(fr, fc, r1, fc, 'n'));
                out.add(new Move(fr, fc, r1, fc, null)); // allow “no suffix”
            } else {
                out.add(new Move(fr, fc, r1, fc, null));
            }
        }

        // forward 2
        int r2 = fr + 2 * dir;
        if (fr == startRow && board.inside(r2, fc) && board.isEmptyAt(r1, fc) && board.isEmptyAt(r2, fc)) {
            out.add(new Move(fr, fc, r2, fc, null));
        }

        // captures + EP
        for (int dc : new int[]{-1, 1}) {
            int tc = fc + dc;
            int tr = fr + dir;
            if (!board.inside(tr, tc)) continue;

            Piece dst = board.getPieceAt(tr, tc);
            if (dst != null && dst.getColor() == mover.opposite()) {
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

            if (game != null && game.enPassantRow == tr && game.enPassantCol == tc && board.isEmptyAt(tr, tc)) {
                out.add(new Move(fr, fc, tr, tc, null));
            }
        }
    }

    private void genKnightMoves(Board board, List<Move> out, int fr, int fc, Color mover) {
        int[][] KN = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : KN) {
            int tr = fr + d[0], tc = fc + d[1];
            if (!board.inside(tr, tc)) continue;

            Piece dst = board.getPieceAt(tr, tc);
            if (dst == null || dst.getColor() != mover) out.add(new Move(fr, fc, tr, tc, null));
        }
    }

    private void genSlidingMoves(Board board, List<Move> out, int fr, int fc, Color mover, int[][] dirs) {
        for (int[] d : dirs) {
            int r = fr + d[0], c = fc + d[1];
            while (board.inside(r, c)) {
                Piece dst = board.getPieceAt(r, c);
                if (dst == null) {
                    out.add(new Move(fr, fc, r, c, null));
                } else {
                    if (dst.getColor() != mover) out.add(new Move(fr, fc, r, c, null));
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
    }

    private void genKingMoves(Game game, Board board, List<Move> out, int fr, int fc, Color mover) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int tr = fr + dr, tc = fc + dc;
                if (!board.inside(tr, tc)) continue;

                Piece dst = board.getPieceAt(tr, tc);
                if (dst == null || dst.getColor() != mover) out.add(new Move(fr, fc, tr, tc, null));
            }
        }

        // castling “pseudo” (validated by isLegalMove(game,...))
        if (game != null && fc == 4) {
            if (mover == Color.WHITE && fr == 7) {
                if (game.wK) out.add(new Move(7, 4, 7, 6, null));
                if (game.wQ) out.add(new Move(7, 4, 7, 2, null));
            } else if (mover == Color.BLACK && fr == 0) {
                if (game.bK) out.add(new Move(0, 4, 0, 6, null));
                if (game.bQ) out.add(new Move(0, 4, 0, 2, null));
            }
        }
    }
}