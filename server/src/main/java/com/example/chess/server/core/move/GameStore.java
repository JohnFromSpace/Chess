package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;

import java.io.IOException;

interface GameStore {
    void save(Game g) throws IOException;
}