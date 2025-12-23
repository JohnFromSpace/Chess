package com.example.chess.client.ui;

import com.example.chess.client.net.ClientConnection;
import com.example.chess.client.ui.menu.Command;
import com.example.chess.client.view.ConsoleView;

public class RegisterCommand implements Command {

    private final ClientConnection conn;
    private final ConsoleView view;

    public RegisterCommand(ClientConnection conn, ConsoleView view) {
        this.conn = conn;
        this.view = view;
    }

    @Override
    public void execute() {
        String username = view.askLine("Username: ").trim();
        String name = view.askLine("Name: ").trim();
        String pass = view.askLine("Password: ").trim(); // ако няма readPassword

        var status = conn.register(username, name, pass).join();
        if (status.isError()) view.showError(status.getMessage());
        else view.showMessage("Registered successfully.");
    }
}