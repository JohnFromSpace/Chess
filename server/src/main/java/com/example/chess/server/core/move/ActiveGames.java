package com.example.chess.server.core.move;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveGames {

    private final Map<String, GameContext> active = new ConcurrentHashMap<>();
    private final Map<String, String> userToGame = new ConcurrentHashMap<>();

    void put(GameContext ctx) {
        active.put(ctx.getGame().getId(), ctx);
        indexUsers(ctx);
    }

    GameContext mustCtx(String gameId) {
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("Missing gameId.");
        GameContext ctx = active.get(gameId);
        if (ctx == null) throw new IllegalArgumentException("No such active game.");
        return ctx;
    }

    GameContext findCtxByUser(String username) {
        if (username == null) return null;
        String gid = userToGame.get(username);
        if (gid == null) return null;
        return active.get(gid);
    }

    void remove(GameContext ctx) {
        if (ctx == null || ctx.getGame() == null || ctx.getGame().getId() == null) {
            throw new IllegalArgumentException("Missing game context to remove.");
        }

        String gid = ctx.getGame().getId();
        active.remove(gid);

        if (ctx.getGame().getWhiteUser() != null) userToGame.remove(ctx.getGame().getWhiteUser(), gid);
        if (ctx.getGame().getBlackUser() != null) userToGame.remove(ctx.getGame().getBlackUser(), gid);
    }

    private void indexUsers(GameContext ctx) {
        if (ctx.getGame().getWhiteUser() != null) userToGame.put(ctx.getGame().getWhiteUser(), ctx.getGame().getId());
        if (ctx.getGame().getBlackUser() != null) userToGame.put(ctx.getGame().getBlackUser(), ctx.getGame().getId());
    }

    List<GameContext> snapshot() {
        return new ArrayList<>(active.values());
    }

    int size() {
        return active.size();
    }
}
