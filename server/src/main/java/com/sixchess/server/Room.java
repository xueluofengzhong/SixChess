package com.sixchess.server;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Room state machine. Holds two player sessions and manages the game lifecycle.
 *
 * States: INIT -> WAITING -> READY -> PLAYING -> FINISHED -> CLOSED
 *                              |                         |
 *                              +----> CLOSED <-----------+
 */
public class Room {

    private static final Logger log = LoggerFactory.getLogger(Room.class);

    public enum State {
        INIT,       // Room created, awaiting host
        WAITING,    // Host is in room, waiting for guest
        READY,      // Both players joined, ready to start
        PLAYING,    // Game in progress
        FINISHED,   // Game ended (win/draw)
        CLOSED      // Room closed
    }

    private final String code;
    private State state;
    private WebSocket hostSocket;
    private WebSocket guestSocket;
    private String hostName;
    private String guestName;
    private GameSession game;
    private boolean hostRematch;
    private boolean guestRematch;
    private long lastActivityTime;

    private boolean swapColors; // Toggled on rematch to swap BLACK/WHITE

    public Room(String code) {
        this.code = code;
        this.state = State.INIT;
        this.lastActivityTime = System.currentTimeMillis();
        this.swapColors = false;
    }

    // --- State transitions ---

    /**
     * Host enters the room.
     */
    public synchronized void hostJoin(WebSocket socket, String name) {
        this.hostSocket = socket;
        this.hostName = name;
        this.state = State.WAITING;
        this.lastActivityTime = System.currentTimeMillis();
        log.info("Room {}: host '{}' joined. State -> WAITING", code, name);
    }

    /**
     * Guest joins the room. Returns true if successful.
     */
    public synchronized boolean guestJoin(WebSocket socket, String name) {
        if (state != State.WAITING) return false;
        this.guestSocket = socket;
        this.guestName = name;
        this.state = State.READY;
        this.lastActivityTime = System.currentTimeMillis();
        log.info("Room {}: guest '{}' joined. State -> READY", code, name);
        return true;
    }

    /**
     * Start the game. Both players are ready.
     * @param rematch if true, swap colors from previous game
     */
    public synchronized void startGame(boolean rematch) {
        if (state != State.READY && state != State.FINISHED) return;
        this.game = new GameSession();
        if (rematch) {
            this.swapColors = !this.swapColors;
        }
        this.state = State.PLAYING;
        this.hostRematch = false;
        this.guestRematch = false;
        this.lastActivityTime = System.currentTimeMillis();
        log.info("Room {}: game started (rematch={}). State -> PLAYING", code, rematch);
    }

    /**
     * Handle a piece placement. Returns the result.
     */
    public synchronized GameSession.PlaceResult placePiece(int row, int col, WebSocket socket) {
        if (state != State.PLAYING) {
            return GameSession.PlaceResult.error("Game is not active");
        }
        ChessType color = getColorFor(socket);
        if (color == null) {
            return GameSession.PlaceResult.error("Unknown player");
        }
        GameSession.PlaceResult result = game.placePiece(row, col, color);
        if (result.type == GameSession.PlaceResult.Type.WIN ||
            result.type == GameSession.PlaceResult.Type.DRAW) {
            this.state = State.FINISHED;
            log.info("Room {}: game over. Winner: {}. State -> FINISHED", code, result.winner);
        }
        this.lastActivityTime = System.currentTimeMillis();
        return result;
    }

    /**
     * Player requests rematch. Returns true if both agree.
     */
    public synchronized boolean requestRematch(WebSocket socket) {
        if (state != State.FINISHED) return false;
        if (socket == hostSocket) {
            hostRematch = true;
        } else if (socket == guestSocket) {
            guestRematch = true;
        }
        return hostRematch && guestRematch;
    }

    /**
     * Player leaves the room. Returns the other player's socket if any.
     */
    public synchronized WebSocket playerLeave(WebSocket socket) {
        WebSocket other = null;
        if (socket.equals(hostSocket)) {
            other = guestSocket;
            hostSocket = null;
        } else if (socket.equals(guestSocket)) {
            other = hostSocket;
            guestSocket = null;
        }
        if (hostSocket == null && guestSocket == null) {
            this.state = State.CLOSED;
        } else if (state == State.PLAYING || state == State.READY) {
            this.state = State.FINISHED;
        }
        this.lastActivityTime = System.currentTimeMillis();
        return other;
    }

    // --- Queries ---

    public synchronized boolean isHost(WebSocket socket) {
        return socket.equals(hostSocket);
    }

    public synchronized boolean isGuest(WebSocket socket) {
        return socket.equals(guestSocket);
    }

    public synchronized boolean isInRoom(WebSocket socket) {
        return socket.equals(hostSocket) || socket.equals(guestSocket);
    }

    public synchronized ChessType getColorFor(WebSocket socket) {
        if (socket.equals(hostSocket)) return swapColors ? ChessType.WHITE : ChessType.BLACK;
        if (socket.equals(guestSocket)) return swapColors ? ChessType.BLACK : ChessType.WHITE;
        return null;
    }

    public synchronized WebSocket getOther(WebSocket socket) {
        if (socket.equals(hostSocket)) return guestSocket;
        if (socket.equals(guestSocket)) return hostSocket;
        return null;
    }

    public synchronized String getPlayerName(WebSocket socket) {
        if (socket.equals(hostSocket)) return hostName;
        if (socket.equals(guestSocket)) return guestName;
        return null;
    }

    public synchronized boolean isReady() {
        return state == State.READY || state == State.FINISHED;
    }

    public synchronized boolean isEmpty() {
        return hostSocket == null && guestSocket == null;
    }

    public synchronized State getState() { return state; }
    public String getCode() { return code; }
    public synchronized String getHostName() { return hostName; }
    public synchronized String getGuestName() { return guestName; }
    public synchronized GameSession getGame() { return game; }
    public synchronized long getLastActivityTime() { return lastActivityTime; }
    public synchronized boolean isHostRematch() { return hostRematch; }
    public synchronized boolean isGuestRematch() { return guestRematch; }
}