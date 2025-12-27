package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.King;
import com.example.chess.common.pieces.Piece;

import java.util.List;

public class KingMoveGenerator implements PieceMoveGenerator {

    @Override
    public boolean supports(Piece piece) {
        return piece instanceof King;
    }

    @Override
    public void generate(Game game, Board board, int fr, int fc, List<Move> out) {
        Piece piece = board.getPieceAt(fr, fc);
        if (!(piece instanceof King)) return;

        Color mover = piece.getColor();

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int tr = fr + dr, tc = fc + dc;
                if (!board.inside(tr, tc)) continue;

                Piece dst = board.getPieceAt(tr, tc);
                if (dst == null || dst.getColor() != mover) out.add(new Move(fr, fc, tr, tc, null));
            }
        }

        if (game != null && fc == 4) {
            if (mover == Color.WHITE && fr == 7) {
                if (game.isWK()) out.add(new Move(7, 4, 7, 6, null));
                if (game.isWQ()) out.add(new Move(7, 4, 7, 2, null));
            } else if (mover == Color.BLACK && fr == 0) {
                if (game.isBK()) out.add(new Move(0, 4, 0, 6, null));
                if (game.isBQ()) out.add(new Move(0, 4, 0, 2, null));
            }
        }
    }
}