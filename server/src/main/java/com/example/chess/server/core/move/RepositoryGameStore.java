package com.example.chess.server.core.move;

import com.example.chess.common.model.Game;
import com.example.chess.server.fs.repository.GameRepository;

import java.io.IOException;

final class RepositoryGameStore implements GameStore {

    private final GameRepository repo;

    RepositoryGameStore(GameRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(Game g) throws IOException {
        if (repo != null) repo.saveGame(g);
    }
}