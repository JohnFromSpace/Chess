package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Piece;

import java.util.ArrayList;
import java.util.List;

public class PseudoMoveGenerator {

    private final List<PieceMoveGenerator> gens;

    public PseudoMoveGenerator(List<PieceMoveGenerator> gens) {
        this.gens = gens == null ? List.of() : List.copyOf(gens);
    }

    public static PseudoMoveGenerator defaultGenerator() {
        List<PieceMoveGenerator> g = new ArrayList<>();
        g.add(new PawnMoveGenerator());
        g.add(new KnightMoveGenerator());
        g.add(new SlidingMoveGenerator(com.example.chess.common.pieces.Bishop.class, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}}));
        g.add(new SlidingMoveGenerator(com.example.chess.common.pieces.Rook.class,   new int[][]{{-1,0},{1,0},{0,-1},{0,1}}));
        g.add(new SlidingMoveGenerator(com.example.chess.common.pieces.Queen.class,  new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}}));
        g.add(new KingMoveGenerator());
        return new PseudoMoveGenerator(g);
    }

    public List<Move> generateAllPseudoMoves(Game game, Board board, boolean whiteToMove) {
        List<Move> out = new ArrayList<>();
        Color mover = whiteToMove ? Color.WHITE : Color.BLACK;

        for (int fr = 0; fr < 8; fr++) {
            for (int fc = 0; fc < 8; fc++) {
                Piece piece = board.getPieceAt(fr, fc);
                if (piece == null || piece.getColor() != mover) continue;

                for (PieceMoveGenerator gen : gens) {
                    if (gen.supports(piece)) {
                        gen.generate(game, board, fr, fc, out);
                        break;
                    }
                }
            }
        }
        return out;
    }
}