package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.common.pieces.King;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;

public final class MoveLegalityChecker {

    private final CastlingRule castling;
    private final EnPassantRule enPassant;

    public MoveLegalityChecker(CastlingRule castling, EnPassantRule enPassant) {
        this.castling = castling;
        this.enPassant = enPassant;
    }

    /** Pseudo-legal check (does NOT check self-check). */
    public boolean isLegalMove(Game game, Board board, Move move) {
        if (board == null || move == null) return false;

        if (!board.inside(move.getFromRow(), move.getFromCol()) || !board.inside(move.getToRow(), move.getToCol())) return false;
        if (move.getFromRow() == move.getToRow() && move.getFromCol() == move.getToCol()) return false;

        if (game != null && game.getResult() != null && game.getResult() != Result.ONGOING) return false;

        Piece piece = board.getPieceAt(move.getFromRow(), move.getFromCol());
        if (piece == null) return false;

        // enforce turn if game is present
        if (game != null) {
            boolean wantsWhite = game.isWhiteMove();
            if (piece.isWhite() != wantsWhite) return false;
        }

        Piece dst = board.getPieceAt(move.getToRow(), move.getToCol());
        if (dst != null && dst.getColor() == piece.getColor()) return false;

        Color mover = piece.getColor();

        // castling
        if (game != null && piece instanceof King && castling.isCastleAttempt(piece, move)) {
            boolean kingSide = (move.getToCol() == 6);
            return castling.isLegalCastle(game, board, mover, kingSide);
        }

        // en-passant capture
        if (game != null && piece instanceof Pawn) {
            if (enPassant.isEnPassantCapture(game, board, move, mover)) return true;
        }

        // normal movement rule (incl. pawn normal capture with piece present)
        return piece.canMove(board, move);
    }
}