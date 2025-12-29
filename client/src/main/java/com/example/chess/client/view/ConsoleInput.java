package com.example.chess.client.view;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class ConsoleInput implements AutoCloseable {

    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final Thread reader;
    private volatile boolean closed = false;

    public ConsoleInput(InputStream in) {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        reader = new Thread(() -> {
            try {
                String line;
                while (!closed && (line = br.readLine()) != null) {
                    lines.put(line);
                }
            } catch (Exception ex) {
                System.err.println("Failed to read/write line from buffer: " + ex.getMessage());
            } finally {
                closed = true;
            }
        }, "console-input");
        reader.setDaemon(true);
        reader.start();
    }

    /** Returns null on timeout or if closed. */
    public String pollLine(long timeoutMs) {
        if (closed) return null;
        try {
            return lines.poll(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void drain() {
        lines.clear();
    }

    @Override
    public void close() {
        closed = true;
        reader.interrupt();
        drain();
    }
}