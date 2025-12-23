package com.example.chess.client.ui.menu;

public class MenuItem {
    private final int number;
    private final String label;
    private final Command command;

    public MenuItem(int number, String label, Command command) {
        this.number = number;
        this.label = label;
        this.command = command;
    }

    public int getNumber() { return number; }
    public String getLabel() { return label; }
    public Command getCommand() { return command; }
}

