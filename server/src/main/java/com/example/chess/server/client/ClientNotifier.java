package com.example.chess.server.client;

import com.example.chess.common.message.ResponseMessage;
import com.example.chess.common.model.Game;

import java.util.HashMap;
import java.util.Map;

public final class ClientNotifier {

    public void gameStarted(ClientHandler h, Game g, boolean isWhite) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.getId());
        p.put("color", isWhite ? "white" : "black");
        p.put("opponent", isWhite ? g.getBlackUser() : g.getWhiteUser());
        p.put("timeControlMs", g.getTimeControlMs());
        p.put("incrementMs", g.getIncrementMs());
        p.put("whiteTimeMs", g.getWhiteTimeMs());
        p.put("blackTimeMs", g.getBlackTimeMs());
        p.put("whiteToMove", g.isWhiteMove());
        p.put("board", g.getBoard().toUnicodePrettyString());
        p.put("capturedByWhite", g.getCapturedByWhite());
        p.put("capturedByBlack", g.getCapturedByBlack());
        p.put("rated", g.isRated());
        h.send(ResponseMessage.push("gameStarted", p));
    }

    public void move(ClientHandler h, Game g, String by, String move, boolean wChk, boolean bChk) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.getId());
        p.put("by", by);
        p.put("move", move);
        p.put("whiteInCheck", wChk);
        p.put("blackInCheck", bChk);
        p.put("whiteTimeMs", g.getWhiteTimeMs());
        p.put("blackTimeMs", g.getBlackTimeMs());
        p.put("whiteToMove", g.isWhiteMove());
        p.put("board", g.getBoard().toUnicodePrettyString());
        p.put("capturedByWhite", g.getCapturedByWhite());
        p.put("capturedByBlack", g.getCapturedByBlack());
        p.put("rated", g.isRated());
        h.send(ResponseMessage.push("move", p));
    }

    public void gameOver(ClientHandler h, Game g, boolean statsOk) {
        Map<String, Object> p = new HashMap<>();
        p.put("gameId", g.getId());
        p.put("result", g.getResult().name());
        p.put("reason", g.getResultReason() == null ? "" : g.getResultReason());
        p.put("statsOk", statsOk);
        p.put("rated", g.isRated());
        p.put("board", g.getBoard().toUnicodePrettyString());
        p.put("capturedByWhite", g.getCapturedByWhite());
        p.put("capturedByBlack", g.getCapturedByBlack());
        h.send(ResponseMessage.push("gameOver", p));
    }

    public void drawOffered(ClientHandler h, String gameId, String by) {
        h.send(ResponseMessage.push("drawOffered", Map.of("gameId", gameId, "by", by)));
    }

    public void drawDeclined(ClientHandler h, String gameId, String by) {
        h.send(ResponseMessage.push("drawDeclined", Map.of("gameId", gameId, "by", by)));
    }
}