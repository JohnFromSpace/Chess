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

    Runnable offerDrawLocked(GameContext ctx, User u) throws IOException {
        if (!ctx.isParticipant(u.getUsername())) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.getGame().getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        String by = u.getUsername();
        String prevOffer = ctx.getGame().getDrawOfferedBy();
        long prevUpdate = ctx.getGame().getLastUpdate();
        ctx.getGame().setDrawOfferedBy(by);
        ctx.getGame().setLastUpdate(System.currentTimeMillis());
        try {
            store.save(ctx.getGame());
        } catch (IOException e) {
            ctx.getGame().setDrawOfferedBy(prevOffer);
            ctx.getGame().setLastUpdate(prevUpdate);
            throw e;
        }

        ClientHandler opp = ctx.opponentHandlerOf(by);
        String gameId = ctx.getGame().getId();
        return drawOfferNotification(opp, gameId, by);
    }

    Runnable respondDrawLocked(GameContext ctx, User u, boolean accept) throws IOException {
        if (!ctx.isParticipant(u.getUsername())) throw new IllegalArgumentException("You are not a participant in this game.");
        if (ctx.getGame().getResult() != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

        String by = ctx.getGame().getDrawOfferedBy();
        if (by == null || by.isBlank()) throw new IllegalArgumentException("No draw offer to respond to.");
        if (by.equals(u.getUsername())) throw new IllegalArgumentException("You cannot respond to your own draw offer.");

        if (accept) {
            return finisher.finishLocked(ctx, Result.DRAW, "Draw agreed.");
        } else {
            String prevOffer = ctx.getGame().getDrawOfferedBy();
            long prevUpdate = ctx.getGame().getLastUpdate();
            ctx.getGame().setDrawOfferedBy(null);
            ctx.getGame().setLastUpdate(System.currentTimeMillis());
            try {
                store.save(ctx.getGame());
            } catch (IOException e) {
                ctx.getGame().setDrawOfferedBy(prevOffer);
                ctx.getGame().setLastUpdate(prevUpdate);
                throw e;
            }

            ClientHandler offerer = ctx.handlerOf(by);
            String gameId = ctx.getGame().getId();
            String responder = u.getUsername();
            return drawDeclineNotification(offerer, gameId, responder);
        }
    }

    private static Runnable drawOfferNotification(ClientHandler opp, String gameId, String by) {
        if (opp == null) return null;
        return () -> opp.pushDrawOffered(gameId, by);
    }

    private static Runnable drawDeclineNotification(ClientHandler offerer, String gameId, String responder) {
        if (offerer == null) return null;
        return () -> offerer.pushDrawDeclined(gameId, responder);
    }
}
