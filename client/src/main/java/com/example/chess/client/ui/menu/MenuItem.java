package com.example.chess.client.ui.menu;

public class MenuItem {
    private final String label;
    private final Command command;

    public MenuItem(String label, Command command) {
        this.label = label;
        this.command = command;
    }

    public String getLabel() { return label; }
    public Command getCommand() { return command; }
}
