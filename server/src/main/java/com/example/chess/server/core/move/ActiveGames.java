package com.example.chess.server.core.move;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveGames {

    private final Map<String, GameContext> active = new ConcurrentHashMap<>();
    private final Map<String, String> userToGame = new ConcurrentHashMap<>();

    void put(GameContext ctx) {
        active.put(ctx.game.getId(), ctx);
        indexUsers(ctx);
    }

    GameContext mustCtx(String gameId) {
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("Missing gameId.");
        GameContext ctx = active.get(gameId);
        if (ctx == null) throw new IllegalArgumentException("No such active game.");
        return ctx;
    }

    GameContext findCtxByUser(String username) {
        if (username == null) throw new IllegalArgumentException("There is no username.");
        String gid = userToGame.get(username);
        if (gid == null) throw new IllegalArgumentException("There is no game id.");
        return active.get(gid);
    }

    void remove(GameContext ctx) {
        if (ctx == null || ctx.game == null || ctx.game.getId() == null) return;

        String gid = ctx.game.getId();
        active.remove(gid);

        if (ctx.game.getWhiteUser() != null) userToGame.remove(ctx.game.getWhiteUser(), gid);
        if (ctx.game.getBlackUser() != null) userToGame.remove(ctx.game.getBlackUser(), gid);
    }

    private void indexUsers(GameContext ctx) {
        if (ctx.game.getWhiteUser() != null) userToGame.put(ctx.game.getWhiteUser(), ctx.game.getId());
        if (ctx.game.getBlackUser() != null) userToGame.put(ctx.game.getBlackUser(), ctx.game.getId());
    }

    List<GameContext> snapshot() {
        return new ArrayList<>(active.values());
    }
}