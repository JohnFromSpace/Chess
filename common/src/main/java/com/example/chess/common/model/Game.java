package com.example.chess.common.model;

import com.example.chess.common.board.Board;
import java.util.ArrayList;
import java.util.List;

public class Game {
    public String id;
    public String whiteUser;
    public String blackUser;

    public boolean whiteMove = true;

    public long createdAt;
    public long lastUpdate;

    public long timeControlMs = 300_000L;
    public long incrementMs = 0L;

    public long whiteTimeMs = 300_000L;
    public long blackTimeMs = 300_000L;

    public long whiteOfflineSince = 0L;
    public long blackOfflineSince = 0L;

    public Result result = Result.ONGOING;
    public String resultReason;

    public String drawOfferedBy;

    public Board board = Board.initial();
    public List<String> moves = new ArrayList<>();

    public boolean isWhite(String username) { return username != null && username.equals(whiteUser); }

    public String opponentOf(String username) {
        if (username == null) return null;
        if (username.equals(whiteUser)) return blackUser;
        if (username.equals(blackUser)) return whiteUser;
        return null;
    }
}