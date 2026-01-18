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
                com.example.chess.server.util.Log.warn("Failed to read/write line from buffer: ", ex);
            } finally {
                closed = true;
            }
        }, "console-input");
        reader.setDaemon(true);
        reader.start();
    }

    public String pollLine(long timeoutMs) throws InterruptedException {
        if (closed) throw new RuntimeException("Timeout.");
        try {
            return lines.poll(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Interrupted " + e);
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