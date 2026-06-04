package com.sixchess.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.sixchess.R;
import com.sixchess.model.ChessType;
import com.sixchess.model.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Canvas View for the SixChess board.
 * Adapted from the reference Gomoku Chessboard.java.
 * 15x15 grid, 6-in-a-row to win.
 */
public class BoardView extends View {

    private static final int BOARD_SIZE = 15;
    private static final float PIECE_RATIO = 0.75f; // piece size relative to grid cell

    private int boardWidth;
    private float cellSize;
    private float offsetX;
    private float offsetY;
    private int pieceSize;

    private Paint gridPaint;
    private Paint bgPaint;
    private Paint blackPaint;
    private Paint whitePaint;
    private Paint highlightPaint;
    private Paint winLinePaint;
    private Paint starPaint;
    private int[][] stars;

    // 3D gradient piece paints
    private Paint blackGradientPaint;
    private Paint whiteGradientPaint;
    private Paint blackBorderPaint;
    private Paint whiteBorderPaint;

    // Last move tracking
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private Paint lastMovePaint;

    private ChessType[][] board;
    private ChessType myColor;
    private ChessType currentTurn;
    private boolean gameActive;
    private boolean isMyTurn;
    private List<Point> winPositions;

    private Bitmap blackPiece;
    private Bitmap whitePiece;

    private BoardListener listener;

    public interface BoardListener {
        void onCellClicked(int row, int col);
    }

    public BoardView(Context context) {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        board = new ChessType[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = ChessType.NONE;
            }
        }
        myColor = ChessType.NONE;
        currentTurn = ChessType.BLACK;
        gameActive = false;
        isMyTurn = false;
        winPositions = new ArrayList<>();

        // Star points
        starPaint = new Paint();
        starPaint.setColor(0xFF000000);
        starPaint.setStyle(Paint.Style.FILL);
        stars = new int[][]{{3,3}, {3,7}, {3,11}, {7,3}, {7,7}, {7,11}, {11,3}, {11,7}, {11,11}};

        // Background paint
        bgPaint = new Paint();
        bgPaint.setColor(0xFFDEB887); // Burlywood / wood color
        bgPaint.setStyle(Paint.Style.FILL);

        // Grid paint
        gridPaint = new Paint();
        gridPaint.setColor(0xFF000000);
        gridPaint.setStrokeWidth(2);
        gridPaint.setAntiAlias(true);

        // Piece paints
        blackPaint = new Paint();
        blackPaint.setColor(0xFF333333);
        blackPaint.setAntiAlias(true);
        blackPaint.setStyle(Paint.Style.FILL);

        whitePaint = new Paint();
        whitePaint.setColor(0xFFEEEEEE);
        whitePaint.setAntiAlias(true);
        whitePaint.setStyle(Paint.Style.FILL);

