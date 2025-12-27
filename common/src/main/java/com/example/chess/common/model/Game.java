package com.example.chess.common.model;

import com.example.chess.common.board.Board;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private String id;
    private String whiteUser;
    private String blackUser;

    private boolean whiteMove = true;

    private long createdAt;
    private long lastUpdate;

    private long timeControlMs = 300_000L;
    private long incrementMs = 0L;

    private long whiteTimeMs = 300_000L;
    private long blackTimeMs = 300_000L;

    private long whiteOfflineSince = 0L;
    private long blackOfflineSince = 0L;

    private Result result = Result.ONGOING;
    private String resultReason;

    private String drawOfferedBy;

    private Board board = Board.initial();

    private List<String> moves = new ArrayList<>();
    private List<MoveEntry> moveHistory = new ArrayList<>();

    private boolean wK = true;
    private boolean wQ = true;
    private boolean bK = true;
    private boolean bQ = true;

    private int enPassantRow = -1;
    private int enPassantCol = -1;

    public static class MoveEntry {
        private String by;
        private String move;
        private long atMs;

        public MoveEntry(String by, String move, long atMs) {
            this.by = by;
            this.move = move;
            this.atMs = atMs;
        }

        public String getBy() { return by; }
        public String getMove() { return move; }
        public long getAtMs() { return atMs; }
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

    // ---- getters/setters ----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWhiteUser() { return whiteUser; }
    public void setWhiteUser(String whiteUser) { this.whiteUser = whiteUser; }

    public String getBlackUser() { return blackUser; }
    public void setBlackUser(String blackUser) { this.blackUser = blackUser; }

    public boolean isWhiteMove() { return whiteMove; }
    public void setWhiteMove(boolean whiteMove) { this.whiteMove = whiteMove; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    public long getTimeControlMs() { return timeControlMs; }
    public void setTimeControlMs(long timeControlMs) { this.timeControlMs = timeControlMs; }

    public long getIncrementMs() { return incrementMs; }
    public void setIncrementMs(long incrementMs) { this.incrementMs = incrementMs; }

    public long getWhiteTimeMs() { return whiteTimeMs; }
    public void setWhiteTimeMs(long whiteTimeMs) { this.whiteTimeMs = whiteTimeMs; }

    public long getBlackTimeMs() { return blackTimeMs; }
    public void setBlackTimeMs(long blackTimeMs) { this.blackTimeMs = blackTimeMs; }

    public long getWhiteOfflineSince() { return whiteOfflineSince; }
    public void setWhiteOfflineSince(long whiteOfflineSince) { this.whiteOfflineSince = whiteOfflineSince; }

    public long getBlackOfflineSince() { return blackOfflineSince; }
    public void setBlackOfflineSince(long blackOfflineSince) { this.blackOfflineSince = blackOfflineSince; }

    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }

    public String getResultReason() { return resultReason; }
    public void setResultReason(String resultReason) { this.resultReason = resultReason; }

    public String getDrawOfferedBy() { return drawOfferedBy; }
    public void setDrawOfferedBy(String drawOfferedBy) { this.drawOfferedBy = drawOfferedBy; }

    public Board getBoard() { return board; }
    public void setBoard(Board board) { this.board = board; }

    public List<String> getMoves() { return moves; }
    public void setMoves(List<String> moves) { this.moves = moves; }

    public List<MoveEntry> getMoveHistory() { return moveHistory; }
    public void setMoveHistory(List<MoveEntry> moveHistory) { this.moveHistory = moveHistory; }

    public boolean isWK() { return wK; }
    public void setWK(boolean wK) { this.wK = wK; }

    public boolean isWQ() { return wQ; }
    public void setWQ(boolean wQ) { this.wQ = wQ; }

    public boolean isBK() { return bK; }
    public void setBK(boolean bK) { this.bK = bK; }

    public boolean isBQ() { return bQ; }
    public void setBQ(boolean bQ) { this.bQ = bQ; }

    public int getEnPassantRow() { return enPassantRow; }
    public void setEnPassantRow(int enPassantRow) { this.enPassantRow = enPassantRow; }

    public int getEnPassantCol() { return enPassantCol; }
    public void setEnPassantCol(int enPassantCol) { this.enPassantCol = enPassantCol; }
}