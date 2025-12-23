package com.example.chess.client.ui.menu;

@FunctionalInterface
public interface Command {
    /*
    * @return true to keep running the main loop,
    *         false to exit the client.
    * */

    boolean execute();
}
