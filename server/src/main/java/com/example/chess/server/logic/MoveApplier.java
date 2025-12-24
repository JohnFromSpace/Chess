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

        // en passant
        if (game != null && piece instanceof Pawn && enPassant.isEnPassantCapture(game, board, move, mover)) {
            enPassant.applyEnPassant(board, move, mover, piece);
            return;
        }

        // rook captured on start square => update castling rights
        if (updateState && game != null && dst instanceof Rook) {
            castling.onRookCaptured(game, move);
        }

        // remove from source
        board.setPieceAt(move.fromRow, move.fromCol, null);

        // pawn double-step => set EP target
        if (updateState && game != null && piece instanceof Pawn) {
            enPassant.onPawnMoveMaybeSetTarget(game, move, mover);
        }

        // king/rook move => update castling rights
        if (updateState && game != null) {
            castling.onKingOrRookMoved(game, piece, move, mover);
        }

        // promotion
        if (piece instanceof Pawn && ((mover == Color.WHITE && move.toRow == 0) || (mover == Color.BLACK && move.toRow == 7))) {
            Piece promoted = PieceFactory.promotionPiece(mover, move.promotion);
            board.setPieceAt(move.toRow, move.toCol, promoted);
        } else {
            board.setPieceAt(move.toRow, move.toCol, piece);
        }
    }
}