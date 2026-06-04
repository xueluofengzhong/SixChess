package com.sixchess.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.sixchess.R;
import com.sixchess.network.Message;
import com.sixchess.network.WebSocketManager;

/**
 * Launcher screen: create or join a room.
 */
public class LauncherActivity extends AppCompatActivity implements WebSocketManager.MessageListener {

    private static final String TAG = "LauncherActivity";

    private Button createBtn;
    private Button joinBtn;
    private EditText codeInput;
    private EditText nameInput;
    private EditText serverUrlInput;
    private TextView statusText;
    private TextView roomCodeText;

    private WebSocketManager ws;
    private String pendingRoomCode;
    private boolean handedOff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        createBtn = findViewById(R.id.btn_create);
        joinBtn = findViewById(R.id.btn_join);
        codeInput = findViewById(R.id.et_room_code);
        nameInput = findViewById(R.id.et_player_name);
        serverUrlInput = findViewById(R.id.et_server_url);
        statusText = findViewById(R.id.tv_status);
        roomCodeText = findViewById(R.id.tv_room_code);

        ws = WebSocketManager.getInstance();

        createBtn.setOnClickListener(v -> {
            String name = getName();
            setWaiting(true);
            ws.connect(getServerUrl(), this);
            // We'll send create_room when connected
            pendingRoomCode = null;
        });

        joinBtn.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "请输入房间号", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = getName();
            setWaiting(true);
            ws.connect(getServerUrl(), this);
            pendingRoomCode = code;
        });
    }

    private String getName() {
        String name = nameInput.getText().toString().trim();
        return name.isEmpty() ? "Player" : name;
    }

    private String getServerUrl() {
        String url = serverUrlInput.getText().toString().trim();
        if (!url.isEmpty()) return url;
        return getString(R.string.server_url);
    }

    private void setWaiting(boolean waiting) {
        createBtn.setEnabled(!waiting);
        joinBtn.setEnabled(!waiting);
        statusText.setText(waiting ? "连接中..." : "");
    }

    // --- WebSocket callbacks ---

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            statusText.setText("已连接！");
            if (pendingRoomCode != null) {
                ws.joinRoom(pendingRoomCode, getName());
            } else {
                ws.createRoom(getName());
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            setWaiting(false);
            statusText.setText("断开连接: " + reason);
        });
    }

    @Override
    public void onMessage(Message msg) {
        if (isFinishing() || isDestroyed()) return;
        switch (msg.type) {
            case Message.ROOM_CREATED: {
                String code = msg.getDataString("roomCode");
                runOnUiThread(() -> {
                    roomCodeText.setText("房间号: " + code);
                    statusText.setText("等待对手加入...");
                    Toast.makeText(this, "房间创建成功！房间号: " + code, Toast.LENGTH_LONG).show();
                });
                break;
            }
            case Message.ROOM_JOINED: {
                runOnUiThread(() -> {
                    statusText.setText("已加入房间！等待游戏开始...");
                });
                break;
            }
            case Message.OPPONENT_JOINED: {
                String opponent = msg.getDataString("opponentName");
                runOnUiThread(() -> {
                    statusText.setText("对手 " + opponent + " 已加入！");
                });
                break;
            }
            case Message.GAME_START: {
                String myColor = msg.getDataString("yourColor");
                runOnUiThread(() -> {
                    Intent intent = new Intent(LauncherActivity.this, GameActivity.class);
                    intent.putExtra("myColor", myColor);
                    startActivity(intent);
                    handedOff = true;
                    finish();
                });
                break;
            }
            case Message.PING:
                ws.sendPong();
                break;
            case Message.ERROR: {
                String errMsg = msg.getDataString("message");
                runOnUiThread(() -> {
                    setWaiting(false);
                    statusText.setText("错误: " + errMsg);
                    Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show();
                    // Don't disconnect — let user fix input and retry
                });
                break;
            }
        }
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            setWaiting(false);
            statusText.setText("连接失败: " + error);
            Toast.makeText(this, "连接失败: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!handedOff) {
            ws.setListener(null);
            ws.disconnect();
        }
    }
}