package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Result;
import com.example.chess.server.core.ClockService;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;

final class MoveFlow {

    private final RulesEngine rules;
    private final ClockService clocks;
    private final GameStore store;
    private final GameFinisher finisher;

    MoveFlow(RulesEngine rules, ClockService clocks, GameStore store, GameFinisher finisher) {
        this.rules = rules;
        this.clocks = clocks;
        this.store = store;
        this.finisher = finisher;
    }

    void makeMoveLocked(GameContext ctx, User u, String uci) throws IOException {
        if (!ctx.isParticipant(u.getUsername())) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.game.getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        boolean moverIsWhite = ctx.isWhiteUser(u.getUsername());
        if (ctx.game.isWhiteMove() != moverIsWhite) throw new IllegalArgumentException("Not your turn.");

        Move move = Move.parse(uci);

        Board board = ctx.game.getBoard();
        if (!rules.isLegalMove(ctx.game, board, move))
            throw new IllegalArgumentException("Illegal move.");

        Board test = board.copy();
        rules.applyMove(test, ctx.game, move, false);
        if (rules.isKingInCheck(test, moverIsWhite))
            throw new IllegalArgumentException("Illegal move: your king would be in check.");

        rules.applyMove(board, ctx.game, move, true);

        ctx.game.recordMove(u.getUsername(), move.toString());

        clocks.onMoveApplied(ctx.game);

        boolean wChk = rules.isKingInCheck(board, true);
        boolean bChk = rules.isKingInCheck(board, false);

        if (ctx.game.getWhiteTimeMs() <= 0) {
            finisher.finishLocked(ctx, Result.BLACK_WIN, "Timeout.");
            return;
        }
        if (ctx.game.getBlackTimeMs() <= 0) {
            finisher.finishLocked(ctx, Result.WHITE_WIN, "Timeout.");
            return;
        }

        boolean whiteToMove = ctx.game.isWhiteMove();
        boolean inCheck = rules.isKingInCheck(board, whiteToMove);
        boolean anyLegal = rules.hasAnyLegalMove(ctx.game, board, whiteToMove);

        if (!anyLegal) {
            if (inCheck) {
                finisher.finishLocked(ctx, whiteToMove ? Result.BLACK_WIN : Result.WHITE_WIN, "Checkmate.");
            } else {
                finisher.finishLocked(ctx, Result.DRAW, "Stalemate.");
            }
            return;
        }

        store.save(ctx.game);

        if (ctx.white != null) ctx.white.pushMove(ctx.game, u.getUsername(), move.toString(), wChk, bChk);
        if (ctx.black != null) ctx.black.pushMove(ctx.game, u.getUsername(), move.toString(), wChk, bChk);
    }
}