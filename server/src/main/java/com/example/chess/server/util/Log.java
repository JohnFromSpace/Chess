package com.example.chess.server.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Log {
    private static final Logger L = Logger.getLogger("ChessServer");

    private Log() {}

    public static void info(String msg) {
        L.info(msg);
    }

    public static void warn(String msg, Throwable t) {
        L.log(Level.WARNING, msg, t);
    }

    public static void error(String msg, Throwable t) {
        L.log(Level.SEVERE, msg, t);
    }
}