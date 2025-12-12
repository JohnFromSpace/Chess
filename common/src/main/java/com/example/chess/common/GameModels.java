package com.example.chess.common;

import java.util.ArrayList;
import java.util.List;

import com.example.chess.common.board.Square;

public class GameModels {

    public enum Result { ONGOING, WHITE_WIN, BLACK_WIN, DRAW }

    public static class Move {
        public int fromRow, fromCol;
        public int toRow, toCol;

        public static Move parse(String moveStr) {
            if(moveStr.length() != 4) {
                throw new IllegalArgumentException("Move must be like e2e4");
            }

            Move m = new Move();
            m.fromCol = moveStr.charAt(0) - 'a';
            m.fromRow = 8 - (moveStr.charAt(1) - '0');
            m.toCol = moveStr.charAt(2) - 'a';
            m.toRow = 8 - (moveStr.charAt(3) - '0');

            return m;
        }
    }

    public static class Board {
        public char[][] squares = new char[8][8];

        public Board() {
            loadInitialPosition();
        }

        public void loadInitialPosition() {
            squares[0] = new char[] {'r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'};
            squares[1] = new char[] {'p', 'p', 'p', 'p', 'p', 'p', 'p', 'p'};
            squares[6] = new char[] {'P', 'P', 'P', 'P', 'P', 'P', 'P', 'P'};
            squares[7] = new char[] {'R', 'N', 'B', 'Q', 'K', 'B', 'N', 'R'};

            for (int i = 2; i <= 5; i++) {
                for (int j = 0; j < 8; j++) {
                    squares[i][j] = '.';
                }
            }
        }

        public char get(int row, int col) {
            return squares[row][col];
        }

        public void set(int row, int col, char piece) {
            squares[row][col] = piece;
        }
    }

    public static class Game {
        public String id;
        public String whiteUser;
        public String blackUser;

        public long timeControlMs = 5L * 60L * 1000L;
        public long incrementMs = 2L * 1000L;
        public long whiteTimeMs = timeControlMs;
        public long blackTimeMs = timeControlMs;

        public long whiteOfflineSince = 0L;
        public long blackOfflineSince = 0L;

        public Result result = Result.ONGOING;
        public String resultReason = "";

        public String drawOfferedBy;

        public long createdAt;
        public long lastUpdate;

        public Board board = new Board();
        public boolean whiteMove = true;

        public List<String> moves = new ArrayList<>();
    }

    public Square fromSquare() {
        return Square.of(fromRow, fromCol);
    }
    public Square toSquare() {
        return Square.of(toRow, toCol);
    }
}

