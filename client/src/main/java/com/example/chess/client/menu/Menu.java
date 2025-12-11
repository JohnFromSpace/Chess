package com.example.chess.client.menu;

import com.example.chess.client.view.ConsoleView;

import java.util.List;

public class Menu {
    private final String title;
    private final List<MenuItem> items;

    public Menu(String title, List<MenuItem> items) {
        this.title = title;
        this.items = items;
    }

    public boolean showAndHandle(ConsoleView view) {
        view.showMessage("\n=== " + title + " ===");
        for (MenuItem item : items) {
            view.showMessage(item.getNumber() + ") " + item.getLabel());
        }
        view.showMessage("0) Exit");

        int choice = view.askInt("Choose: ");
        if (choice == 0) {
            return false;
        }

        for (MenuItem item : items) {
            if (item.getNumber() == choice) {
                return item.getCommand().execute();
            }
        }

        view.showError("Invalid choice.");
        return true;
    }
}
