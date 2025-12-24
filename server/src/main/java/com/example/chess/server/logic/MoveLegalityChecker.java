package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.King;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;

public class MoveLegalityChecker {

    private final AttackService attacks;
    private final CastlingRule castling;
    private final EnPassantRule enPassant;
    private final MoveApplier applier;

    public MoveLegalityChecker(AttackService attacks, CastlingRule castling, EnPassantRule enPassant, MoveApplier applier) {
        this.attacks = attacks;
        this.castling = castling;
        this.enPassant = enPassant;
        this.applier = applier;
    }

    public boolean isLegalMove(Board board, Move move) {
        if (!basicBounds(board, move)) return false;

        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return false;

        Piece dst = board.getPieceAt(move.toRow, move.toCol);
        if (dst != null && dst.getColor() == piece.getColor()) return false;

        return piece.canMove(board, move);
    }

    public boolean isLegalMove(Game game, Board board, Move move) {
        if (game == null) return isLegalMove(board, move);
        if (!basicBounds(board, move)) return false;

        Piece piece = board.getPieceAt(move.fromRow, move.fromCol);
        if (piece == null) return false;

        Piece dst = board.getPieceAt(move.toRow, move.toCol);
        if (dst != null && dst.getColor() == piece.getColor()) return false;

        Color mover = piece.getColor();

        boolean ok;

        // castling attempt
        if (piece instanceof King && castling.isCastleAttempt(piece, move)) {
            boolean kingSide = (move.toCol == 6);
            ok = castling.isLegalCastle(game, board, mover, kingSide);
        }
        // en passant
        else if (piece instanceof Pawn && enPassant.isEnPassantCapture(game, board, move, mover)) {
            ok = true;
        }
        // normal/promotion geometry
        else {
            ok = piece.canMove(board, move);
        }

        if (!ok) return false;

        // self-check prevention
        Board test = board.copy();
        applier.applyMove(test, game, move, false);
        boolean moverWhite = (mover == Color.WHITE);
        return !attacks.isKingInCheck(test, moverWhite);
    }

    private boolean basicBounds(Board board, Move move) {
        if (board == null || move == null) return false;
        if (!board.inside(move.fromRow, move.fromCol) || !board.inside(move.toRow, move.toCol)) return false;
        return !(move.fromRow == move.toRow && move.fromCol == move.toCol);
    }
}