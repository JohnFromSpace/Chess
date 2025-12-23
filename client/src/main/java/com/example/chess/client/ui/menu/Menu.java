package com.example.chess.client.ui.menu;

import com.example.chess.client.view.ConsoleView;

import java.util.ArrayList;
import java.util.List;

public class Menu {
    private final String title;
    private final List<MenuItem> items = new ArrayList<>();

    public Menu(String title) {
        this.title = title;
    }

    public void add(MenuItem item) {
        items.add(item);
    }

    public void render(ConsoleView view) {
        view.showMessage("\n=== " + title + " ===");
        for (int i = 0; i < items.size(); i++) {
            view.showMessage((i + 1) + ") " + items.get(i).getLabel());
        }
    }

    public void readAndExecute(ConsoleView view) {
        int choice = view.askInt("Choose: ");
        if (choice < 1 || choice > items.size()) {
            view.showError("Invalid choice.");
            return;
        }
        items.get(choice - 1).getCommand().execute();
    }

    public void readAndExecuteNonBlocking(ConsoleView view, java.util.function.BooleanSupplier shouldExit) {
        Integer choice = view.askIntWithTimeout("Choose: ", 300);
        if (shouldExit.getAsBoolean()) return;     // gameStarted arrived -> leave immediately
        if (choice == null) return;                // timeout -> caller loop continues

        if (choice < 1 || choice > items.size()) {
            view.showError("Invalid choice.");
            return;
        }
        items.get(choice - 1).getCommand().execute();
    }
}