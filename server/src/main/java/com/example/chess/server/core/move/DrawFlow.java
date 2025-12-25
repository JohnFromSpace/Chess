package com.example.chess.server.core.move;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;

import java.io.IOException;

final class DrawFlow {

    private final GameStore store;
    private final GameFinisher finisher;

    DrawFlow(GameStore store, GameFinisher finisher) {
        this.store = store;
        this.finisher = finisher;
    }

    void offerDrawLocked(GameContext ctx, User u) throws IOException {
        if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        ctx.game.drawOfferedBy = u.username;
        ctx.game.lastUpdate = System.currentTimeMillis();
        store.save(ctx.game);

        ClientHandler opp = ctx.opponentHandlerOf(u.username);
        if (opp != null) opp.pushDrawOffered(ctx.game.id, u.username);
    }

    void respondDrawLocked(GameContext ctx, User u, boolean accept) throws IOException {
        if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        String by = ctx.game.drawOfferedBy;
        if (by == null || by.isBlank()) throw new IllegalArgumentException("No draw offer to respond to.");
        if (by.equals(u.username)) throw new IllegalArgumentException("You cannot respond to your own draw offer.");

        if (accept) {
            finisher.finishLocked(ctx, Result.DRAW, "Draw agreed.");
        } else {
            ctx.game.drawOfferedBy = null;
            ctx.game.lastUpdate = System.currentTimeMillis();
            store.save(ctx.game);

            // notify offerer
            ClientHandler offerer = ctx.handlerOf(by);
            if (offerer != null) offerer.pushDrawDeclined(ctx.game.id, u.username);
        }
    }
}