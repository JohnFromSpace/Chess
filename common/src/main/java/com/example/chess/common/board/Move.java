package com.example.chess.common.board;

public class Move {
    private final int fromRow;
    private final int fromCol;
    private final int toRow;
    private final int toCol;
    private final Character promotion;

    public int getFromRow() {
        return fromRow;
    }

    public int getFromCol() {
        return fromCol;
    }

    public int getToRow() {
        return toRow;
    }

    public int getToCol() {
        return toCol;
    }

    public Character getPromotion() {
        return promotion;
    }

    public Move(int fr, int fc, int tr, int tc, Character promotion) {
        this.fromRow = fr;
        this.fromCol = fc;
        this.toRow = tr;
        this.toCol = tc;
        this.promotion = promotion;
    }

    public static Move parse(String text) {
        if (text == null) throw new IllegalArgumentException("Move is null");

        // Accept inputs like "e5xe4", "e7-e8q", "e7e8=Q", spaces, etc.
        String s = text.trim().toLowerCase();
        s = s.replaceAll("[\\s\\-x=:+]", ""); // remove separators

        if (s.length() < 4) throw new IllegalArgumentException("Bad move: " + text);
        if (s.length() > 5) throw new IllegalArgumentException("Bad move: " + text);

        int fc = s.charAt(0) - 'a';
        int fr = 8 - (s.charAt(1) - '0');
        int tc = s.charAt(2) - 'a';
        int tr = 8 - (s.charAt(3) - '0');

        if (fc < 0 || fc > 7 || tc < 0 || tc > 7 || fr < 0 || fr > 7 || tr < 0 || tr > 7)
            throw new IllegalArgumentException("Bad move: " + text);

        Character promotion = null;
        if (s.length() == 5) {
            char p = s.charAt(4);
            if (p == 'q' || p == 'r' || p == 'n' || p == 'b') promotion = p;
            else throw new IllegalArgumentException("Bad promotion piece: " + p);
        }

        return new Move(fr, fc, tr, tc, promotion);
    }

    @Override public String toString() {
        String base = ""+(char)('a'+fromCol)+(char)('8'-fromRow)+(char)('a'+toCol)+(char)('8'-toRow);
        return promotion == null ? base : base + promotion;
    }
}