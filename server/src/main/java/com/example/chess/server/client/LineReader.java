package com.example.chess.server.client;

import java.io.BufferedReader;
import java.io.IOException;

final class LineReader {
    private LineReader() {}

    static String readLineLimited(BufferedReader in, int maxChars) throws IOException {
        if (in == null) return null;
        int limit = Math.max(1, maxChars);
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = in.read();
            if (ch == -1) {
                return sb.isEmpty() ? null : sb.toString();
            }
            if (ch == '\n') {
                return sb.toString();
            }
            if (ch == '\r') {
                if (in.markSupported()) {
                    in.mark(1);
                    int next = in.read();
                    if (next != '\n' && next != -1) {
                        in.reset();
                    }
                }
                return sb.toString();
            }
            sb.append((char) ch);
            if (sb.length() > limit) {
                throw new LineTooLongException("Incoming line exceeds limit: " + limit);
            }
        }
    }

    static final class LineTooLongException extends IOException {
        private LineTooLongException(String message) {
            super(message);
        }
    }
}
