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
        if (move.getFromCol() != 4) return false;
        if (move.getFromRow() != move.getToRow()) return false;
        return move.getToCol() == 6 || move.getToCol() == 2;
    }

    public boolean isLegalCastle(Game game, Board board, Color mover, boolean kingSide) {
        boolean white = mover == Color.WHITE;
        int row = white ? 7 : 0;

        if (kingSide) {
            if (white && !game.isWK()) return false;
            if (!white && !game.isBK()) return false;
        } else {
            if (white && !game.isWQ()) return false;
            if (!white && !game.isBQ()) return false;
        }

        Piece king = board.getPieceAt(row, 4);
        if (!(king instanceof King) || king.getColor() != mover) return false;

        Piece rook = board.getPieceAt(row, kingSide ? 7 : 0);
        if (!(rook instanceof Rook) || rook.getColor() != mover) return false;

        if (kingSide) {
            if (!board.isEmptyAt(row, 5) || !board.isEmptyAt(row, 6)) return false;
        } else {
            if (!board.isEmptyAt(row, 1) || !board.isEmptyAt(row, 2) || !board.isEmptyAt(row, 3)) return false;
        }

        if (attacks.isKingInCheck(board, white)) return false;

        if (kingSide) {
            if (attacks.isSquareAttacked(board, row, 5, mover.opposite())) return false;
            return !attacks.isSquareAttacked(board, row, 6, mover.opposite());
        } else {
            if (attacks.isSquareAttacked(board, row, 3, mover.opposite())) return false;
            return !attacks.isSquareAttacked(board, row, 2, mover.opposite());
        }
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
            if (mover == Color.WHITE) { game.setWK(false); game.setWQ(false); }
            else { game.setBK(false); game.setBQ(false); }
        }
    }

    public void onRookCaptured(Game game, Move move) {
        if (game == null) throw new IllegalArgumentException("There is no game.");
        if (move.getToRow() == 7 && move.getToCol() == 0) game.setWQ(false);
        if (move.getToRow() == 7 && move.getToCol() == 7) game.setWK(false);
        if (move.getToRow() == 0 && move.getToCol() == 0) game.setBQ(false);
        if (move.getToRow() == 0 && move.getToCol() == 7) game.setBK(false);
    }

    public void onKingOrRookMoved(Game game, Piece piece, Move move, Color mover) {
        if (game == null || piece == null) throw new IllegalArgumentException("There is no game/piece.");

        if (piece instanceof King) {
            if (mover == Color.WHITE) { game.setWK(false); game.setWQ(false); }
            else { game.setBK(false); game.setBQ(false); }
            return;
        }

        if (piece instanceof Rook) {
            if (mover == Color.WHITE && move.getFromRow() == 7 && move.getFromCol() == 0) game.setWQ(false);
            if (mover == Color.WHITE && move.getFromRow() == 7 && move.getFromCol() == 7) game.setWK(false);
            if (mover == Color.BLACK && move.getFromRow() == 0 && move.getFromCol() == 0) game.setBQ(false);
            if (mover == Color.BLACK && move.getFromRow() == 0 && move.getFromCol() == 7) game.setBK(false);
        }
    }
}