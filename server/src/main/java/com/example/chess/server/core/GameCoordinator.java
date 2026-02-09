package com.example.chess.server.core;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.core.move.MoveService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GameCoordinator {
    private final MatchmakingService matchmaking;
    private final MoveService moves;
    private final StatsService stats;
    private final OnlineUserRegistry online;
    private final Object userStateLock = new Object();

    public GameCoordinator(MatchmakingService matchmaking, MoveService moves, StatsService stats, OnlineUserRegistry online) {
        this.matchmaking = matchmaking;
        this.moves = moves;
        this.stats = stats;
        this.online = online;
    }

    public void onUserOnline(ClientHandler h, User u) {
        if (u == null) throw new IllegalArgumentException("Missing user.");
        synchronized (userStateLock) {
            online.markOnline(u.getUsername(), h);
        }
    }

    public void onUserOffline(ClientHandler h, User u) {
        synchronized (userStateLock) {
            if (u != null) online.markOffline(u.getUsername(), h);
            matchmaking.onDisconnect(u);
            moves.onDisconnect(u);
        }
    }

    public void onUserLogout(ClientHandler h, User u) {
        synchronized (userStateLock) {
            if (u != null) online.markOffline(u.getUsername(), h);
            matchmaking.onDisconnect(u);
            moves.onDisconnect(u);
        }
    }

    public void requestGame(ClientHandler h, User u) throws IOException { matchmaking.enqueue(h, u); }
    public void makeMove(String gameId, User u, String move) throws IOException { moves.makeMove(gameId, u, move); }
    public void offerDraw(String id, User u) throws IOException { moves.offerDraw(id, u); }
    public void respondDraw(String id, User u, boolean accept) throws IOException { moves.respondDraw(id, u, accept); }
    public void resign(String id, User u) throws IOException { moves.resign(id, u); }

    public List<Game> listGamesForUser(String username) { return stats.listGamesForUser(username); }
    public Game getGameForUser(String gameId, String username) { return stats.getGameForUser(gameId, username); }

    public Map<String, Object> toGameDetailsPayload(Game g) {
        return stats.toGameDetailsPayload(g);
    }
}
