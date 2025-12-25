package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;

public interface GameEndHook {
    void onGameFinished(Game g) throws Exception;
}