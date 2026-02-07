package com.example.chess.client.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Log {
    private static final Logger L = Logger.getLogger("ChessClient");

    private Log() {
    }

    public static void warn(String msg, Throwable t) {
        L.log(Level.WARNING, msg, t);
    }
}
