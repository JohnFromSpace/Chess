package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
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

    Runnable makeMoveLocked(GameContext ctx, User u, String uci) throws IOException {
        if (!Thread.holdsLock(ctx)) throw new IllegalStateException("Game context must be locked.");
        if (!ctx.isParticipant(u.getUsername())) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.game.getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        String by = u.getUsername();
        boolean moverIsWhite = ctx.isWhiteUser(by);
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

        String moveStr = move.toString();
        ctx.game.recordMove(by, moveStr);

        clocks.onMoveApplied(ctx.game);

        boolean wChk = rules.isKingInCheck(board, true);
        boolean bChk = rules.isKingInCheck(board, false);

        if (ctx.game.getWhiteTimeMs() <= 0) {
            return finisher.finishLocked(ctx, Result.BLACK_WIN, "Timeout.");
        }
        if (ctx.game.getBlackTimeMs() <= 0) {
            return finisher.finishLocked(ctx, Result.WHITE_WIN, "Timeout.");
        }

        boolean whiteToMove = ctx.game.isWhiteMove();
        boolean inCheck = rules.isKingInCheck(board, whiteToMove);
        boolean anyLegal = rules.hasAnyLegalMove(ctx.game, board, whiteToMove);

        if (!anyLegal) {
            if (inCheck) {
                return finisher.finishLocked(ctx, whiteToMove ? Result.BLACK_WIN : Result.WHITE_WIN, "Checkmate.");
            } else {
                return finisher.finishLocked(ctx, Result.DRAW, "Stalemate.");
            }
        }

        store.save(ctx.game);

        ClientHandler white = ctx.white;
        ClientHandler black = ctx.black;
        Game game = ctx.game;
        return moveNotification(game, white, black, by, moveStr, wChk, bChk);
    }

    private static Runnable moveNotification(Game game,
                                             ClientHandler white,
                                             ClientHandler black,
                                             String by,
                                             String move,
                                             boolean wChk,
                                             boolean bChk) {
        if (white == null && black == null) return null;
        return () -> {
            if (white != null) white.pushMove(game, by, move, wChk, bChk);
            if (black != null) black.pushMove(game, by, move, wChk, bChk);
        };
    }
}
