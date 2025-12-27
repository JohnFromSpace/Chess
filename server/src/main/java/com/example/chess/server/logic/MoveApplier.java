package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;
import com.example.chess.common.pieces.PieceFactory;
import com.example.chess.common.pieces.Rook;

public class MoveApplier {

    private final CastlingRule castling;
    private final EnPassantRule enPassant;

    public MoveApplier(CastlingRule castling, EnPassantRule enPassant) {
        this.castling = castling;
        this.enPassant = enPassant;
    }

    public void applyMove(Board board, Game game, Move move, boolean updateState) {
        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return;

        Color mover = piece.getColor();
        Piece dst = board.getPieceAt(move.toRow, move.toCol);

        if (updateState && game != null) {
            enPassant.clearEp(game);
        }

        // castling
        if (game != null && castling.isCastleAttempt(piece, move)) {
            boolean kingSide = (move.toCol == 6);
            castling.applyCastle(board, game, mover, kingSide, piece, updateState);
            return;
        }

        // en-passant capture (destination empty; captured pawn is behind)
        if (game != null && piece instanceof Pawn && enPassant.isEnPassantCapture(game, board, move, mover)) {
            int capRow = (mover == Color.WHITE) ? move.toRow + 1 : move.toRow - 1;
            Piece captured = board.getPieceAt(capRow, move.toCol);

            if (updateState && game != null && captured != null) {
                recordCapture(game, mover, captured);
            }

            enPassant.applyEnPassant(board, move, mover, piece);
            return;
        }

        // normal capture
        if (updateState && game != null && dst != null) {
            recordCapture(game, mover, dst);
        }

        if (updateState && game != null && dst instanceof Rook) {
            castling.onRookCaptured(game, move);
        }

        board.setPieceAt(move.fromRow, move.fromCol, null);

        if (updateState && game != null && piece instanceof Pawn) {
            enPassant.onPawnMoveMaybeSetTarget(game, move, mover);
        }

        if (updateState && game != null) {
            castling.onKingOrRookMoved(game, piece, move, mover);
        }

        // promotion / normal placement
        if (piece instanceof Pawn && ((mover == Color.WHITE && move.toRow == 0) || (mover == Color.BLACK && move.toRow == 7))) {
            Piece promoted = PieceFactory.promotionPiece(mover, move.promotion);
            board.setPieceAt(move.toRow, move.toCol, promoted);
        } else {
            board.setPieceAt(move.toRow, move.toCol, piece);
        }
    }

    private static void recordCapture(Game game, Color mover, Piece captured) {
        if (game == null || captured == null) return;
        char ch = captured.toChar();
        if (mover == Color.WHITE) game.addCapturedByWhite(ch);
        else game.addCapturedByBlack(ch);
    }
}