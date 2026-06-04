package com.sixchess.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SixChess server entry point.
 * Starts a WebSocket server on the specified port (default 8080).
 *
 * Usage: java -jar sixchess-server.jar [port]
 */
public class SixChessServer {

    private static final Logger log = LoggerFactory.getLogger(SixChessServer.class);
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.error("Invalid port number: {}", args[0]);
                System.exit(1);
            }
        }

        log.info("========================================");
        log.info("  SixChess (六子棋) Server");
        log.info("  Port: {}", port);
        log.info("  WebSocket: ws://localhost:{}/ws", port);
        log.info("========================================");

        GameServer server = new GameServer(port);
        server.start();
        server.startCleanupAndPing();

        log.info("Server is running. Press Ctrl+C to stop.");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down SixChess server...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}