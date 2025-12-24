package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.King;
import com.example.chess.common.pieces.Piece;
import com.example.chess.common.pieces.Rook;

public class CastlingRule {

    private final AttackService attacks;

    public CastlingRule(AttackService attacks) {
        this.attacks = attacks;
    }

    public boolean isCastleAttempt(Piece piece, Move move) {
        if (!(piece instanceof King)) return false;
        if (move.fromCol != 4) return false;
        if (move.fromRow != move.toRow) return false;
        return move.toCol == 6 || move.toCol == 2;
    }

    public boolean isLegalCastle(Game game, Board board, Color mover, boolean kingSide) {
        boolean white = mover == Color.WHITE;
        int row = white ? 7 : 0;

        // rights
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

        // empty between
        if (kingSide) {
            if (!board.isEmptyAt(row, 5) || !board.isEmptyAt(row, 6)) return false;
        } else {
            if (!board.isEmptyAt(row, 1) || !board.isEmptyAt(row, 2) || !board.isEmptyAt(row, 3)) return false;
        }

        // cannot be in check, cannot pass through attacked squares
        if (attacks.isKingInCheck(board, white)) return false;

        if (kingSide) {
            if (attacks.isSquareAttacked(board, row, 5, mover.opposite())) return false;
            if (attacks.isSquareAttacked(board, row, 6, mover.opposite())) return false;
        } else {
            if (attacks.isSquareAttacked(board, row, 3, mover.opposite())) return false;
            if (attacks.isSquareAttacked(board, row, 2, mover.opposite())) return false;
        }

        return true;
    }

    public void applyCastle(Board board, Game game, Color mover, boolean kingSide, Piece king, boolean updateState) {
        int row = (mover == Color.WHITE) ? 7 : 0;

        board.setPieceAt(row, 4, null);
        board.setPieceAt(row, kingSide ? 6 : 2, king);

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
    }

    public void onRookCaptured(Game game, Move move) {
        if (game == null) return;
        if (move.toRow == 7 && move.toCol == 0) game.wQ = false;
        if (move.toRow == 7 && move.toCol == 7) game.wK = false;
        if (move.toRow == 0 && move.toCol == 0) game.bQ = false;
        if (move.toRow == 0 && move.toCol == 7) game.bK = false;
    }

    public void onKingOrRookMoved(Game game, Piece piece, Move move, Color mover) {
        if (game == null || piece == null) return;

        if (piece instanceof King) {
            if (mover == Color.WHITE) { game.wK = false; game.wQ = false; }
            else { game.bK = false; game.bQ = false; }
            return;
        }

        if (piece instanceof Rook) {
            if (mover == Color.WHITE && move.fromRow == 7 && move.fromCol == 0) game.wQ = false;
            if (mover == Color.WHITE && move.fromRow == 7 && move.fromCol == 7) game.wK = false;
            if (mover == Color.BLACK && move.fromRow == 0 && move.fromCol == 0) game.bQ = false;
            if (mover == Color.BLACK && move.fromRow == 0 && move.fromCol == 7) game.bK = false;
        }
    }
}