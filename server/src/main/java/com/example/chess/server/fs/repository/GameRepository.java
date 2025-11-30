package com.example.chess.server.fs.repository;

import java.util.List;
import java.util.Optional;
import com.example.chess.common.GameModels.Game;

public interface GameRepository {
    List<Game> findAllGames();

    Optional<Game> findGameById(String id);
    void saveGame(Game game);

    Optional<Game> findById(String id);

    List<Game> findGamesForUser(String username);
}