        // Piece border
        Paint borderPaint = new Paint();
        borderPaint.setColor(0xFF000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        borderPaint.setAntiAlias(true);

        // Highlight paint for last move
        highlightPaint = new Paint();
        highlightPaint.setColor(0x44FF0000);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setAntiAlias(true);

        // Win line paint
        winLinePaint = new Paint();
        winLinePaint.setColor(0xFFFF0000);
        winLinePaint.setStyle(Paint.Style.STROKE);
        winLinePaint.setStrokeWidth(6);
        winLinePaint.setAntiAlias(true);

        // Last move indicator paint
        lastMovePaint = new Paint();
        lastMovePaint.setColor(0x88FF4444);
        lastMovePaint.setStyle(Paint.Style.FILL);
        lastMovePaint.setAntiAlias(true);

        // Pre-create border paints
        blackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackBorderPaint.setColor(0xFF000000);
        blackBorderPaint.setStyle(Paint.Style.STROKE);
        blackBorderPaint.setStrokeWidth(2);

        whiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteBorderPaint.setColor(0xFF888888);
        whiteBorderPaint.setStyle(Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(2);

        // Try to load piece bitmaps, fall back to drawing circles
        try {
            blackPiece = BitmapFactory.decodeResource(getResources(), R.drawable.black_piece);
            whitePiece = BitmapFactory.decodeResource(getResources(), R.drawable.white_piece);
        } catch (Exception e) {
            blackPiece = null;
            whitePiece = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        boardWidth = Math.min(w, h);
        cellSize = boardWidth * 1.0f / (BOARD_SIZE + 1);
        offsetX = cellSize;
        offsetY = cellSize;
        pieceSize = (int) (cellSize * PIECE_RATIO / 2);

        // Create radial gradient shaders for 3D piece effect
        blackGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackGradientPaint.setStyle(Paint.Style.FILL);
        blackGradientPaint.setShader(new RadialGradient(
                0, 0, pieceSize,
                new int[]{0xFF666666, 0xFF333333, 0xFF111111},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP));

        whiteGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteGradientPaint.setStyle(Paint.Style.FILL);
        whiteGradientPaint.setShader(new RadialGradient(
                0, 0, pieceSize,
                new int[]{0xFFFFFFFF, 0xFFEEEEEE, 0xFFCCCCCC},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP));

        // Scale bitmaps
        if (blackPiece != null) {
            int size = (int) (cellSize * PIECE_RATIO);
            blackPiece = Bitmap.createScaledBitmap(blackPiece, size, size, true);
            whitePiece = Bitmap.createScaledBitmap(whitePiece, size, size, true);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawGrid(canvas);
        drawPieces(canvas);
        if (lastMoveRow >= 0 && lastMoveCol >= 0 && board[lastMoveRow][lastMoveCol] != ChessType.NONE) {
            drawLastMoveIndicator(canvas);
        }
        if (!winPositions.isEmpty()) {
            drawWinHighlight(canvas);
        }
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
    }

    private void drawGrid(Canvas canvas) {
        float start = offsetX;
        float end = offsetX + BOARD_SIZE * cellSize;

        for (int i = 0; i <= BOARD_SIZE; i++) {
            float pos = offsetX + i * cellSize;
            // Horizontal line
            canvas.drawLine(start, pos, end, pos, gridPaint);
            // Vertical line
            canvas.drawLine(pos, start, pos, end, gridPaint);
        }

        // Draw star points (天元 and corner stars)
        for (int[] s : stars) {
            float cx = offsetX + s[1] * cellSize; // col
            float cy = offsetY + s[0] * cellSize; // row
            canvas.drawCircle(cx, cy, pieceSize / 4, starPaint);
        }
    }

    private void drawPieces(Canvas canvas) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == ChessType.NONE) continue;

                float cx = offsetX + c * cellSize;
                float cy = offsetY + r * cellSize;

                if (board[r][c] == ChessType.BLACK) {
                    if (blackPiece != null) {
                        canvas.drawBitmap(blackPiece, cx - blackPiece.getWidth() / 2f,
                                cy - blackPiece.getHeight() / 2f, null);
                    } else if (blackGradientPaint != null) {
                        // 3D gradient piece
                        canvas.save();
                        canvas.translate(cx, cy);
                        canvas.drawCircle(0, 0, pieceSize, blackGradientPaint);
                        canvas.drawCircle(0, 0, pieceSize, blackBorderPaint);
                        canvas.restore();
                    } else {
                        // Fallback flat piece
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(0xFF333333);
                        p.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, pieceSize, p);
                        canvas.drawCircle(cx, cy, pieceSize, blackBorderPaint);
                    }
                } else {
                    if (whitePiece != null) {
                        canvas.drawBitmap(whitePiece, cx - whitePiece.getWidth() / 2f,
                                cy - whitePiece.getHeight() / 2f, null);
                    } else if (whiteGradientPaint != null) {
                        // 3D gradient piece
                        canvas.save();
                        canvas.translate(cx, cy);
                        canvas.drawCircle(0, 0, pieceSize, whiteGradientPaint);
                        canvas.drawCircle(0, 0, pieceSize, whiteBorderPaint);
                        canvas.restore();
                    } else {
                        // Fallback flat piece
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(0xFFEEEEEE);
                        p.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, pieceSize, p);
                        canvas.drawCircle(cx, cy, pieceSize, whiteBorderPaint);
                    }
                }
            }
        }
    }

    private void drawLastMoveIndicator(Canvas canvas) {
        float cx = offsetX + lastMoveCol * cellSize;
        float cy = offsetY + lastMoveRow * cellSize;
        canvas.drawCircle(cx, cy, pieceSize * 0.35f, lastMovePaint);
    }

    private void drawWinHighlight(Canvas canvas) {
        if (winPositions.size() < 2) return;

        // Draw highlight circles on winning positions
        for (Point p : winPositions) {
            float cx = offsetX + p.col * cellSize;
            float cy = offsetY + p.row * cellSize;
            canvas.drawCircle(cx, cy, pieceSize + 4, highlightPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!gameActive || !isMyTurn) return false;
        if (event.getAction() != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        // Find nearest intersection
        int col = Math.round((x - offsetX) / cellSize);
        int row = Math.round((y - offsetY) / cellSize);

        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return false;
        if (board[row][col] != ChessType.NONE) return false;

        if (listener != null) {
            listener.onCellClicked(row, col);
        }
        return true;
    }

    // --- Public API ---

    public void setBoardListener(BoardListener listener) {
        this.listener = listener;
    }

    public void setMyColor(ChessType color) {
        this.myColor = color;
    }

    public void setGameActive(boolean active) {
        this.gameActive = active;
    }

    public void setMyTurn(boolean myTurn) {
        this.isMyTurn = myTurn;
    }

    public void setCurrentTurn(ChessType turn) {
        this.currentTurn = turn;
    }

    public void placePiece(int row, int col, ChessType color) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
        board[row][col] = color;
        lastMoveRow = row;
        lastMoveCol = col;
        invalidate();
    }

    public void removePiece(int row, int col) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
        board[row][col] = ChessType.NONE;
        if (lastMoveRow == row && lastMoveCol == col) {
            lastMoveRow = -1;
            lastMoveCol = -1;
        }
        invalidate();
    }

    public void setWinPositions(List<Point> positions) {
        this.winPositions = positions != null ? positions : new ArrayList<>();
        invalidate();
    }

    public void resetBoard() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = ChessType.NONE;
            }
        }
        winPositions.clear();
        lastMoveRow = -1;
        lastMoveCol = -1;
        gameActive = false;
        isMyTurn = false;
        invalidate();
    }

    public ChessType getPieceAt(int row, int col) {
        return board[row][col];
    }
}