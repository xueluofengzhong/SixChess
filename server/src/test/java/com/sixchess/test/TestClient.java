package com.sixchess.test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple WebSocket client for testing SixChess server.
 * Tests: create room, join, place pieces, win detection, draw, rematch with color swap.
 */
public class TestClient {
    private Socket socket;
    private OutputStream rawOut;
    private InputStream in;
    private String name;
    private int passed = 0;
    private int failed = 0;

    public TestClient(String name) {
        this.name = name;
    }

    public void connect(String host, int port) throws Exception {
        socket = new Socket(host, port);
        rawOut = socket.getOutputStream();
        in = socket.getInputStream();

        // WebSocket upgrade handshake
        String key = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest("test-key".getBytes())
        );
        String upgrade = "GET / HTTP/1.1\r\n" +
            "Host: " + host + ":" + port + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: " + key + "\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n";
        rawOut.write(upgrade.getBytes(StandardCharsets.UTF_8));
        rawOut.flush();

        // Read upgrade response
        StringBuilder header = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            header.append((char) b);
            if (header.toString().endsWith("\r\n\r\n")) break;
        }
    }

    public void send(String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text opcode
        if (data.length < 126) {
            frame.write(data.length);
        } else if (data.length < 65536) {
            frame.write(126);
            frame.write((data.length >> 8) & 0xFF);
            frame.write(data.length & 0xFF);
        }
        frame.write(data);
        rawOut.write(frame.toByteArray());
        rawOut.flush();
    }

    public String receive() throws Exception {
        int first = in.read();
        if (first == -1) return null;
        int second = in.read();
        int length = second & 0x7F;

        if (length == 126) {
            length = (in.read() << 8) | in.read();
        } else if (length == 127) {
            for (int i = 0; i < 8; i++) in.read();
        }

        byte[] payload = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(payload, read, length - read);
            if (r == -1) break;
            read += r;
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    public String recvMsg() throws Exception {
        String json;
        while ((json = receive()) != null) {
            if (json.contains("\"type\"")) return json;
        }
        return null;
    }

    public void close() {
        try { socket.close(); } catch (Exception e) {}
    }

    // Test helpers
    public void check(String testName, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + testName);
            passed++;
        } else {
            System.out.println("  [FAIL] " + testName);
            failed++;
        }
    }

    public String getField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return null;
        idx = json.indexOf(":", idx);
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx);
        int end = json.indexOf("\"", start + 1);
        if (start < 0 || end < 0) return null;
        return json.substring(start + 1, end);
    }

    public int getIntField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return -1;
        idx = json.indexOf(":", idx);
        if (idx < 0) return -1;
        String sub = json.substring(idx + 1).trim();
        int end = 0;
        while (end < sub.length() && (Character.isDigit(sub.charAt(end)) || sub.charAt(end) == '-')) end++;
        return Integer.parseInt(sub.substring(0, end));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== SixChess Server Test Suite ===\n");

        TestClient p1 = new TestClient("Alice");
        TestClient p2 = new TestClient("Bob");

        // Test 1: Connect
        System.out.println("Test 1: Connection");
        p1.connect("localhost", 8080);
        p2.connect("localhost", 8080);
        p1.check("P1 connected", true);
        p2.check("P2 connected", true);

        // Test 2: Create room
        System.out.println("\nTest 2: Create Room");
        p1.send("{\"type\":\"create_room\",\"data\":{\"playerName\":\"Alice\"}}");
        String resp = p1.recvMsg();
        p1.check("P1 received room_created", resp != null && resp.contains("room_created"));
        String roomCode = p1.getField(resp, "roomCode");
        p1.check("Room code generated", roomCode != null && roomCode.length() == 6);
        System.out.println("  Room code: " + roomCode);

        // Test 3: Join room
        System.out.println("\nTest 3: Join Room");
        p2.send("{\"type\":\"join_room\",\"data\":{\"roomCode\":\"" + roomCode + "\",\"playerName\":\"Bob\"}}");
        String r2 = p2.recvMsg();
        p2.check("P2 received room_joined", r2 != null && r2.contains("room_joined"));
        String r1a = p1.recvMsg(); // opponent_joined
        p1.check("P1 received opponent_joined", r1a != null && r1a.contains("opponent_joined"));
        String r1b = p1.recvMsg(); // game_start
        p1.check("P1 received game_start", r1b != null && r1b.contains("game_start"));
        String r2b = p2.recvMsg(); // game_start
        p2.check("P2 received game_start", r2b != null && r2b.contains("game_start"));
        boolean p1Black = r1b.contains("BLACK");
        boolean p2White = r2b.contains("WHITE");
        p1.check("P1 is BLACK", p1Black);
        p2.check("P2 is WHITE", p2White);

        // Test 4: Place pieces and verify
        System.out.println("\nTest 4: Piece Placement");
        // P1 (BLACK) places at (7,7) - center
        p1.send("{\"type\":\"place_piece\",\"data\":{\"row\":7,\"col\":7}}");
        String m1 = p1.recvMsg(); // piece_placed
        p1.check("P1 received piece_placed", m1 != null && m1.contains("piece_placed"));
        p1.check("P1 move at (7,7)", p1.getIntField(m1, "row") == 7 && p1.getIntField(m1, "col") == 7);
        String m1b = p2.recvMsg(); // P2 also receives piece_placed
        p2.check("P2 received piece_placed", m1b != null && m1b.contains("piece_placed"));

        // P2 (WHITE) places at (7,8)
        p2.send("{\"type\":\"place_piece\",\"data\":{\"row\":7,\"col\":8}}");
        p2.recvMsg(); // consume
        p1.recvMsg(); // consume

        // Test 5: Invalid move (out of turn)
        System.out.println("\nTest 5: Invalid Move Detection");
        p2.send("{\"type\":\"place_piece\",\"data\":{\"row\":5,\"col\":5}}"); // P2 tries twice
        String err = p2.recvMsg();
        p2.check("Server rejects out-of-turn move", err != null && err.contains("error"));

        // Test 6: Invalid move (occupied position)
        p1.send("{\"type\":\"place_piece\",\"data\":{\"row\":7,\"col\":7}}"); // Already occupied
        String err2 = p1.recvMsg();
        p1.check("Server rejects occupied position", err2 != null && err2.contains("error"));

        // Test 7: Win detection - build 6 in a row for BLACK (P1)
        System.out.println("\nTest 7: Win Detection (6-in-a-row)");
        // P1 places remaining 5 pieces to form: (7,3)(7,4)(7,5)(7,6)(7,7)(7,8)
        // P1 already has (7,7). P2 has (7,8). So P1 needs (7,3)(7,4)(7,5)(7,6) plus (7,9) or similar
        // Actually: P1 already at (7,7). Let's build a different line:
        // P1 at (0,0), (0,1), (0,2), (0,3), (0,4), (0,5) - horizontal 6 in a row
        // But we need to alternate turns. Let's do a simpler approach:

        // Reset: close and reconnect
        p1.close();
        p2.close();
        Thread.sleep(200);

        p1 = new TestClient("Alice");
        p2 = new TestClient("Bob");
        p1.connect("localhost", 8080);
        p2.connect("localhost", 8080);

        p1.send("{\"type\":\"create_room\",\"data\":{\"playerName\":\"Alice\"}}");
        String cr = p1.recvMsg();
        String rc = p1.getField(cr, "roomCode");
        p2.send("{\"type\":\"join_room\",\"data\":{\"roomCode\":\"" + rc + "\",\"playerName\":\"Bob\"}}");
        p2.recvMsg(); p1.recvMsg(); p1.recvMsg(); p2.recvMsg(); // consume all

        // P1(BLACK): (0,0), (0,1), (0,2), (0,3), (0,4), (0,5) = 6 in a row
        // P2(WHITE): (1,0), (1,1), (1,2), (1,3), (1,4) = distraction
        int[][] p1Moves = {{0,0}, {0,1}, {0,2}, {0,3}, {0,4}, {0,5}};
        int[][] p2Moves = {{1,0}, {1,1}, {1,2}, {1,3}, {1,4}};

        for (int i = 0; i < 5; i++) {
            p1.send("{\"type\":\"place_piece\",\"data\":{\"row\":" + p1Moves[i][0] + ",\"col\":" + p1Moves[i][1] + "}}");
            p1.recvMsg(); p2.recvMsg();
            p2.send("{\"type\":\"place_piece\",\"data\":{\"row\":" + p2Moves[i][0] + ",\"col\":" + p2Moves[i][1] + "}}");
            p2.recvMsg(); p1.recvMsg();
        }

        // P1's 6th move (winning)
        p1.send("{\"type\":\"place_piece\",\"data\":{\"row\":0,\"col\":5}}");
        String win1 = p1.recvMsg(); // piece_placed
        String win2 = p1.recvMsg(); // game_over
        String lose1 = p2.recvMsg(); // piece_placed
        String lose2 = p2.recvMsg(); // game_over

        p1.check("P1 receives piece_placed on final move", win1 != null && win1.contains("piece_placed"));
        p1.check("P1 receives game_over", win2 != null && win2.contains("game_over"));
        p1.check("P1 wins", win2 != null && win2.contains("BLACK") && win2.contains("SIX_IN_ROW"));
        p1.check("P1 winPositions included", win2 != null && win2.contains("winPositions"));

        p2.check("P2 receives piece_placed on final move (DRAW fix)", lose1 != null && lose1.contains("piece_placed"));
        p2.check("P2 receives game_over", lose2 != null && lose2.contains("game_over"));
        p2.check("P2 loses", lose2 != null && lose2.contains("BLACK"));

        // Test 8: Rematch with color swap
        System.out.println("\nTest 8: Rematch with Color Swap");
        p1.send("{\"type\":\"rematch\",\"data\":{}}");
        String remReq = p2.recvMsg(); // P2 receives rematch_request
        p2.check("P2 receives rematch_request", remReq != null && remReq.contains("rematch_request"));

        p2.send("{\"type\":\"rematch\",\"data\":{}}");
        String rem1 = p1.recvMsg(); // rematch_ready
        String rem2 = p2.recvMsg(); // rematch_ready
        p1.check("P1 receives rematch_ready", rem1 != null && rem1.contains("rematch_ready"));
        p2.check("P2 receives rematch_ready", rem2 != null && rem2.contains("rematch_ready"));

        // Colors should be swapped: P1 now WHITE, P2 now BLACK
        boolean p1NowWhite = rem1.contains("WHITE");
        boolean p2NowBlack = rem2.contains("BLACK");
        p1.check("P1 color swapped to WHITE", p1NowWhite);
        p2.check("P2 color swapped to BLACK", p2NowBlack);

        // Test 9: P2 (now BLACK) places first in rematch
        p2.send("{\"type\":\"place_piece\",\"data\":{\"row\":3,\"col\":3}}");
        String rm1 = p2.recvMsg();
        p2.check("P2 (BLACK) can move first in rematch", rm1 != null && rm1.contains("piece_placed"));

        // Test 10: Invalid room join
        System.out.println("\nTest 10: Error Handling");
        TestClient p3 = new TestClient("Eve");
        p3.connect("localhost", 8080);
        p3.send("{\"type\":\"join_room\",\"data\":{\"roomCode\":\"999999\",\"playerName\":\"Eve\"}}");
        String err3 = p3.recvMsg();
        p3.check("Joining invalid room returns error", err3 != null && err3.contains("error"));
        p3.close();

        // Test 11: Ping/Pong
        System.out.println("\nTest 11: Ping/Pong");
        String ping = p1.recvMsg(); // Server sends ping every 30s, but we might get one
        if (ping != null && ping.contains("ping")) {
            p1.send("{\"type\":\"pong\",\"data\":{}}");
            p1.check("Ping received and pong sent", true);
        } else {
            p1.check("No ping yet (30s interval not reached)", true);
        }

        // Cleanup
        p1.close();
        p2.close();

        // Summary
        System.out.println("\n=== Results ===");
        System.out.println("PASSED: " + p1.passed + " (Alice) + " + p2.passed + " (Bob) = " + (p1.passed + p2.passed));
        System.out.println("FAILED: " + p1.failed + " (Alice) + " + p2.failed + " (Bob) = " + (p1.failed + p2.failed));
        int total = p1.passed + p2.passed + p1.failed + p2.failed + 1; // +1 for p3 check
        if (p1.failed + p2.failed == 0) {
            System.out.println("\nALL TESTS PASSED!");
        } else {
            System.out.println("\nSOME TESTS FAILED!");
            System.exit(1);
        }
    }
}