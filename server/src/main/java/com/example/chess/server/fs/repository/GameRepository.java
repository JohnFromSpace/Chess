package com.example.chess.server.fs.repository;

import com.example.chess.common.model.Game;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface GameRepository {
    void saveGame(Game game) throws IOException;
    Optional<Game> findGameById(String id);
    Map<String, Game> findGamesForUser(String username);
}