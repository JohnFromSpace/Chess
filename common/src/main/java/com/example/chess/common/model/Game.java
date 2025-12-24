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

    public List<MoveEntry> moveHistory = new ArrayList<>();

    public boolean wK = true; // White can castle king-side
    public boolean wQ = true; // White can castle queen-side
    public boolean bK = true; // Black can castle king-side
    public boolean bQ = true; // Black can castle queen-side

    // En-passant target square (the square "passed over"), or -1 if none
    public int enPassantRow = -1;
    public int enPassantCol = -1;

    public static class MoveEntry {
        public String by;     // username
        public String move;   // UCI
        public long atMs;     // epoch millis

        public MoveEntry() {}
        public MoveEntry(String by, String move, long atMs) {
            this.by = by;
            this.move = move;
            this.atMs = atMs;
        }
    }

    public boolean isWhite(String username) { return username != null && username.equals(whiteUser); }

    public String opponentOf(String username) {
        if (username == null) return null;
        if (username.equals(whiteUser)) return blackUser;
        if (username.equals(blackUser)) return whiteUser;
        return null;
    }

    public void recordMove(String by, String moveUci) {
        if (moveUci == null) return;
        if (moves == null) moves = new ArrayList<>();
        if (moveHistory == null) moveHistory = new ArrayList<>();

        moves.add(moveUci);
        moveHistory.add(new MoveEntry(by, moveUci, System.currentTimeMillis()));
        lastUpdate = System.currentTimeMillis();
    }

    public void ensureMoveHistory() {
        if (moveHistory != null && !moveHistory.isEmpty()) return;
        moveHistory = new ArrayList<>();
        if (moves == null) return;
        for (String m : moves) moveHistory.add(new MoveEntry(null, m, 0L));
    }
}