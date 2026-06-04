package com.sixchess.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Message types matching the server protocol.
 */
public class Message {

    // Client -> Server
    public static final String CREATE_ROOM = "create_room";
    public static final String JOIN_ROOM = "join_room";
    public static final String PLACE_PIECE = "place_piece";
    public static final String LEAVE_ROOM = "leave_room";
    public static final String REMATCH = "rematch";
    public static final String PONG = "pong";
    public static final String UNDO_REQUEST = "undo_request";
    public static final String UNDO_RESPONSE = "undo_response";
    public static final String BEG = "beg";

    // Server -> Client
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
    public static final String UNDO_APPLIED = "undo_applied";
    public static final String UNDO_DECLINED = "undo_declined";

    public final String type;
    public final JsonObject data;

    public Message(String type, JsonObject data) {
        this.type = type;
        this.data = data != null ? data : new JsonObject();
    }

    public static Message parse(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("type")) {
            return new Message("unknown", new JsonObject());
        }
        String type = obj.get("type").getAsString();
        JsonObject data = obj.has("data") ? obj.getAsJsonObject("data") : new JsonObject();
        return new Message(type, data);
    }

    public String getDataString(String key) {
        if (data.has(key)) return data.get(key).getAsString();
        return null;
    }

    public int getDataInt(String key) {
        if (data.has(key)) return data.get(key).getAsInt();
        return -1;
    }
}