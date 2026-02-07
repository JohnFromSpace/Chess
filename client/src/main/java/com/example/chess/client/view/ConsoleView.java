package com.example.chess.client.view;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class ConsoleView {

    private final PrintStream out;
    private final ConsolePrompter prompter;
    private final BoardRenderer boardRenderer;

    public ConsoleView(ConsoleInput in, PrintStream out) {
        this.out = out;
        this.prompter = new ConsolePrompter(in, out);
        this.boardRenderer = new ChessBoardRenderer();
    }

    public void showMessage(String msg) {
        out.println(msg);
    }

    public void showError(String msg) {
        out.println("ERROR: " + msg);
    }

    public void showGameOver(String result, String reason) {
        String r = (reason == null || reason.isBlank()) ? "" : (" (" + reason + ")");
        showMessage("\n=== Game Over ===");
        showMessage("Result: " + result + r);
        showMessage("");
    }

    public void clearScreen() {
        out.print("\u001B[2J\u001B[H");
        out.flush();
    }

    public String askLineResponsive(String prompt, long pollEveryMs, Runnable pump, BooleanSupplier shouldAbort) throws InterruptedException {
        return prompter.askLineResponsive(prompt, pollEveryMs, pump, shouldAbort);
    }

    public int askIntResponsive(String prompt, long pollEveryMs, Runnable pump, BooleanSupplier shouldAbort) throws InterruptedException {
        return prompter.askIntResponsive(prompt, pollEveryMs, pump, shouldAbort);
    }

    public String askLine(String prompt) throws InterruptedException { return prompter.askLine(prompt); }
    public int askInt(String prompt) throws InterruptedException { return prompter.askInt(prompt); }

    public void showBoard(String boardText) { showBoard(boardText, true); }

    public void showBoard(String boardText, boolean isWhitePerspective) {
        if (boardText == null || boardText.isBlank()) {
            out.println("(no board)");
            return;
        }
        out.println(boardRenderer.render(boardText, isWhitePerspective));
    }

    public void showBoardWithCaptured(String boardText,
                                      List<String> capturedByYou,
                                      List<String> capturedByOpp,
                                      boolean isWhitePerspective) {

        String renderedBoard = boardRenderer.render(boardText, isWhitePerspective).stripTrailing();
        String[] b = renderedBoard.split("\n", -1);

        int boardCols = 0;
        for (String line : b) boardCols = Math.max(boardCols, TextWidth.displayWidth(line));

        final int GAP = 10;

        List<String> side = new ArrayList<>();
        side.add("Captured by YOU: " + ChessGlyphs.renderCaptured(capturedByYou));
        side.add("Captured by OPP: " + ChessGlyphs.renderCaptured(capturedByOpp));
        side.add("Promotion: q/r/b/n (not limited by captured pieces)");

        int rows = Math.max(b.length, side.size());
        for (int i = 0; i < rows; i++) {
            String left = (i < b.length) ? b[i] : "";
            String right = (i < side.size()) ? side.get(i) : "";

            out.print(left);

            int pad = (boardCols - TextWidth.displayWidth(left)) + GAP;
            if (!right.isBlank()) {
                out.print(" ".repeat(Math.max(1, pad)));
                out.print(right);
            }
            out.println();
        }
    }
}
