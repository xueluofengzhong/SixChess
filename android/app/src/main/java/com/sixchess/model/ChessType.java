package com.sixchess.model;

public enum ChessType {
    NONE,
    BLACK,
    WHITE;

    public static ChessType fromProtocol(String s) {
        if ("BLACK".equals(s)) return BLACK;
        if ("WHITE".equals(s)) return WHITE;
        return NONE;
    }

    public String toProtocol() {
        return name();
    }
}