package com.sixchess.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.sixchess.R;
import com.sixchess.model.ChessType;
import com.sixchess.model.Point;
import com.sixchess.network.Message;
import com.sixchess.network.WebSocketManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main game screen. Hosts the BoardView and handles game state.
 */
public class GameActivity extends AppCompatActivity implements WebSocketManager.MessageListener, BoardView.BoardListener {

    private static final String[] DANMAKU_MESSAGES = {
        "求求你让让我吧🥺",
        "大佬带带我🙏",
        "我错了下次不敢了",
        "手下留情啊❤️",
        "别打了别打了",
        "再这样我要哭了😭",
        "让我赢一把吧求求了",
        "你太强了我瑟瑟发抖",
        "饶命啊大佬",
        "给条活路吧😅",
        "我太难了😫",
        "放点水吧求求了",
        "大佬您悠着点",
        "我已经很努力了…",
        "你忍心赢我吗🥺"
    };

    private BoardView boardView;
    private TextView turnText;
    private TextView statusText;
    private Button undoBtn;
    private Button begBtn;
    private Button rematchBtn;
    private Button leaveBtn;
    private FrameLayout danmakuContainer;

    private WebSocketManager ws;
    private ChessType myColor;
    private boolean gameActive;
    private boolean rematchRequested;
    private boolean destroyed;
    private boolean leaving;
    private boolean undoRequested;
    private final Random danmakuRandom = new Random();

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        boardView = findViewById(R.id.board_view);
        turnText = findViewById(R.id.tv_turn);
        statusText = findViewById(R.id.tv_game_status);
        undoBtn = findViewById(R.id.btn_undo);
        begBtn = findViewById(R.id.btn_beg);
        rematchBtn = findViewById(R.id.btn_rematch);
        leaveBtn = findViewById(R.id.btn_leave);
        danmakuContainer = findViewById(R.id.danmaku_overlay);

        // Get my color from intent
        String colorStr = getIntent().getStringExtra("myColor");
        myColor = ChessType.fromProtocol(colorStr != null ? colorStr : "BLACK");

        boardView.setBoardListener(this);
        boardView.setMyColor(myColor);

        ws = WebSocketManager.getInstance();
        ws.setListener(this); // Swap listener without reconnecting

        // Trigger game start UI immediately since we're already connected
        boardView.setGameActive(true);
        boardView.setMyTurn(myColor == ChessType.BLACK);
        gameActive = true;
        statusText.setText("游戏开始！");
        turnText.setText(myColor == ChessType.BLACK ? "轮到你了 (黑棋)" : "等待对手 (白棋)");

        rematchRequested = false;
        undoRequested = false;

        undoBtn.setOnClickListener(v -> {
            if (undoRequested) return;
            undoRequested = true;
            undoBtn.setEnabled(false);
            ws.requestUndo();
            statusText.setText("等待对手同意悔棋...");
        });

        begBtn.setOnClickListener(v -> ws.sendBeg());

        rematchBtn.setEnabled(false);
        rematchBtn.setOnClickListener(v -> {
            ws.requestRematch();
            rematchBtn.setEnabled(false);
            rematchRequested = true;
            statusText.setText("等待对手同意复赛...");
        });

        leaveBtn.setOnClickListener(v -> {
            if (leaving) return;
            leaving = true;
            ws.leaveRoom();
            handler.postDelayed(() -> {
                ws.disconnect();
                finish();
            }, 300);
        });

