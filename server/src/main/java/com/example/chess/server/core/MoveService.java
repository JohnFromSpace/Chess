package com.example.chess.server.core;

import com.example.chess.common.UserModels.User;
import com.example.chess.common.board.Board;
import com.example.chess.common.board.Move;
import com.example.chess.common.model.Game;
import com.example.chess.common.model.Result;
import com.example.chess.server.client.ClientHandler;
import com.example.chess.server.fs.repository.GameRepository;
import com.example.chess.server.logic.RulesEngine;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoveService {

    public static final class GameContext {
        public final Game game;
        public volatile ClientHandler white;
        public volatile ClientHandler black;

        public volatile long whiteOfflineAtMs = 0L;
        public volatile long blackOfflineAtMs = 0L;

        GameContext(Game game, ClientHandler white, ClientHandler black) {
            this.game = game;
            this.white = white;
            this.black = black;
        }

        boolean isWhiteUser(String username) { return username != null && username.equals(game.whiteUser); }
        boolean isParticipant(String username) { return username != null && (username.equals(game.whiteUser) || username.equals(game.blackUser)); }

        ClientHandler handlerOf(String username) { return isWhiteUser(username) ? white : black; }
        ClientHandler opponentHandlerOf(String username) { return isWhiteUser(username) ? black : white; }
    }

    private final Map<String, GameContext> active = new ConcurrentHashMap<>();
    private final Map<String, String> userToGame = new ConcurrentHashMap<>();

    private final ReconnectService reconnects = new ReconnectService(60_000L);
    private final GameRepository gameRepo;
    private final ClockService clocks;
    private final RulesEngine rules = new RulesEngine();

    public MoveService(GameRepository gameRepo, ClockService clocks) {
        this.gameRepo = gameRepo;
        this.clocks = clocks;
    }

    // ---- matchmaking entry point (keeps compatibility with your current MatchmakingService call) ----
    public void registerGame(Game g,
                             String whiteUser,
                             String blackUser,
                             ClientHandler h1,
                             ClientHandler h2,
                             boolean h1IsWhite) throws IOException {

        if (g == null || g.id == null || g.id.isBlank()) return;

        if (g.whiteUser == null || g.whiteUser.isBlank()) g.whiteUser = whiteUser;
        if (g.blackUser == null || g.blackUser.isBlank()) g.blackUser = blackUser;

        ClientHandler whiteH = h1IsWhite ? h1 : h2;
        ClientHandler blackH = h1IsWhite ? h2 : h1;

        registerGame(g, whiteH, blackH);
    }

    public void registerGame(Game g, ClientHandler whiteH, ClientHandler blackH) throws IOException {
        if (g == null || g.id == null || g.id.isBlank()) return;

        long now = System.currentTimeMillis();
        if (g.createdAt == 0L) g.createdAt = now;
        g.lastUpdate = now;
        g.result = Result.ONGOING;
        if (g.board == null) g.board = com.example.chess.common.board.Board.initial();

        GameContext ctx = new GameContext(g, whiteH, blackH);
        active.put(g.id, ctx);

        if (g.whiteUser != null) userToGame.put(g.whiteUser, g.id);
        if (g.blackUser != null) userToGame.put(g.blackUser, g.id);

        // make sure clocks exist even if matchmaking forgot
        clocks.register(g);

        safeSave(g);

        if (whiteH != null) whiteH.pushGameStarted(g, true);
        if (blackH != null) blackH.pushGameStarted(g, false);
    }

    public void makeMove(String gameId, User u, String uci) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = mustCtx(gameId);

        synchronized (ctx) {
            if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
            if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

            boolean moverIsWhite = ctx.isWhiteUser(u.username);
            if (ctx.game.whiteMove != moverIsWhite) throw new IllegalArgumentException("Not your turn.");

            Move move = Move.parse(uci);

            if (!rules.isLegalMove(ctx.game, ctx.game.board, move))
                throw new IllegalArgumentException("Illegal move.");

            // king-safety check (no self-check)
            Board test = ctx.game.board.copy();
            rules.applyMove(test, ctx.game, move, false);
            if (rules.isKingInCheck(test, moverIsWhite))
                throw new IllegalArgumentException("Illegal move: your king would be in check.");

            // apply for real (+ update castling/ep state)
            rules.applyMove(ctx.game.board, ctx.game, move, true);

            // record move with timestamp
            ctx.game.recordMove(u.username, move.toString());

            // update clock + flip side-to-move
            clocks.onMoveApplied(ctx.game);

            // check flags (after move)
            boolean wChk = rules.isKingInCheck(ctx.game.board, true);
            boolean bChk = rules.isKingInCheck(ctx.game.board, false);

            // timeout check
            if (ctx.game.whiteTimeMs <= 0) {
                finish(ctx, Result.BLACK_WIN, "Time.");
                return;
            }
            if (ctx.game.blackTimeMs <= 0) {
                finish(ctx, Result.WHITE_WIN, "Time.");
                return;
            }

            // mate/stalemate check for side-to-move after flip
            boolean whiteToMove = ctx.game.whiteMove;
            boolean inCheck = rules.isKingInCheck(ctx.game.board, whiteToMove);
            boolean anyLegal = rules.hasAnyLegalMove(ctx.game, ctx.game.board, whiteToMove);

            if (!anyLegal) {
                if (inCheck) {
                    finish(ctx, whiteToMove ? Result.BLACK_WIN : Result.WHITE_WIN, "Checkmate.");
                } else {
                    finish(ctx, Result.DRAW, "Stalemate.");
                }
                return;
            }

            // persist snapshot after each move (good for history/replay even if server dies)
            safeSave(ctx.game);

            // push move to both (if connected)
            if (ctx.white != null) ctx.white.pushMove(ctx.game, u.username, move.toString(), wChk, bChk);
            if (ctx.black != null) ctx.black.pushMove(ctx.game, u.username, move.toString(), wChk, bChk);
        }
    }

    public void offerDraw(String gameId, User u) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = mustCtx(gameId);

        synchronized (ctx) {
            if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
            if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

            ctx.game.drawOfferedBy = u.username;
            ctx.game.lastUpdate = System.currentTimeMillis();
            safeSave(ctx.game);

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.pushDrawOffered(gameId, u.username);
        }
    }

    public void respondDraw(String gameId, User u, boolean accept) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = mustCtx(gameId);

        synchronized (ctx) {
            if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
            if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

            String by = ctx.game.drawOfferedBy;
            if (by == null || by.isBlank()) throw new IllegalArgumentException("No draw offer to respond to.");
            if (by.equals(u.username)) throw new IllegalArgumentException("You cannot respond to your own draw offer.");

            if (accept) {
                finish(ctx, Result.DRAW, "Draw agreed.");
            } else {
                ctx.game.drawOfferedBy = null;
                ctx.game.lastUpdate = System.currentTimeMillis();
                safeSave(ctx.game);

                // notify offerer
                ClientHandler offerer = ctx.handlerOf(by);
                if (offerer != null) offerer.pushDrawDeclined(gameId, u.username);
            }
        }
    }

    public void resign(String gameId, User u) throws IOException {
        if (u == null || u.username == null) throw new IllegalArgumentException("Not logged in.");
        GameContext ctx = mustCtx(gameId);

        synchronized (ctx) {
            if (!ctx.isParticipant(u.username)) throw new IllegalArgumentException("You are not a participant in this game.");
            if (ctx.game.result != Result.ONGOING) throw new IllegalArgumentException("Game is already finished.");

            boolean leaverWhite = ctx.isWhiteUser(u.username);
            finish(ctx, leaverWhite ? Result.BLACK_WIN : Result.WHITE_WIN, "Resignation.");
        }
    }

    // called by coordinator on disconnect
    public void onDisconnect(ClientHandler h, User u) {
        if (u == null || u.username == null) return;

        GameContext ctx = findCtxByUser(u.username);
        if (ctx == null) return;

        synchronized (ctx) {
            if (ctx.game.result != Result.ONGOING) return;

            boolean isWhite = ctx.isWhiteUser(u.username);
            long now = System.currentTimeMillis();

            if (isWhite) {
                ctx.whiteOfflineAtMs = now;
                ctx.game.whiteOfflineSince = now;
            } else {
                ctx.blackOfflineAtMs = now;
                ctx.game.blackOfflineSince = now;
            }

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " disconnected. Waiting 60s for reconnect...");

            String key = ctx.game.id + ":" + u.username;
            reconnects.scheduleDrop(key, () -> {
                try {
                    synchronized (ctx) {
                        long off = isWhite ? ctx.whiteOfflineAtMs : ctx.blackOfflineAtMs;
                        if (off == 0L) return; // reconnected
                        if (ctx.game.result != Result.ONGOING) return;

                        boolean leaverWhite = isWhite;
                        finish(ctx, leaverWhite ? Result.BLACK_WIN : Result.WHITE_WIN,
                                "Disconnected for more than 60 seconds.");
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    // called on login (router calls it AFTER loginOk response)
    public boolean tryReconnect(User u, ClientHandler newHandler) {
        if (u == null || u.username == null || newHandler == null) return false;

        GameContext ctx = findCtxByUser(u.username);
        if (ctx == null) return false;

        synchronized (ctx) {
            if (ctx.game.result != Result.ONGOING) return false;

            boolean isWhite = ctx.isWhiteUser(u.username);

            // cancel grace timer + clear offline markers
            reconnects.cancel(ctx.game.id + ":" + u.username);

            if (isWhite) {
                ctx.white = newHandler;
                ctx.whiteOfflineAtMs = 0L;
                ctx.game.whiteOfflineSince = 0L;
            } else {
                ctx.black = newHandler;
                ctx.blackOfflineAtMs = 0L;
                ctx.game.blackOfflineSince = 0L;
            }

            // push current state (client will immediately enter game)
            newHandler.pushGameStarted(ctx.game, isWhite);

            ClientHandler opp = ctx.opponentHandlerOf(u.username);
            if (opp != null) opp.sendInfo(u.username + " reconnected.");

            return true;
        }
    }

    // ---- helpers ----

    private GameContext mustCtx(String gameId) {
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("Missing gameId.");
        GameContext ctx = active.get(gameId);
        if (ctx == null) throw new IllegalArgumentException("No such active game.");
        return ctx;
    }

    private GameContext findCtxByUser(String username) {
        String gid = userToGame.get(username);
        if (gid == null) return null;
        return active.get(gid);
    }

    private void finish(GameContext ctx, Result result, String reason) throws IOException {
        ctx.game.result = result;
        ctx.game.resultReason = reason == null ? "" : reason;
        ctx.game.lastUpdate = System.currentTimeMillis();

        safeSave(ctx.game);

        // notify connected handlers
        if (ctx.white != null) ctx.white.pushGameOver(ctx.game, true);
        if (ctx.black != null) ctx.black.pushGameOver(ctx.game, true);

        cleanup(ctx);
    }

    private void cleanup(GameContext ctx) {
        try { clocks.stop(ctx.game.id); } catch (Exception ignored) {}

        active.remove(ctx.game.id);

        if (ctx.game.whiteUser != null) userToGame.remove(ctx.game.whiteUser, ctx.game.id);
        if (ctx.game.blackUser != null) userToGame.remove(ctx.game.blackUser, ctx.game.id);
    }

    private void safeSave(Game g) throws IOException {
        if (gameRepo != null) gameRepo.saveGame(g);
    }
}