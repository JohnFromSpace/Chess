package com.example.chess.server.fs.repository;

import java.util.List;
import java.util.Optional;
import com.example.chess.common.GameModels.Game;

public interface GameRepository {
    void saveGame(Game game);
    Optional<Game> findGameById(String id);
    List<Game> findGamesForUser(String username);
}
