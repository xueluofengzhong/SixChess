package com.sixchess.server;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe room registry. Manages all active rooms.
 */
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, String> sessionRoomMap = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codeGen = new RoomCodeGenerator();

    /**
     * Create a new room for the host.
     */
    public Room createRoom(WebSocket hostSocket, String hostName) {
        String code = codeGen.generate();
        Room room = new Room(code);
        room.hostJoin(hostSocket, hostName);
        rooms.put(code, room);
        sessionRoomMap.put(hostSocket, code);
        log.info("Created room {} for host '{}'", code, hostName);
        return room;
    }

    /**
     * Join an existing room by code.
     * @return the room if successful, null if room not found or not available
     */
    public Room joinRoom(String code, WebSocket guestSocket, String guestName) {
        Room room = rooms.get(code);
        if (room == null) {
            log.warn("Room {} not found", code);
            return null;
        }
        if (!room.guestJoin(guestSocket, guestName)) {
            log.warn("Room {} is not in WAITING state", code);
            return null;
        }
        sessionRoomMap.put(guestSocket, code);
        return room;
    }

    /**
     * Get the room a session is in.
     */
    public Room getRoomBySession(WebSocket socket) {
        String code = sessionRoomMap.get(socket);
        if (code == null) return null;
        return rooms.get(code);
    }

    /**
     * Handle a player disconnecting. Notifies the other player.
     * @return the other player's socket (if any) that needs to be notified
     */
    public WebSocket handleDisconnect(WebSocket socket) {
        Room room = getRoomBySession(socket);
        if (room == null) return null;
        WebSocket other = room.playerLeave(socket);
        sessionRoomMap.remove(socket);
        if (room.isEmpty()) {
            rooms.remove(room.getCode());
            codeGen.release(room.getCode());
            log.info("Room {} closed (empty)", room.getCode());
        }
        return other;
    }

    /**
     * Clean up stale rooms (no activity for > 5 minutes).
     */
    public void cleanupStaleRooms(long maxIdleMillis) {
        long now = System.currentTimeMillis();
        rooms.values().removeIf(room -> {
            if (now - room.getLastActivityTime() > maxIdleMillis) {
                codeGen.release(room.getCode());
                sessionRoomMap.values().removeIf(c -> c.equals(room.getCode()));
                log.info("Room {} removed (stale)", room.getCode());
                return true;
            }
            return false;
        });
    }

    public int getRoomCount() {
        return rooms.size();
    }
}