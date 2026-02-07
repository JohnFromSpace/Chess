package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.function.BooleanSupplier;

final class ConsolePrompter {

    private final ConsoleInput in;
    private final PrintStream out;

    ConsolePrompter(ConsoleInput in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    String askLineResponsive(String prompt, long pollEveryMs, Runnable pump, BooleanSupplier shouldAbort) throws InterruptedException {
        out.print(prompt);
        out.flush();

        while (true) {
            if (shouldAbort != null && shouldAbort.getAsBoolean()) {
                out.println();
                return null;
            }

            String line = in.pollLine(pollEveryMs);
            if (line != null) return line;

            if (pump != null) pump.run();
        }
    }

    int askIntResponsive(String prompt, long pollEveryMs, Runnable pump, BooleanSupplier shouldAbort) throws InterruptedException {
        while (true) {
            String line = askLineResponsive(prompt, pollEveryMs, pump, shouldAbort);
            if (line == null) return Integer.MIN_VALUE;

            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("Please enter a number.");
            }
        }
    }

    String askLine(String prompt) throws InterruptedException {
        return askLineResponsive(prompt, 1_000_000L, null, () -> false);
    }

    int askInt(String prompt) throws InterruptedException {
        int v = askIntResponsive(prompt, 1_000_000L, null, () -> false);
        return v == Integer.MIN_VALUE ? 0 : v;
    }
}
