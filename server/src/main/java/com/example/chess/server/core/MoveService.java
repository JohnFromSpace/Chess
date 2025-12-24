package com.example.chess.server.core;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.client.ClientNotifier;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoveService {

    private static final class Session {
        final Game game;
        volatile ClientHandler white;
        volatile ClientHandler black;

        Session(Game game, ClientHandler white, ClientHandler black) {
            this.game = game;
            this.white = white;
            this.black = black;
        }
    }

    private final Map<String, Session> active = new ConcurrentHashMap<>();
    private final StatsService stats;
    private final ClockService clocks;
    private final RulesEngine rules = new RulesEngine();
    private final ClientNotifier notify = new ClientNotifier();

    public MoveService(StatsService stats, ClockService clocks) {
        this.stats = stats;
        this.clocks = clocks;
    }

    public void registerGame(Game g, String whiteUser, String blackUser, ClientHandler h1, ClientHandler h2, boolean h1IsWhite) throws IOException {
        ClientHandler whiteH = h1IsWhite ? h1 : h2;
        ClientHandler blackH = h1IsWhite ? h2 : h1;

        active.put(g.id, new Session(g, whiteH, blackH));

        // persist snapshot so game exists by id even if still ongoing
        stats.saveSnapshot(g);

        notify.gameStarted(whiteH, g, true);
        notify.gameStarted(blackH, g, false);
    }

    public void makeMove(String id, User u, String uci) throws IOException {
        Session s = active.get(id);
        if (s == null) throw new IllegalArgumentException("No such active game.");
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");

        Game g = s.game;

        boolean isWhitePlayer = u.username.equals(g.whiteUser);
        boolean isBlackPlayer = u.username.equals(g.blackUser);
        if (!isWhitePlayer && !isBlackPlayer) throw new IllegalArgumentException("You are not a participant in this game.");

        // enforce turn
        if (g.whiteMove && !isWhitePlayer) throw new IllegalArgumentException("It is WHITE's turn.");
        if (!g.whiteMove && !isBlackPlayer) throw new IllegalArgumentException("It is BLACK's turn.");

        Move m;
        try { m = Move.parse(uci); }
        catch (Exception e) { throw new IllegalArgumentException("Bad move: " + e.getMessage()); }

        if (!rules.isLegalMove(g, g.board, m)) {
            throw new IllegalArgumentException("Illegal move.");
        }

        long now = System.currentTimeMillis();

        // apply move
        rules.applyMove(g.board, g, m, true);

        // store move with timestamp + who
        // format: atMs|by|uci
        g.moves.add(now + "|" + u.username + "|" + m.toString());

        // update clocks + flip side-to-move
        clocks.onMoveApplied(g);

        // compute check flags on resulting position
        boolean whiteInCheck = rules.isKingInCheck(g.board, true);
        boolean blackInCheck = rules.isKingInCheck(g.board, false);

        // checkmate / stalemate detection for side-to-move AFTER flip
        boolean sideToMoveWhite = g.whiteMove;
        boolean hasLegal = rules.hasAnyLegalMove(g, g.board, sideToMoveWhite);
        if (!hasLegal) {
            if (sideToMoveWhite && whiteInCheck) {
                endGame(g, Result.BLACK_WIN, "Checkmate");
            } else if (!sideToMoveWhite && blackInCheck) {
                endGame(g, Result.WHITE_WIN, "Checkmate");
            } else {
                endGame(g, Result.DRAW, "Stalemate");
            }
        }

        // persist snapshot each move (optional but nice for replay correctness)
        stats.saveSnapshot(g);

        // broadcast move + check info
        notify.move(s.white, g, u.username, m.toString(), whiteInCheck, blackInCheck);
        notify.move(s.black, g, u.username, m.toString(), whiteInCheck, blackInCheck);

        // if game ended by the move, broadcast gameOver and cleanup
        if (g.result != Result.ONGOING) {
            boolean ok = stats.finishGame(g);
            notify.gameOver(s.white, g, ok);
            notify.gameOver(s.black, g, ok);
            active.remove(g.id);
            clocks.stop(g.id);
        }
    }

    public void offerDraw(String id, User u) {
        Session s = active.get(id);
        if (s == null) throw new IllegalArgumentException("No such active game.");
        Game g = s.game;

        if (!u.username.equals(g.whiteUser) && !u.username.equals(g.blackUser)) {
            throw new IllegalArgumentException("You are not a participant.");
        }

        g.drawOfferedBy = u.username;

        ClientHandler opp = u.username.equals(g.whiteUser) ? s.black : s.white;
        notify.drawOffered(opp, id, u.username);
    }

    public void respondDraw(String id, User u, boolean accept) throws IOException {
        Session s = active.get(id);
        if (s == null) throw new IllegalArgumentException("No such active game.");
        Game g = s.game;

        if (g.drawOfferedBy == null) throw new IllegalArgumentException("No draw offer to respond to.");
        if (u.username.equals(g.drawOfferedBy)) throw new IllegalArgumentException("You cannot respond to your own draw offer.");

        ClientHandler opp = u.username.equals(g.whiteUser) ? s.black : s.white;

        if (!accept) {
            g.drawOfferedBy = null;
            notify.drawDeclined(opp, id, u.username);
            return;
        }

        endGame(g, Result.DRAW, "Draw agreed");
        boolean ok = stats.finishGame(g);

        notify.gameOver(s.white, g, ok);
        notify.gameOver(s.black, g, ok);

        active.remove(g.id);
        clocks.stop(g.id);
    }

    public void resign(String id, User u) throws IOException {
        Session s = active.get(id);
        if (s == null) throw new IllegalArgumentException("No such active game.");
        Game g = s.game;

        if (!u.username.equals(g.whiteUser) && !u.username.equals(g.blackUser)) {
            throw new IllegalArgumentException("You are not a participant.");
        }

        Result res = u.username.equals(g.whiteUser) ? Result.BLACK_WIN : Result.WHITE_WIN;
        endGame(g, res, "Resignation");

        boolean ok = stats.finishGame(g);
        notify.gameOver(s.white, g, ok);
        notify.gameOver(s.black, g, ok);

        active.remove(g.id);
        clocks.stop(g.id);
    }

    public void onDisconnect(ClientHandler h, User u) {
        if (u == null || u.username == null) return;

        // end any active game involving this user as a forfeit (prevents stuck games)
        for (Iterator<Map.Entry<String, Session>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Session> e = it.next();
            Session s = e.getValue();
            Game g = s.game;

            boolean inGame = u.username.equals(g.whiteUser) || u.username.equals(g.blackUser);
            if (!inGame) continue;

            try {
                Result res = u.username.equals(g.whiteUser) ? Result.BLACK_WIN : Result.WHITE_WIN;
                endGame(g, res, "Opponent disconnected");
                stats.finishGame(g);

                ClientHandler a = s.white;
                ClientHandler b = s.black;
                notify.gameOver(a, g, true);
                notify.gameOver(b, g, true);
            } catch (Exception ignored) {
            }

            it.remove();
            clocks.stop(g.id);
        }
    }

    private static void endGame(Game g, Result r, String reason) {
        if (g.result != Result.ONGOING) return;
        g.result = r;
        g.resultReason = reason;
    }
}