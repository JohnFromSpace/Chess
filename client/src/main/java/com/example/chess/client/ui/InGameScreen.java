package com.example.chess.client.ui;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.SessionState;
import com.example.chess.client.ui.menu.Command;
import com.example.chess.client.ui.menu.Menu;
import com.example.chess.client.ui.menu.MenuItem;
import com.example.chess.client.view.ConsoleView;

public class InGameScreen implements Screen {

    private final ClientConnection conn;
    private final ConsoleView view;
    private final SessionState state;

    public InGameScreen(ClientConnection conn, ConsoleView view, SessionState state) {
        this.conn = conn; this.view = view; this.state = state;
    }

    @Override
    public void show() {
        Menu menu = new Menu("Game");
        menu.add(new MenuItem("Move", new MoveCommand(conn, view, state)));
        menu.add(new MenuItem("Offer draw", new OfferDrawCommand(conn, view, state)));
        menu.add(new MenuItem("Resign", new ResignCommand(conn, view, state)));
        menu.add(new MenuItem("Back to lobby", () -> state.clearGame()));
        menu.add(new MenuItem("Print board", () -> view.showBoard(state.getLastBoard())));

        while (state.getUser() != null && state.isInGame()) {
            menu.render(view);
            menu.readAndExecute(view);
        }
    }

    static final class MoveCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;
        private final SessionState state;

        MoveCommand(ClientConnection conn, ConsoleView view, SessionState state) {
            this.conn = conn; this.view = view; this.state = state;
        }

        @Override public void execute() {
            String move = view.askLine("Enter move (e2e4 / e7e8q): ").trim();
            var status = conn.makeMove(state.getActiveGameId(), move).join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Move sent.");
        }
    }

    static final class OfferDrawCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;
        private final SessionState state;

        OfferDrawCommand(ClientConnection conn, ConsoleView view, SessionState state) {
            this.conn = conn; this.view = view; this.state = state;
        }

        @Override
        public void execute() {
            var status = conn.offerDraw(state.getActiveGameId()).join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Draw offer sent.");
        }
    }

    static final class ResignCommand implements Command {
        private final ClientConnection conn;
        private final ConsoleView view;
        private final SessionState state;

        ResignCommand(ClientConnection conn, ConsoleView view, SessionState state) {
            this.conn = conn; this.view = view; this.state = state;
        }

        @Override
        public void execute() {
            var status = conn.resign(state.getActiveGameId()).join();
            if (status.isError()) view.showError(status.getMessage());
            else view.showMessage("Resigned.");
        }
    }
}