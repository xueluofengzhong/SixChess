package com.sixchess.server;

/**
 * Chess piece type.
 */
public enum ChessType {
    NONE,
    BLACK,
    WHITE;

    public ChessType opposite() {
        if (this == BLACK) return WHITE;
        if (this == WHITE) return BLACK;
        return NONE;
    }

    public String toProtocolString() {
        return name();
    }
}