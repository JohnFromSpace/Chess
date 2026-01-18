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
        if (game.getEnPassantRow() != m.getToRow() || game.getEnPassantCol() != m.getToCol()) return false;

        Piece piece = board.getPieceAt(m.getFromRow(), m.getFromCol());
        if (!(piece instanceof Pawn) || piece.getColor() != mover) return false;

        int dir = (mover == Color.WHITE) ? -1 : 1;
        int dr = m.getToRow() - m.getFromRow();
        int dc = m.getToCol() - m.getFromCol();

        if (dr != dir || Math.abs(dc) != 1) return false;
        if (!board.isEmptyAt(m.getToRow(), m.getToCol())) return false;

        int capRow = (mover == Color.WHITE) ? m.getToRow() + 1 : m.getToRow() - 1;
        Piece cap = board.getPieceAt(capRow, m.getToCol());
        return (cap instanceof Pawn) && cap.getColor() == mover.opposite();
    }

    public void applyEnPassant(Board board, Move m, Color mover, Piece pawn) {
        int capRow = (mover == Color.WHITE) ? m.getToRow() + 1 : m.getToRow() - 1;
        board.setPieceAt(capRow, m.getToCol(), null);
        board.setPieceAt(m.getFromRow(), m.getFromCol(), null);
        board.setPieceAt(m.getToRow(), m.getToCol(), pawn);
    }

    public void onPawnMoveMaybeSetTarget(Game game, Move move, Color mover) {
        if (game == null) throw new IllegalArgumentException("There is no game.");

        int startRow = (mover == Color.WHITE) ? 6 : 1;
        int dir = (mover == Color.WHITE) ? -1 : 1;

        if (move.getFromRow() == startRow &&
                move.getToRow() == startRow + 2 * dir &&
                move.getFromCol() == move.getToCol()) {
            game.setEnPassantRow(move.getFromRow() + dir);
            game.setEnPassantCol(move.getFromCol());
        }
    }
}