package com.example.chess.common;

import java.util.ArrayList;
import java.util.List;

public class GameModels {

    public enum Result { ONGOING, WHITE_WIN, BLACK_WIN, DRAW }

    public static class Move {
        public String uci;        // e2e4, e7e8q, O-O, etc.
        public String san;        // optional pretty notation
        public long whiteMs;      // white's clock after the move
        public long blackMs;      // black's clock after the move

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
            String[] rows = new String[] {
                    "rnbqkbnr",
                    "pppppppp",
                    "........",
                    "........",
                    "........",
                    "........",
                    "PPPPPPPP",
                    "RNBQKBNR"
            };

            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    squares[i][j] = rows[i].charAt(j);
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

        public String initialFen = "startpos";
        public long timeControlMs = 5 * 6 * 1000L;
        public long incrementMs = 2 * 1000L;
        public long whiteTimeMs = timeControlMs;
        public long blackTimeMs = timeControlMs;

        public List<Move> moves = new ArrayList<>();
        public Result result = Result.ONGOING;
        public String resultReason = "";

        public String drawOfferedBy;

        public long createdAt;
        public long lastUpdate;

        public Board board = new Board();
        public boolean whiteMove = true;


    }
}

