package com.sixchess.server;

import java.util.List;

/**
 * Holds the 15x15 board state and enforces game rules.
 * All moves are validated server-side for security.
 */
public class GameSession {

    public static final int BOARD_SIZE = 15;

    private final ChessType[][] board;
    private ChessType currentTurn;
    private int moveCount;
    private boolean gameOver;
    private String winner; // "BLACK", "WHITE", or "DRAW"

    public GameSession() {
        board = new ChessType[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = ChessType.NONE;
            }
        }
        currentTurn = ChessType.BLACK; // Black always goes first
        moveCount = 0;
        gameOver = false;
        winner = null;
    }

    /**
     * Reset the board for a new game. Colors swap for rematch.
     * @param firstTurn who goes first in the new game
     */
    public void reset(ChessType firstTurn) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = ChessType.NONE;
            }
        }
        currentTurn = firstTurn;
        moveCount = 0;
        gameOver = false;
        winner = null;
    }

    /**
     * Attempt to place a piece at (row, col) by the given color.
     *
     * @return PlaceResult with the outcome
     */
    public PlaceResult placePiece(int row, int col, ChessType color) {
        if (gameOver) {
            return PlaceResult.error("Game is already over");
        }
        if (color != currentTurn) {
            return PlaceResult.error("Not your turn. It is " + currentTurn + "'s turn");
        }
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            return PlaceResult.error("Position out of bounds: (" + row + "," + col + ")");
        }
        if (board[row][col] != ChessType.NONE) {
            return PlaceResult.error("Position already occupied: (" + row + "," + col + ")");
        }

        // Place the piece
        board[row][col] = color;
        moveCount++;

        // Check for win
        List<Point> winPositions = WinDetector.checkWin(board, row, col);
        if (winPositions != null) {
            gameOver = true;
            winner = color.toProtocolString();
            return PlaceResult.win(color.toProtocolString(), winPositions, moveCount);
        }

        // Check for draw
        if (WinDetector.isDraw(board)) {
            gameOver = true;
            winner = "DRAW";
            return PlaceResult.draw(moveCount);
        }

        // Switch turn
        currentTurn = currentTurn.opposite();
        return PlaceResult.placed(currentTurn.toProtocolString(), moveCount);
    }

    public ChessType getCurrentTurn() { return currentTurn; }
    public int getMoveCount() { return moveCount; }
    public boolean isGameOver() { return gameOver; }
    public String getWinner() { return winner; }
    public ChessType[][] getBoard() { return board; }

    /**
     * Result of a placePiece attempt.
     */
    public static class PlaceResult {
        public enum Type { PLACED, WIN, DRAW, ERROR }

        public final Type type;
        public final String nextTurn;
        public final int moveNumber;
        public final String winner;
        public final List<Point> winPositions;
        public final String errorMessage;

        private PlaceResult(Type type, String nextTurn, int moveNumber,
                            String winner, List<Point> winPositions, String errorMessage) {
            this.type = type;
            this.nextTurn = nextTurn;
            this.moveNumber = moveNumber;
            this.winner = winner;
            this.winPositions = winPositions;
            this.errorMessage = errorMessage;
        }

        public static PlaceResult placed(String nextTurn, int moveNumber) {
            return new PlaceResult(Type.PLACED, nextTurn, moveNumber, null, null, null);
        }

        public static PlaceResult win(String winner, List<Point> winPositions, int moveNumber) {
            return new PlaceResult(Type.WIN, null, moveNumber, winner, winPositions, null);
        }

        public static PlaceResult draw(int moveNumber) {
            return new PlaceResult(Type.DRAW, null, moveNumber, "DRAW", null, null);
        }

        public static PlaceResult error(String message) {
            return new PlaceResult(Type.ERROR, null, 0, null, null, message);
        }
    }
}