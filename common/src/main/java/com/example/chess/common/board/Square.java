package com.example.chess.common.board;

import java.util.Objects;

public final class Square {
    public final int row;
    public final int col;

    public Square(int row, int col) {
        this.row = row;
        this.col = col;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Square that)) return false;
        return row == that.row && col == that.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }
}