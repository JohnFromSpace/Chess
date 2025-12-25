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
        if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        boolean moverIsWhite = ctx.isWhiteUser(u.username);
        if (ctx.game.whiteMove != moverIsWhite) throw new IllegalArgumentException("Not your turn.");

        Move move = Move.parse(uci);

        if (!rules.isLegalMove(ctx.game, ctx.game.board, move))
            throw new IllegalArgumentException("Illegal move.");

        // king-safety check (no self-check)
        Board test = ctx.game.board.copy();
        rules.applyMove(test, ctx.game, move, false);
        if (rules.isKingInCheck(test, moverIsWhite))
            throw new IllegalArgumentException("Illegal move: your king would be in check.");

        // apply for real (+ update castling/ep state)
        rules.applyMove(ctx.game.board, ctx.game, move, true);

        // record move with timestamp
        ctx.game.recordMove(u.username, move.toString());

        // update clock + flip side-to-move
        clocks.onMoveApplied(ctx.game);

        // check flags (after move)
        boolean wChk = rules.isKingInCheck(ctx.game.board, true);
        boolean bChk = rules.isKingInCheck(ctx.game.board, false);

        // timeout check
        if (ctx.game.whiteTimeMs <= 0) {
            finisher.finishLocked(ctx, Result.BLACK_WIN, "Time.");
            return;
        }
        if (ctx.game.blackTimeMs <= 0) {
            finisher.finishLocked(ctx, Result.WHITE_WIN, "Time.");
            return;
        }

        // mate/stalemate check for side-to-move after flip
        boolean whiteToMove = ctx.game.whiteMove;
        boolean inCheck = rules.isKingInCheck(ctx.game.board, whiteToMove);
        boolean anyLegal = rules.hasAnyLegalMove(ctx.game, ctx.game.board, whiteToMove);

        if (!anyLegal) {
            if (inCheck) {
                finisher.finishLocked(ctx, whiteToMove ? Result.BLACK_WIN : Result.WHITE_WIN, "Checkmate.");
            } else {
                finisher.finishLocked(ctx, Result.DRAW, "Stalemate.");
            }
            return;
        }

        // persist snapshot after each move (good for history/replay even if server dies)
        store.save(ctx.game);

        // push move to both (if connected)
        if (ctx.white != null) ctx.white.pushMove(ctx.game, u.username, move.toString(), wChk, bChk);
        if (ctx.black != null) ctx.black.pushMove(ctx.game, u.username, move.toString(), wChk, bChk);
    }
}