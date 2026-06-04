package com.sixchess.server;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-in-a-row win detection.
 * Checks 4 directions from the last placed piece, returns winning positions.
 */
public class WinDetector {

    private static final int WIN_COUNT = 6;
    private static final int BOARD_SIZE = 15;

    // Direction vectors: right, down, down-right, down-left
    private static final int[][] DIRECTIONS = {
        { 0,  1},  // horizontal
        { 1,  0},  // vertical
        { 1,  1},  // diagonal down-right
        { 1, -1}   // diagonal down-left
    };

    /**
     * Check if placing a piece at (row, col) creates a 6-in-a-row win.
     *
     * @param board the current board state
     * @param row   row of the last placed piece
     * @param col   column of the last placed piece
     * @return list of winning positions (6 cells), or null if no win
     */
    public static List<Point> checkWin(ChessType[][] board, int row, int col) {
        ChessType color = board[row][col];
        if (color == null || color == ChessType.NONE) return null;

        for (int[] dir : DIRECTIONS) {
            int dr = dir[0], dc = dir[1];

            // Scan backward (negative direction) first to collect positions in order
            List<Point> line = new ArrayList<>();
            for (int step = WIN_COUNT - 1; step >= 1; step--) {
                int r = row - dr * step;
                int c = col - dc * step;
                if (!inBounds(r, c) || board[r][c] != color) break;
                line.add(new Point(r, c));
            }

            // Add the placed piece
            line.add(new Point(row, col));

            // Scan forward (positive direction)
            for (int step = 1; step < WIN_COUNT; step++) {
                int r = row + dr * step;
                int c = col + dc * step;
                if (!inBounds(r, c) || board[r][c] != color) break;
                line.add(new Point(r, c));
            }

            if (line.size() >= WIN_COUNT) {
                return line.subList(0, WIN_COUNT);
            }
        }
        return null;
    }

    /**
     * Check if the board is completely full (draw).
     */
    public static boolean isDraw(ChessType[][] board) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == null || board[r][c] == ChessType.NONE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE;
    }
}