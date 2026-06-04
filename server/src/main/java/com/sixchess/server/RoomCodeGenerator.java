package com.sixchess.server;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates unique 6-digit room codes.
 */
public class RoomCodeGenerator {

    private final Random random = new Random();
    private final Set<String> activeCodes = ConcurrentHashMap.newKeySet();

    public synchronized String generate() {
        String code;
        int attempts = 0;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
            attempts++;
        } while (activeCodes.contains(code) && attempts < 200);
        if (attempts >= 200) {
            throw new IllegalStateException("Failed to generate unique room code after 200 attempts");
        }
        activeCodes.add(code);
        return code;
    }

    public void release(String code) {
        activeCodes.remove(code);
    }
}