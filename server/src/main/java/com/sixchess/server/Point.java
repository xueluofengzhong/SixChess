package com.sixchess.server;

/**
 * A grid position (row, col).
 */
public class Point {
    public int row;
    public int col;

    public Point() {}

    public Point(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return row == p.row && col == p.col;
    }

    @Override
    public int hashCode() {
        return 31 * row + col;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d)", row, col);
    }
}