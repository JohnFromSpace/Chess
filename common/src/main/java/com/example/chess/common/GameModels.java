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
    }

    public static class Game {
        public String id;
        public String whiteUser;
        public String blackUser;
        public String initialFen = "startpos";
        public long timeControlMs = 300_000;
        public long incrementMs = 0;
        public List<Move> moves = new ArrayList<>();
        public Result result = Result.ONGOING;
        public String resultReason = "";
        public long createdAt;
        public long lastUpdate;
    }
}

