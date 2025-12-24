package com.example.chess.server.logic;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.server.logic.movegenerator.PseudoMoveGenerator;

public class RulesEngine {

    private final AttackService attacks;
    private final CastlingRule castling;
    private final EnPassantRule enPassant;
    private final MoveApplier applier;
    private final MoveLegalityChecker legality;
    private final PseudoMoveGenerator generator;

    public RulesEngine() {
        this.attacks = new AttackService();
        this.castling = new CastlingRule(attacks);
        this.enPassant = new EnPassantRule();
        this.applier = new MoveApplier(castling, enPassant);
        this.legality = new MoveLegalityChecker(attacks, castling, enPassant, applier);
        this.generator = PseudoMoveGenerator.defaultGenerator();
    }

    public boolean isLegalMove(Board board, Move move) {
        return legality.isLegalMove(board, move);
    }

    public boolean isLegalMove(Game game, Board board, Move move) {
        return legality.isLegalMove(game, board, move);
    }

    public void applyMove(Board board, Game game, Move move, boolean updateState) {
        applier.applyMove(board, game, move, updateState);
    }

    public boolean hasAnyLegalMove(Game game, Board board, boolean whiteToMove) {
        for (Move m : generator.generateAllPseudoMoves(game, board, whiteToMove)) {
            if (legality.isLegalMove(game, board, m)) return true; // legality already prevents self-check
        }
        return false;
    }

    public boolean isKingInCheck(Board b, boolean whiteKing) {
        return attacks.isKingInCheck(b, whiteKing);
    }
}