package com.sixchess.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket server for SixChess. Handles all game communication.
 */
public class GameServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(GameServer.class);
    private static final long STALE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final long PING_INTERVAL_MS = 30 * 1000; // 30 seconds

    private final RoomManager roomManager;
    private final ScheduledExecutorService scheduler;

    public GameServer(int port) {
        super(new InetSocketAddress(port));
        this.roomManager = new RoomManager();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        log.info("SixChess GameServer created on port {}", port);
    }

    public void startCleanupAndPing() {
        // Cleanup stale rooms every 60 seconds
        scheduler.scheduleAtFixedRate(() -> {
            roomManager.cleanupStaleRooms(STALE_TIMEOUT_MS);
        }, 60, 60, TimeUnit.SECONDS);

        // Ping connected clients every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            for (WebSocket conn : getConnections()) {
                if (conn.isOpen()) {
                    conn.send(Message.ping().toJson());
                }
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log.info("New connection: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        log.info("Connection closed: {} (code={}, reason={})", conn.getRemoteSocketAddress(), code, reason);
        handlePlayerDisconnect(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket error from {}: {}", conn.getRemoteSocketAddress(), ex.getMessage());
        handlePlayerDisconnect(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("Received: {}", message);
        try {
            Message msg = Message.parse(message);
            dispatch(conn, msg);
        } catch (Exception e) {
            log.error("Failed to parse message: {}", message, e);
            conn.send(Message.error(Message.ERR_UNKNOWN_TYPE, "Invalid message format").toJson());
        }
    }

    private void dispatch(WebSocket conn, Message msg) {
        switch (msg.type) {
            case Message.CREATE_ROOM -> handleCreateRoom(conn, msg);
            case Message.JOIN_ROOM -> handleJoinRoom(conn, msg);
            case Message.PLACE_PIECE -> handlePlacePiece(conn, msg);
            case Message.LEAVE_ROOM -> handleLeaveRoom(conn);
            case Message.REMATCH -> handleRematch(conn);
            case Message.PONG -> { /* heartbeat response, nothing to do */ }
            case Message.UNDO_REQUEST -> handleUndoRequest(conn);
            case Message.UNDO_RESPONSE -> handleUndoResponse(conn, msg);
            case Message.BEG -> handleBeg(conn);
            default -> conn.send(Message.error(Message.ERR_UNKNOWN_TYPE, "Unknown message type: " + msg.type).toJson());
        }
    }

    private void handleCreateRoom(WebSocket conn, Message msg) {
        String playerName = msg.getDataString("playerName");
        if (playerName == null || playerName.isBlank()) {
            playerName = "Player" + conn.getRemoteSocketAddress().getPort();
        }
        Room room = roomManager.createRoom(conn, playerName);
        conn.send(Message.roomCreated(room.getCode()).toJson());
        log.info("Room {} created by '{}'", room.getCode(), playerName);
    }

    private void handleJoinRoom(WebSocket conn, Message msg) {
        String roomCode = msg.getDataString("roomCode");
        String playerName = msg.getDataString("playerName");
        if (roomCode == null || roomCode.isBlank()) {
            conn.send(Message.error(Message.ERR_INVALID_ROOM_CODE, "Room code is required").toJson());
            return;
        }
        if (playerName == null || playerName.isBlank()) {
            playerName = "Player" + conn.getRemoteSocketAddress().getPort();
        }
        Room room = roomManager.joinRoom(roomCode, conn, playerName);
        if (room == null) {
            conn.send(Message.error(Message.ERR_ROOM_NOT_FOUND, "Room not found or already full").toJson());
            return;
        }

        // Notify guest
        conn.send(Message.roomJoined(roomCode).toJson());

        // Notify host
        WebSocket hostSocket = room.getOther(conn);
        if (hostSocket != null && hostSocket.isOpen()) {
            hostSocket.send(Message.opponentJoined(playerName).toJson());
        }

        // Start the game (first game, no color swap)
        room.startGame(false);
        WebSocket guest = conn;
        if (hostSocket != null && hostSocket.isOpen()) {
            hostSocket.send(Message.gameStart("BLACK").toJson());
        }
        guest.send(Message.gameStart("WHITE").toJson());
        log.info("Game started in room {}", roomCode);
    }

    private void handlePlacePiece(WebSocket conn, Message msg) {
        int row = msg.getDataInt("row");
        int col = msg.getDataInt("col");

        Room room = roomManager.getRoomBySession(conn);
        if (room == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "You are not in a room").toJson());
            return;
        }

        GameSession.PlaceResult result = room.placePiece(row, col, conn);
        switch (result.type) {
            case PLACED -> {
                Message placed = Message.piecePlaced(row, col,
                    room.getColorFor(conn).toProtocolString(),
                    result.nextTurn, result.moveNumber);
                broadcastToRoom(room, placed);
            }
            case WIN -> {
                // Send the final piece placement first, then game over
                broadcastToRoom(room, Message.piecePlaced(row, col,
                    room.getColorFor(conn).toProtocolString(),
                    null, result.moveNumber));
                broadcastToRoom(room, Message.gameOver(result.winner, "SIX_IN_ROW", result.winPositions));
            }
            case DRAW -> {
                // Send the final piece placement first, then draw
                broadcastToRoom(room, Message.piecePlaced(row, col,
                    room.getColorFor(conn).toProtocolString(),
                    null, result.moveNumber));
                broadcastToRoom(room, Message.gameOver("DRAW", "BOARD_FULL", null));
            }
            case ERROR -> conn.send(Message.error(Message.ERR_INVALID_MOVE, result.errorMessage).toJson());
        }
    }

    private void handleLeaveRoom(WebSocket conn) {
        WebSocket other = roomManager.handleDisconnect(conn);
        if (other != null && other.isOpen()) {
            other.send(Message.playerDisconnected("Your opponent has left").toJson());
        }
        conn.close();
    }

    private void handleRematch(WebSocket conn) {
        Room room = roomManager.getRoomBySession(conn);
        if (room == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "You are not in a room").toJson());
            return;
        }

        // Only allow rematch in FINISHED state
        if (room.getState() != Room.State.FINISHED) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "Game is still in progress").toJson());
            return;
        }

        boolean bothAgreed = room.requestRematch(conn);
        WebSocket other = room.getOther(conn);

        if (bothAgreed) {
            // Guard: if other player disconnected between requestRematch and here
            if (other == null || !other.isOpen()) {
                conn.send(Message.error(Message.ERR_ROOM_NOT_FOUND, "Opponent disconnected").toJson());
                return;
            }
            // Both agreed, start new game with swapped colors
            room.startGame(true);
            ChessType myColor = room.getColorFor(conn);
            ChessType otherColor = room.getColorFor(other);
            conn.send(Message.rematchReady(myColor.toProtocolString()).toJson());
            other.send(Message.rematchReady(otherColor.toProtocolString()).toJson());
        } else {
            // Notify the other player
            String playerName = room.getPlayerName(conn);
            if (other != null && other.isOpen()) {
                other.send(Message.rematchRequest(playerName != null ? playerName : "Opponent").toJson());
            }
        }
    }

    private void handleUndoRequest(WebSocket conn) {
        Room room = roomManager.getRoomBySession(conn);
        if (room == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "You are not in a room").toJson());
            return;
        }

        WebSocket other = room.requestUndo(conn);
        if (other == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "Cannot request undo now").toJson());
            return;
        }

        String playerName = room.getPlayerName(conn);
        if (other.isOpen()) {
            other.send(Message.undoRequest(playerName != null ? playerName : "对手").toJson());
        }
    }

    private void handleUndoResponse(WebSocket conn, Message msg) {
        Room room = roomManager.getRoomBySession(conn);
        if (room == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "You are not in a room").toJson());
            return;
        }

        // Capture requester before apply clears it
        WebSocket requester = room.getUndoRequester();
        boolean accept = msg.data.has("accept") && msg.data.get("accept").getAsBoolean();
        GameSession.MoveRecord record = room.applyUndoResponse(accept, conn);

        if (accept && record != null) {
            String nextTurn = record.color.toProtocolString();
            int moveNumber = room.getGame().getMoveCount();
            broadcastToRoom(room, Message.undoApplied(record.row, record.col, nextTurn, moveNumber));
            log.info("Room {}: undo applied, removed ({},{})", room.getCode(), record.row, record.col);
        } else if (!accept && requester != null && requester.isOpen()) {
            requester.send(Message.undoDeclined().toJson());
        }
    }

    private void handleBeg(WebSocket conn) {
        Room room = roomManager.getRoomBySession(conn);
        if (room == null) {
            conn.send(Message.error(Message.ERR_GAME_NOT_ACTIVE, "You are not in a room").toJson());
            return;
        }
        broadcastToRoom(room, Message.beg());
        log.info("Room {}: {} begs for mercy", room.getCode(), room.getPlayerName(conn));
    }

    private void handlePlayerDisconnect(WebSocket conn) {
        WebSocket other = roomManager.handleDisconnect(conn);
        if (other != null && other.isOpen()) {
            other.send(Message.playerDisconnected("Your opponent has disconnected").toJson());
        }
    }

    private void broadcastToRoom(Room room, Message msg) {
        String json = msg.toJson();
        for (WebSocket conn : getConnections()) {
            if (room.isInRoom(conn) && conn.isOpen()) {
                conn.send(json);
            }
        }
    }

    @Override
    public void onStart() {
        log.info("SixChess server started on port {}", getPort());
    }
}