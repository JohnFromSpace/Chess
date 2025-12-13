package com.example.chess.common.board;

public class Move {
    public int fromRow;
    public int fromCol;
    public int toRow;
    public int toCol;

    public Move(int fr, int fc, int tr, int tc) {
        this.fromRow=fr; this.fromCol=fc; this.toRow=tr; this.toCol=tc;
    }

    public static Move parse(String uci) {
        if (uci == null) throw new IllegalArgumentException("Move is null");
        String s = uci.trim().toLowerCase();
        if (s.length() < 4) throw new IllegalArgumentException("Bad move: " + uci);

        int fc = s.charAt(0) - 'a';
        int fr = 8 - (s.charAt(1) - '0');
        int tc = s.charAt(2) - 'a';
        int tr = 8 - (s.charAt(3) - '0');

        return new Move(fr, fc, tr, tc);
    }

    @Override public String toString() {
        return ""+(char)('a'+fromCol)+(char)('8'-fromRow)+(char)('a'+toCol)+(char)('8'-toRow);
    }
}