        updateTurnDisplay();
    }

    private void updateTurnDisplay() {
        if (myColor == ChessType.BLACK) {
            turnText.setText("你执黑棋 (先手)");
        } else {
            turnText.setText("你执白棋 (后手)");
        }
    }

    // --- BoardView.BoardListener ---

    @Override
    public void onCellClicked(int row, int col) {
        if (!gameActive) return;
        ws.placePiece(row, col);
        // Board will be updated when server confirms with PIECE_PLACED
    }

    // --- WebSocket message handling ---

    @Override
    public void onConnected() {
        // Already connected from Launcher, no action needed
    }

    @Override
    public void onDisconnected(String reason) {
        postToUi(() -> {
            Toast.makeText(this, "连接断开: " + reason, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onMessage(Message msg) {
        switch (msg.type) {
            case Message.GAME_START:
                handleGameStart();
                break;
            case Message.PIECE_PLACED:
                handlePiecePlaced(msg);
                break;
            case Message.GAME_OVER:
                handleGameOver(msg);
                break;
            case Message.PLAYER_DISCONNECTED:
                handlePlayerDisconnected(msg);
                break;
            case Message.REMATCH_REQUEST:
                handleRematchRequest(msg);
                break;
            case Message.REMATCH_READY:
                handleRematchReady(msg);
                break;
            case Message.REMATCH_DECLINED:
                handleRematchDeclined();
                break;
            case Message.UNDO_REQUEST:
                handleUndoRequest(msg);
                break;
            case Message.UNDO_APPLIED:
                handleUndoApplied(msg);
                break;
            case Message.UNDO_DECLINED:
                handleUndoDeclined();
                break;
            case Message.BEG:
                handleBeg();
                break;
            case Message.ERROR:
                handleError(msg);
                break;
            case Message.PING:
                ws.sendPong();
                break;
        }
    }

    private void handleGameStart() {
        postToUi(() -> {
            gameActive = true;
            boardView.setGameActive(true);
            boardView.setMyTurn(myColor == ChessType.BLACK);
            undoBtn.setEnabled(true);
            begBtn.setEnabled(true);
            statusText.setText("游戏开始！");
            turnText.setText(myColor == ChessType.BLACK ? "轮到你了 (黑棋)" : "等待对手 (白棋)");
        });
    }

    private void handlePiecePlaced(Message msg) {
        int row = msg.getDataInt("row");
        int col = msg.getDataInt("col");
        String colorStr = msg.getDataString("color");
        String nextTurn = msg.getDataString("nextTurn");
        ChessType color = ChessType.fromProtocol(colorStr);

        // Bounds check before array access
        if (row < 0 || row >= 15 || col < 0 || col >= 15) return;

        postToUi(() -> {
            boardView.placePiece(row, col, color);

            boolean isMyTurn = nextTurn != null && nextTurn.equals(myColor.toProtocol());
            boardView.setMyTurn(isMyTurn);

            if (isMyTurn) {
                turnText.setText("轮到你了！");
            } else {
                turnText.setText("等待对手...");
            }
        });
    }

    private void handleGameOver(Message msg) {
        String winner = msg.getDataString("winner");
        String reason = msg.getDataString("reason");

        // Parse winning positions
        List<Point> winPositions = new ArrayList<>();
        if (msg.data.has("winPositions")) {
            JsonArray arr = msg.data.getAsJsonArray("winPositions");
            for (JsonElement e : arr) {
                Point p = new Point(
                    e.getAsJsonObject().get("row").getAsInt(),
                    e.getAsJsonObject().get("col").getAsInt()
                );
                winPositions.add(p);
            }
        }

        postToUi(() -> {
            gameActive = false;
            boardView.setGameActive(false);
            boardView.setWinPositions(winPositions);
            undoBtn.setEnabled(true); // allow undo even after game over
            begBtn.setEnabled(false);

            String message;
            if ("DRAW".equals(winner)) {
                message = "平局！棋盘已满。";
            } else if (winner != null && winner.equals(myColor.toProtocol())) {
                message = "你赢了！六子连珠！";
            } else if (winner != null) {
                message = "你输了！";
            } else {
                message = "游戏结束";
            }

            statusText.setText(message);
            rematchBtn.setEnabled(!rematchRequested);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void handlePlayerDisconnected(Message msg) {
        String message = msg.getDataString("message");
        postToUi(() -> {
            gameActive = false;
            boardView.setGameActive(false);
            statusText.setText(message != null ? message : "对手已断开连接");
            rematchBtn.setEnabled(false);
            new MaterialAlertDialogBuilder(this)
                .setTitle("游戏结束")
                .setMessage("对手已断开连接。")
                .setPositiveButton("返回", (d, w) -> {
                    ws.disconnect();
                    finish();
                })
                .setCancelable(false)
                .show();
        });
    }

    private void handleRematchRequest(Message msg) {
        postToUi(() -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("复赛请求")
                .setMessage("对手想再来一局，是否同意？")
                .setPositiveButton("同意", (d, w) -> ws.requestRematch())
                .setNegativeButton("拒绝", (d, w) -> {
                    ws.leaveRoom();
                    ws.disconnect();
                    finish();
                })
                .show();
        });
    }

    private void handleRematchReady(Message msg) {
        String colorStr = msg.getDataString("yourColor");
        myColor = ChessType.fromProtocol(colorStr);

        postToUi(() -> {
            boardView.resetBoard();
            boardView.setMyColor(myColor);
            boardView.setGameActive(true);
            boardView.setMyTurn(myColor == ChessType.BLACK);
            gameActive = true;
            rematchRequested = false;
            undoRequested = false;
            rematchBtn.setEnabled(false);
            undoBtn.setEnabled(true);
            begBtn.setEnabled(true);
            updateTurnDisplay();
            statusText.setText("复赛开始！");
            if (myColor == ChessType.BLACK) {
                turnText.setText("轮到你了 (黑棋)");
            } else {
                turnText.setText("等待对手 (白棋)");
            }
        });
    }

    private void handleRematchDeclined() {
        postToUi(() -> {
            rematchRequested = false;
            rematchBtn.setEnabled(true);
            statusText.setText("对手拒绝了复赛请求");
            Toast.makeText(this, "对手拒绝了复赛请求", Toast.LENGTH_SHORT).show();
        });
    }

    private void handleUndoRequest(Message msg) {
        String fromPlayer = msg.getDataString("fromPlayer");
        postToUi(() -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("悔棋请求")
                .setMessage((fromPlayer != null ? fromPlayer : "对手") + " 请求悔棋，是否同意？")
                .setPositiveButton("同意", (d, w) -> {
                    ws.respondUndo(true);
                    statusText.setText("已同意悔棋");
                })
                .setNegativeButton("拒绝", (d, w) -> ws.respondUndo(false))
                .setCancelable(false)
                .show();
        });
    }

    private void handleUndoApplied(Message msg) {
        int row = msg.getDataInt("row");
        int col = msg.getDataInt("col");
        String nextTurn = msg.getDataString("nextTurn");

        postToUi(() -> {
            boardView.removePiece(row, col);
            boardView.setWinPositions(new ArrayList<>());
            boardView.setGameActive(true);
            gameActive = true;
            undoRequested = false;
            undoBtn.setEnabled(true);

            boolean isMyTurn = nextTurn != null && nextTurn.equals(myColor.toProtocol());
            boardView.setMyTurn(isMyTurn);

            statusText.setText("悔棋成功！");
            if (isMyTurn) {
                turnText.setText("轮到你了！");
            } else {
                turnText.setText("等待对手...");
            }
        });
    }

    private void handleUndoDeclined() {
        postToUi(() -> {
            undoRequested = false;
            undoBtn.setEnabled(gameActive);
            statusText.setText("对手拒绝了悔棋请求");
            Toast.makeText(this, "对手拒绝了悔棋请求", Toast.LENGTH_SHORT).show();
        });
    }

    private void handleBeg() {
        postToUi(this::spawnDanmaku);
    }

    private void spawnDanmaku() {
        if (danmakuContainer == null || danmakuContainer.getWidth() <= 0) return;

        int containerWidth = danmakuContainer.getWidth();
        int containerHeight = danmakuContainer.getHeight();
        int count = 8 + danmakuRandom.nextInt(5); // 8-12

        for (int i = 0; i < count; i++) {
            TextView tv = new TextView(this);
            String text = DANMAKU_MESSAGES[danmakuRandom.nextInt(DANMAKU_MESSAGES.length)];
            tv.setText(text);
            tv.setTextSize(14 + danmakuRandom.nextFloat() * 8); // 14-22sp
            tv.setTextColor(Color.WHITE);
            tv.setShadowLayer(3, 1, 1, Color.BLACK);

            float y = 50 + danmakuRandom.nextFloat() * (containerHeight - 100);
            tv.setTranslationY(y);
            tv.setTranslationX(containerWidth + danmakuRandom.nextFloat() * 300);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
            danmakuContainer.addView(tv, params);

            float endX = -400 - danmakuRandom.nextFloat() * 200;
            long duration = 3000 + danmakuRandom.nextInt(4000); // 3-7s
            long delay = i * 150; // stagger

            tv.animate()
                .translationX(endX)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> danmakuContainer.removeView(tv))
                .start();
        }
    }

    private void handleError(Message msg) {
        String errMsg = msg.getDataString("message");
        postToUi(() -> {
            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onError(String error) {
        postToUi(() -> {
            Toast.makeText(this, "连接错误: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("退出游戏")
            .setMessage("确定要退出游戏吗？")
            .setPositiveButton("退出", (d, w) -> {
                ws.leaveRoom();
                handler.postDelayed(() -> {
                    ws.disconnect();
                    finish();
                }, 300);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void postToUi(Runnable action) {
        handler.post(() -> {
            if (!destroyed) action.run();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (danmakuContainer != null) {
            danmakuContainer.removeAllViews();
        }
        ws.setListener(null);
    }
}