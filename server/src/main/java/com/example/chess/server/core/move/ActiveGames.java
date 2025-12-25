package com.example.chess.server.core.move;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ActiveGames {

    private final Map<String, GameContext> active = new ConcurrentHashMap<>();
    private final Map<String, String> userToGame = new ConcurrentHashMap<>();

    void put(GameContext ctx) {
        active.put(ctx.game.id, ctx);
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
        if (ctx == null || ctx.game == null || ctx.game.id == null) return;

        active.remove(ctx.game.id);

        if (ctx.game.whiteUser != null) userToGame.remove(ctx.game.whiteUser, ctx.game.id);
        if (ctx.game.blackUser != null) userToGame.remove(ctx.game.blackUser, ctx.game.id);
    }

    private void indexUsers(GameContext ctx) {
        if (ctx.game.whiteUser != null) userToGame.put(ctx.game.whiteUser, ctx.game.id);
        if (ctx.game.blackUser != null) userToGame.put(ctx.game.blackUser, ctx.game.id);
    }
}