package com.example.chess.server.client;

import com.example.chess.common.model.Game;
import com.example.chess.common.message.ResponseMessage;

import java.util.HashMap;
import java.util.Map;

public final class ClientNotifier {

    public void gameStarted(ClientHandler h, Game g, boolean isWhite) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.id);
        p.put("color", isWhite ? "white" : "black");
        p.put("opponent", isWhite ? g.blackUser : g.whiteUser);
        p.put("timeControlMs", g.timeControlMs);
        p.put("incrementMs", g.incrementMs);
        p.put("whiteTimeMs", g.whiteTimeMs);
        p.put("blackTimeMs", g.blackTimeMs);
        p.put("whiteToMove", g.whiteMove);
        p.put("board", g.board.toPrettyString());
        h.send(ResponseMessage.push("gameStarted", p));
    }

    public void move(ClientHandler h, Game g, String by, String move, boolean wChk, boolean bChk) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.id);
        p.put("by", by);
        p.put("move", move);
        p.put("whiteInCheck", wChk);
        p.put("blackInCheck", bChk);
        p.put("whiteTimeMs", g.whiteTimeMs);
        p.put("blackTimeMs", g.blackTimeMs);
        p.put("whiteToMove", g.whiteMove);
        p.put("board", g.board.toPrettyString());
        h.send(ResponseMessage.push("move", p));
    }

    public void gameOver(ClientHandler h, Game g, boolean statsOk) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.id);
        p.put("result", g.result.name());
        p.put("reason", g.resultReason == null ? "" : g.resultReason);
        p.put("statsOk", statsOk);
        p.put("board", g.board.toPrettyString());
        h.send(ResponseMessage.push("gameOver", p));
    }

    public void drawOffered(ClientHandler h, String gameId, String by) {
        h.send(ResponseMessage.push("drawOffered", Map.of("gameId", gameId, "by", by)));
    }

    public void drawDeclined(ClientHandler h, String gameId, String by) {
        h.send(ResponseMessage.push("drawDeclined", Map.of("gameId", gameId, "by", by)));
    }
}