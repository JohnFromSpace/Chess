package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Color;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Pawn;
import com.example.chess.common.pieces.Piece;

import java.util.List;

public class PawnMoveGenerator implements PieceMoveGenerator {

    @Override
    public boolean supports(Piece piece) {
        return piece instanceof Pawn;
    }

    @Override
    public void generate(Game game, Board board, int fr, int fc, List<Move> out) {
        Piece p = board.getPieceAt(fr, fc);
        if (!(p instanceof Pawn)) return;

        Color mover = p.getColor();
        int dir = (mover == Color.WHITE) ? -1 : 1;
        int startRow = (mover == Color.WHITE) ? 6 : 1;
        int lastRow  = (mover == Color.WHITE) ? 0 : 7;

        int r1 = fr + dir;

        // forward 1
        if (board.inside(r1, fc) && board.isEmptyAt(r1, fc)) {
            if (r1 == lastRow) addPromotionSet(out, fr, fc, r1, fc);
            else out.add(new Move(fr, fc, r1, fc, null));
        }

        // forward 2
        int r2 = fr + 2 * dir;
        if (fr == startRow && board.inside(r2, fc) && board.isEmptyAt(r1, fc) && board.isEmptyAt(r2, fc)) {
            out.add(new Move(fr, fc, r2, fc, null));
        }

        // captures + EP target
        for (int dc : new int[]{-1, 1}) {
            int tc = fc + dc;
            int tr = fr + dir;
            if (!board.inside(tr, tc)) continue;

            Piece dst = board.getPieceAt(tr, tc);
            if (dst != null && dst.getColor() == mover.opposite()) {
                if (tr == lastRow) addPromotionSet(out, fr, fc, tr, tc);
                else out.add(new Move(fr, fc, tr, tc, null));
            }

            if (game != null && game.enPassantRow == tr && game.enPassantCol == tc && board.isEmptyAt(tr, tc)) {
                out.add(new Move(fr, fc, tr, tc, null));
            }
        }
    }

    private void addPromotionSet(List<Move> out, int fr, int fc, int tr, int tc) {
        out.add(new Move(fr, fc, tr, tc, 'q'));
        out.add(new Move(fr, fc, tr, tc, 'r'));
        out.add(new Move(fr, fc, tr, tc, 'b'));
        out.add(new Move(fr, fc, tr, tc, 'n'));
        out.add(new Move(fr, fc, tr, tc, null)); // allow “no suffix”
    }
}