package com.sixchess.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.concurrent.TimeUnit;

/**
 * Singleton WebSocket manager for the SixChess client.
 * Handles connection lifecycle, message sending, and reconnection.
 */
public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String serverUrl;
    private volatile MessageListener activeListener;
    private boolean connected;
    private final Handler mainHandler;
    private final Gson gson;

    // Default server URL (change to ngrok URL for remote play)
    private static final String DEFAULT_SERVER_URL = "ws://10.0.2.2:8080";

    public interface MessageListener {
        void onConnected();
        void onDisconnected(String reason);
        void onMessage(Message msg);
        void onError(String error);
    }

    private WebSocketManager() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        this.connected = false;
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * Swap the message listener without reconnecting.
     * Use when handing off from LauncherActivity to GameActivity.
     */
    public void setListener(MessageListener newListener) {
        this.activeListener = newListener;
        // If already connected, notify the new listener
        if (connected && newListener != null) {
            mainHandler.post(newListener::onConnected);
        }
    }

    /**
     * Connect to the SixChess server.
     * Closes any existing connection first.
     */
    public void connect(String url, MessageListener newListener) {
        // Close existing connection and clean up before reconnecting
        if (webSocket != null) {
            Log.d(TAG, "Closing existing connection before reconnecting");
            disconnect();
        }

        this.activeListener = newListener;
        String resolvedUrl = (url != null && !url.isEmpty()) ? url : DEFAULT_SERVER_URL;

        // Normalize URL: convert http(s) to ws(s), add ws:// if no protocol
        resolvedUrl = resolvedUrl.trim();
        if (resolvedUrl.startsWith("https://")) {
            resolvedUrl = "wss://" + resolvedUrl.substring("https://".length());
        } else if (resolvedUrl.startsWith("http://")) {
            resolvedUrl = "ws://" + resolvedUrl.substring("http://".length());
        } else if (!resolvedUrl.startsWith("ws://") && !resolvedUrl.startsWith("wss://")) {
            resolvedUrl = "ws://" + resolvedUrl;
        }
        this.serverUrl = resolvedUrl;

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(serverUrl)
                .addHeader("ngrok-skip-browser-warning", "1")
                .build();

        Log.d(TAG, "Connecting to: " + serverUrl);
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "Connected to server");
                connected = true;
                mainHandler.post(() -> {
                    MessageListener l = activeListener;
                    if (l != null) l.onConnected();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received: " + text);
                try {
                    Message msg = Message.parse(text);
                    mainHandler.post(() -> {
                        MessageListener l = activeListener;
                        if (l != null) l.onMessage(msg);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse message: " + text, e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                connected = false;
                mainHandler.post(() -> {
                    MessageListener l = activeListener;
                    if (l != null) l.onDisconnected(reason);
                });
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                connected = false;
                mainHandler.post(() -> {
                    MessageListener l = activeListener;
                    if (l != null) {
                        l.onError(t.getMessage() != null ? t.getMessage() : "Connection failed");
                    }
                });
            }
        });
    }

    /**
     * Send a message to the server.
     */
    public void send(String type, JsonObject data) {
        if (webSocket == null || !connected) {
            Log.w(TAG, "Cannot send: not connected");
            return;
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        msg.add("data", data != null ? data : new JsonObject());
        String json = gson.toJson(msg);
        Log.d(TAG, "Sending: " + json);
        webSocket.send(json);
    }

    // Convenience methods
    public void createRoom(String playerName) {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        send(Message.CREATE_ROOM, data);
    }

    public void joinRoom(String roomCode, String playerName) {
        JsonObject data = new JsonObject();
        data.addProperty("roomCode", roomCode);
        data.addProperty("playerName", playerName);
        send(Message.JOIN_ROOM, data);
    }

    public void placePiece(int row, int col) {
        JsonObject data = new JsonObject();
        data.addProperty("row", row);
        data.addProperty("col", col);
        send(Message.PLACE_PIECE, data);
    }

    public void leaveRoom() {
        send(Message.LEAVE_ROOM, new JsonObject());
    }

    public void requestRematch() {
        send(Message.REMATCH, new JsonObject());
    }

    public void sendPong() {
        send(Message.PONG, new JsonObject());
    }

    public void requestUndo() {
        send(Message.UNDO_REQUEST, new JsonObject());
    }

    public void respondUndo(boolean accept) {
        JsonObject data = new JsonObject();
        data.addProperty("accept", accept);
        send(Message.UNDO_RESPONSE, data);
    }

    public void sendBeg() {
        send(Message.BEG, new JsonObject());
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User left");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdownNow();
            client = null;
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.disconnect();
            instance = null;
        }
    }
}