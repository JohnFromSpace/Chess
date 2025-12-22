package com.example.chess.common.board;

public class Move {
    public int fromRow;
    public int fromCol;
    public int toRow;
    public int toCol;
    public Character promotion;

    public Move(int fr, int fc, int tr, int tc, Character promotion) {
        this.fromRow = fr;
        this.fromCol = fc;
        this.toRow = tr;
        this.toCol = tc;
        this.promotion = promotion;
    }

    public static Move parse(String uci) {
        if (uci == null) throw new IllegalArgumentException("Move is null");
        String s = uci.trim().toLowerCase();
        if (s.length() < 4) throw new IllegalArgumentException("Bad move: " + uci);

        int fc = s.charAt(0) - 'a';
        int fr = 8 - (s.charAt(1) - '0');
        int tc = s.charAt(2) - 'a';
        int tr = 8 - (s.charAt(3) - '0');
        Character promotion = null;
        if(s.length() == 5) {
            char p = s.charAt(4);
            if(p == 'q' || p == 'r' || p == 'n' || p == 'b') {
                promotion = p;
            }
            else {
                throw new IllegalArgumentException("Bad promotion piece: " + p);
            }
        }
        return new Move(fr, fc, tr, tc, promotion);
    }

    @Override public String toString() {
        String base = ""+(char)('a'+fromCol)+(char)('8'-fromRow)+(char)('a'+toCol)+(char)('8'-toRow);

        return promotion == null ? base : base + promotion;
    }
}