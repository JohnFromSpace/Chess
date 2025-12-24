package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Piece;

import java.util.List;

public class SlidingMoveGenerator implements PieceMoveGenerator {

    private final Class<? extends Piece> supported;
    private final int[][] dirs;

    public SlidingMoveGenerator(Class<? extends Piece> supported, int[][] dirs) {
        this.supported = supported;
        this.dirs = dirs;
    }

    @Override
    public boolean supports(Piece piece) {
        return supported.isInstance(piece);
    }

    @Override
    public void generate(Game game, Board board, int fr, int fc, List<Move> out) {
        Piece piece = board.getPieceAt(fr, fc);
        if (!supports(piece)) return;

        for (int[] d : dirs) {
            int r = fr + d[0], c = fc + d[1];
            while (board.inside(r, c)) {
                Piece dst = board.getPieceAt(r, c);
                if (dst == null) {
                    out.add(new Move(fr, fc, r, c, null));
                } else {
                    if (dst.getColor() != piece.getColor()) out.add(new Move(fr, fc, r, c, null));
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
    }
}