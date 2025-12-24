package com.example.chess.server.logic.movegenerator;

import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.pieces.Piece;

import java.util.List;

public interface PieceMoveGenerator {
    boolean supports(Piece piece);
    void generate(Game game, Board board, int fr, int fc, List<Move> out);
}