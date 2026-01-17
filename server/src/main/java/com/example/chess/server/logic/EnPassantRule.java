package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;

public class EnPassantRule {

    public void clearEp(Game game) {
        if (game == null) throw new IllegalArgumentException("There is no game.");
        game.setEnPassantRow(-1);
        game.setEnPassantCol(-1);
    }

    public boolean isEnPassantCapture(Game game, Board board, Move m, Color mover) {
        if (game == null) throw new IllegalArgumentException("There is no game.");
        if (game.getEnPassantRow() != m.toRow || game.getEnPassantCol() != m.toCol) return false;

        Piece piece = board.getPieceAt(m.fromRow, m.fromCol);
        if (!(piece instanceof Pawn) || piece.getColor() != mover) return false;

        int dir = (mover == Color.WHITE) ? -1 : 1;
        int dr = m.toRow - m.fromRow;
        int dc = m.toCol - m.fromCol;

        if (dr != dir || Math.abs(dc) != 1) return false;
        if (!board.isEmptyAt(m.toRow, m.toCol)) return false;

        int capRow = (mover == Color.WHITE) ? m.toRow + 1 : m.toRow - 1;
        Piece cap = board.getPieceAt(capRow, m.toCol);
        return (cap instanceof Pawn) && cap.getColor() == mover.opposite();
    }

    public void applyEnPassant(Board board, Move m, Color mover, Piece pawn) {
        int capRow = (mover == Color.WHITE) ? m.toRow + 1 : m.toRow - 1;
        board.setPieceAt(capRow, m.toCol, null);
        board.setPieceAt(m.fromRow, m.fromCol, null);
        board.setPieceAt(m.toRow, m.toCol, pawn);
    }

    public void onPawnMoveMaybeSetTarget(Game game, Move move, Color mover) {
        if (game == null) throw new IllegalArgumentException("There is no game.");

        int startRow = (mover == Color.WHITE) ? 6 : 1;
        int dir = (mover == Color.WHITE) ? -1 : 1;

        if (move.fromRow == startRow &&
                move.toRow == startRow + 2 * dir &&
                move.fromCol == move.toCol) {
            game.setEnPassantRow(move.fromRow + dir);
            game.setEnPassantCol(move.fromCol);
        }
    }
}