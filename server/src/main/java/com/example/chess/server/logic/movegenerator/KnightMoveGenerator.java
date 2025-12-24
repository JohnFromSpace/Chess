package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Knight;
import com.example.chess.common.pieces.Piece;

import java.util.List;

public class KnightMoveGenerator implements PieceMoveGenerator {

    @Override
    public boolean supports(Piece piece) {
        return piece instanceof Knight;
    }

    @Override
    public void generate(Game game, Board board, int fr, int fc, List<Move> out) {
        int[][] KN = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        Piece piece = board.getPieceAt(fr, fc);
        if (!(piece instanceof Knight)) return;

        for (int[] d : KN) {
            int tr = fr + d[0], tc = fc + d[1];
            if (!board.inside(tr, tc)) continue;

            Piece dst = board.getPieceAt(tr, tc);
            if (dst == null || dst.getColor() != piece.getColor()) {
                out.add(new Move(fr, fc, tr, tc, null));
            }
        }
    }
}