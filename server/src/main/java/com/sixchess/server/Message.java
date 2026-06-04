package com.sixchess.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * All WebSocket message types and their JSON serialization.
 * Protocol: {"type":"...", "data":{...}}
 */
public class Message {

    // Client -> Server types
    public static final String CREATE_ROOM = "create_room";
    public static final String JOIN_ROOM = "join_room";
    public static final String PLACE_PIECE = "place_piece";
    public static final String LEAVE_ROOM = "leave_room";
    public static final String REMATCH = "rematch";
    public static final String PONG = "pong";

    // Server -> Client types
    public static final String ROOM_CREATED = "room_created";
    public static final String ROOM_JOINED = "room_joined";
    public static final String OPPONENT_JOINED = "opponent_joined";
    public static final String GAME_START = "game_start";
    public static final String PIECE_PLACED = "piece_placed";
    public static final String GAME_OVER = "game_over";
    public static final String ERROR = "error";
    public static final String PLAYER_DISCONNECTED = "player_disconnected";
    public static final String REMATCH_REQUEST = "rematch_request";
    public static final String REMATCH_READY = "rematch_ready";
    public static final String REMATCH_DECLINED = "rematch_declined";
    public static final String PING = "ping";

    // Error codes
    public static final String ERR_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ERR_ROOM_FULL = "ROOM_FULL";
    public static final String ERR_INVALID_MOVE = "INVALID_MOVE";
    public static final String ERR_NOT_YOUR_TURN = "NOT_YOUR_TURN";
    public static final String ERR_GAME_NOT_ACTIVE = "GAME_NOT_ACTIVE";
    public static final String ERR_INVALID_ROOM_CODE = "INVALID_ROOM_CODE";
    public static final String ERR_NAME_TAKEN = "NAME_TAKEN";
    public static final String ERR_UNKNOWN_TYPE = "UNKNOWN_TYPE";

    private static final Gson gson = new Gson();

    public final String type;
    public final JsonObject data;

    public Message(String type, JsonObject data) {
        this.type = type;
        this.data = data != null ? data : new JsonObject();
    }

    /**
     * Parse a JSON string into a Message.
     */
    public static Message parse(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("type")) {
            return new Message(ERROR, null);
        }
        String type = obj.get("type").getAsString();
        JsonObject data = obj.has("data") ? obj.getAsJsonObject("data") : new JsonObject();
        return new Message(type, data);
    }

    /**
     * Serialize this message to a JSON string.
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.add("data", data);
        return gson.toJson(obj);
    }

    // --- Factory methods for server -> client messages ---

    public static Message roomCreated(String roomCode) {
        JsonObject d = new JsonObject();
        d.addProperty("roomCode", roomCode);
        d.addProperty("playerRole", "HOST");
        d.addProperty("boardSize", 15);
        return new Message(ROOM_CREATED, d);
    }

    public static Message roomJoined(String roomCode) {
        JsonObject d = new JsonObject();
        d.addProperty("roomCode", roomCode);
        d.addProperty("playerRole", "GUEST");
        d.addProperty("boardSize", 15);
        return new Message(ROOM_JOINED, d);
    }

    public static Message opponentJoined(String opponentName) {
        JsonObject d = new JsonObject();
        d.addProperty("opponentName", opponentName);
        return new Message(OPPONENT_JOINED, d);
    }

    public static Message gameStart(String yourColor) {
        JsonObject d = new JsonObject();
        d.addProperty("yourColor", yourColor);
        d.addProperty("boardSize", 15);
        return new Message(GAME_START, d);
    }

    public static Message piecePlaced(int row, int col, String color, String nextTurn, int moveNumber) {
        JsonObject d = new JsonObject();
        d.addProperty("row", row);
        d.addProperty("col", col);
        d.addProperty("color", color);
        d.addProperty("nextTurn", nextTurn);
        d.addProperty("moveNumber", moveNumber);
        return new Message(PIECE_PLACED, d);
    }

    public static Message gameOver(String winner, String reason, java.util.List<com.sixchess.server.Point> winPositions) {
        JsonObject d = new JsonObject();
        d.addProperty("winner", winner);
        d.addProperty("reason", reason);
        if (winPositions != null && !winPositions.isEmpty()) {
            d.add("winPositions", gson.toJsonTree(winPositions));
        }
        return new Message(GAME_OVER, d);
    }

    public static Message error(String code, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("code", code);
        d.addProperty("message", message);
        return new Message(ERROR, d);
    }

    public static Message playerDisconnected(String msg) {
        JsonObject d = new JsonObject();
        d.addProperty("message", msg);
        d.addProperty("gameEnded", true);
        return new Message(PLAYER_DISCONNECTED, d);
    }

    public static Message rematchRequest(String fromPlayer) {
        JsonObject d = new JsonObject();
        d.addProperty("fromPlayer", fromPlayer);
        return new Message(REMATCH_REQUEST, d);
    }

    public static Message rematchReady(String yourColor) {
        JsonObject d = new JsonObject();
        d.addProperty("yourColor", yourColor);
        return new Message(REMATCH_READY, d);
    }

    public static Message rematchDeclined() {
        return new Message(REMATCH_DECLINED, new JsonObject());
    }

    public static Message ping() {
        return new Message(PING, new JsonObject());
    }

    // --- Accessors for incoming messages ---

    public String getDataString(String key) {
        if (data.has(key)) return data.get(key).getAsString();
        return null;
    }

    public int getDataInt(String key) {
        if (data.has(key)) return data.get(key).getAsInt();
        return -1;
    }
}