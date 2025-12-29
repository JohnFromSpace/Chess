package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.King;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;

import java.util.ArrayList;
import java.util.List;

public final class RulesEngine {

    private final AttackService attacks = new AttackService();
    private final CastlingRule castling = new CastlingRule(attacks);
    private final EnPassantRule enPassant = new EnPassantRule();
    private final MoveApplier applier = new MoveApplier(castling, enPassant);
    private final MoveLegalityChecker legality = new MoveLegalityChecker(castling, enPassant);

    public boolean isLegalMove(Game game, Board board, Move move) {
        return legality.isLegalMove(game, board, move);
    }

    public void applyMove(Board board, Game game, Move move, boolean updateState) {
        applier.applyMove(board, game, move, updateState);
    }

    public boolean isKingInCheck(Board board, boolean whiteKing) {
        return attacks.isKingInCheck(board, whiteKing);
    }

    public boolean hasAnyLegalMove(Game game, Board board, boolean whiteToMove) {
        if (board == null) return false;

        Color side = whiteToMove ? Color.WHITE : Color.BLACK;

        for (int fr = 0; fr < 8; fr++) {
            for (int fc = 0; fc < 8; fc++) {
                Piece p = board.getPieceAt(fr, fc);
                if (p == null || p.getColor() != side) continue;

                for (int tr = 0; tr < 8; tr++) {
                    for (int tc = 0; tc < 8; tc++) {
                        if (fr == tr && fc == tc) continue;

                        for (Move m : candidateMovesFor(p, fr, fc, tr, tc, whiteToMove)) {
                            if (!isLegalMove(game, board, m)) continue;

                            Board copy = board.copy();
                            applyMove(copy, game, m, false);

                            if (!isKingInCheck(copy, whiteToMove)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private List<Move> candidateMovesFor(Piece p,
                                         int fr, int fc,
                                         int tr, int tc,
                                         boolean whiteToMove) {

        List<Move> out = new ArrayList<>(5);

        if (p instanceof Pawn) {
            int lastRow = whiteToMove ? 0 : 7;

            // Only expand promotions when the pawn is moving into last rank.
            if (tr == lastRow) {
                out.add(new Move(fr, fc, tr, tc, 'q'));
                out.add(new Move(fr, fc, tr, tc, 'r'));
                out.add(new Move(fr, fc, tr, tc, 'b'));
                out.add(new Move(fr, fc, tr, tc, 'n'));
                out.add(new Move(fr, fc, tr, tc, null)); // allow default promotion behavior if supported
                return out;
            }
        }

        // Normal case
        out.add(new Move(fr, fc, tr, tc, null));
        return out;
    }

    public boolean isKingInCheck(Game game, Board board, Color kingColor) {
        return isKingInCheck(board, kingColor == Color.WHITE);
    }

    public int[] findKing(Board board, boolean whiteKing) {
        if (board == null) return null;
        char kingChar = whiteKing ? 'K' : 'k';
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board.get(r, c) == kingChar) return new int[]{r, c};
            }
        }
        return null;
    }
}