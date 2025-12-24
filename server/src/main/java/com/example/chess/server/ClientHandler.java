package com.example.chess.server;

import com.example.chess.common.MessageCodec;
import com.example.chess.common.UserModels.User;
import com.example.chess.common.model.Game;
import com.example.chess.common.proto.Message;
import com.example.chess.common.proto.RequestMessage;
import com.example.chess.common.proto.ResponseMessage;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final GameCoordinator gameCoordinator;

    private BufferedReader in;
    private BufferedWriter out;
    private User currentUser;

    public ClientHandler(Socket socket, AuthService authService, GameCoordinator gameCoordinator) {
        this.socket = socket;
        this.authService = authService;
        this.gameCoordinator = gameCoordinator;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse line: " + e.getMessage());
        } finally {
            try {
                gameCoordinator.onUserOffline(this, currentUser);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to exrtact" + e.getMessage());
            }
        }
    }

    private void handleLine(String line) {
        Message parsed;
        try {
            parsed = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage()));
            return;
        }

        if (!(parsed instanceof RequestMessage msg)) {
            send(ResponseMessage.error(null, "Client must send request messages."));
            return;
        }

        String type = msg.type;
        String corrId = msg.corrId;

        try {
            switch (type) {
                case "ping"        -> handlePing(msg);
                case "register"    -> handleRegister(msg);
                case "login"       -> handleLogin(msg);

                case "requestGame" -> handleRequestGame(msg);
                case "makeMove"    -> handleMakeMove(msg);
                case "offerDraw"   -> handleOfferDraw(msg);
                case "acceptDraw"  -> handleRespondDraw(msg, true);
                case "declineDraw" -> handleRespondDraw(msg, false);
                case "resign"      -> handleResign(msg);

                case "listGames"      -> handleListGames(msg);
                case "getGameDetails" -> handleGetGameDetails(msg);
                case "getStats"       -> handleGetStats(msg);

                default -> send(ResponseMessage.error(corrId, "Unknown message type: " + type));
            }
        } catch (IllegalArgumentException ex) {
            send(ResponseMessage.error(corrId, ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            send(ResponseMessage.error(corrId, "Internal server error."));
        }
    }

    private static String reqStr(RequestMessage m, String key) {
        Object v = m.payload.get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Blank field: " + key);
        return s;
    }

    private void handlePing(RequestMessage inMsg) {
        send(ResponseMessage.ok("pong", inMsg.corrId));
    }

    private void handleRegister(RequestMessage inMsg) {
        String username = reqStr(inMsg, "username");
        String name     = reqStr(inMsg, "name");
        String password = reqStr(inMsg, "password");

        User user = authService.register(username, name, password);

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        send(ResponseMessage.ok("registerOk", inMsg.corrId, payload));
    }

    private void handleLogin(RequestMessage inMsg) {
        String username = reqStr(inMsg, "username");
        String password = reqStr(inMsg, "password");

        User user = authService.login(username, password);

        // register online first; may throw if already logged in elsewhere
        gameCoordinator.onUserOnline(this, user);

        // set only after successful registration
        this.currentUser = user;

        Map<String, Object> u = new HashMap<>();
        u.put("username", user.username);
        u.put("name", user.name);
        u.put("played", user.stats.played);
        u.put("won", user.stats.won);
        u.put("drawn", user.stats.drawn);
        u.put("rating", user.stats.rating);

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", u);

        send(ResponseMessage.ok("loginOk", inMsg.corrId, payload));
    }

    private void handleRequestGame(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        gameCoordinator.requestGame(this, currentUser);
        send(ResponseMessage.ok("requestGameOk", inMsg.corrId));
    }

    private void handleMakeMove(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");
        String move   = reqStr(inMsg, "move");
        gameCoordinator.makeMove(gameId, currentUser, move);
        send(ResponseMessage.ok("makeMoveOk", inMsg.corrId));
    }

    private void handleOfferDraw(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");
        gameCoordinator.offerDraw(gameId, currentUser);
        send(ResponseMessage.ok("offerDrawOk", inMsg.corrId));
    }

    private void handleRespondDraw(RequestMessage inMsg, boolean accept) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");
        gameCoordinator.respondDraw(gameId, currentUser, accept);
        send(ResponseMessage.ok(accept ? "acceptDrawOk" : "declineDrawOk", inMsg.corrId));
    }

    private void handleResign(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");
        gameCoordinator.resign(gameId, currentUser);
        send(ResponseMessage.ok("resignOk", inMsg.corrId));
    }

    private void handleListGames(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");

        List<Game> games = gameCoordinator.listGamesForUser(currentUser.username);

        List<Map<String, Object>> outGames = new ArrayList<>();
        for (Game g : games) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.id);
            m.put("whiteUser", g.whiteUser);
            m.put("blackUser", g.blackUser);
            m.put("result", String.valueOf(g.result));
            m.put("reason", g.resultReason);
            m.put("createdAt", g.createdAt);
            m.put("lastUpdate", g.lastUpdate);

            String me = currentUser.username;
            String opponent = me.equals(g.whiteUser) ? g.blackUser : g.whiteUser;
            String color = me.equals(g.whiteUser) ? "WHITE" : "BLACK";
            m.put("opponent", opponent);
            m.put("youAre", color);

            outGames.add(m);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("games", outGames);
        send(ResponseMessage.ok("listGamesOk", inMsg.corrId, payload));
    }

    private void handleGetGameDetails(RequestMessage inMsg) throws IOException {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");
        String gameId = reqStr(inMsg, "gameId");

        Game g = gameCoordinator.getGameForUser(gameId, currentUser.username);
        if (g == null) {
            throw new IllegalArgumentException("No such game (or you are not a participant).");
        }

        Map<String, Object> gm = new HashMap<>();
        gm.put("id", g.id);
        gm.put("whiteUser", g.whiteUser);
        gm.put("blackUser", g.blackUser);
        gm.put("result", String.valueOf(g.result));
        gm.put("reason", g.resultReason);
        gm.put("createdAt", g.createdAt);
        gm.put("lastUpdate", g.lastUpdate);
        gm.put("moves", g.moves == null ? List.of() : new ArrayList<>(g.moves));
        gm.put("board", g.board == null ? null : g.board.toPrettyString());

        Map<String, Object> payload = new HashMap<>();
        payload.put("game", gm);

        send(ResponseMessage.ok("getGameDetailsOk", inMsg.corrId, payload));
    }

    private void handleGetStats(RequestMessage msg) {
        if (currentUser == null) throw new IllegalArgumentException("You must be logged in.");

        User fresh = authService.getUser(currentUser.username);
        currentUser = fresh;

        Map<String, Object> payload = new HashMap<>();
        if (fresh.stats == null) {
            payload.put("played", 0);
            payload.put("won", 0);
            payload.put("drawn", 0);
            payload.put("rating", 1000);
        } else {
            payload.put("played", fresh.stats.played);
            payload.put("won", fresh.stats.won);
            payload.put("drawn", fresh.stats.drawn);
            payload.put("rating", fresh.stats.rating);
        }

        send(ResponseMessage.ok("getStatsOk", msg.corrId, payload));
    }

    void sendGameStarted(Game game, boolean isWhite, boolean isReconnect) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("opponent", isWhite ? game.blackUser : game.whiteUser);
        payload.put("youAre", isWhite ? "WHITE" : "BLACK");
        payload.put("reconnect", isReconnect);
        payload.put("board", game.board.toPrettyString());
        payload.put("whiteTimeMs", game.whiteTimeMs);
        payload.put("blackTimeMs", game.blackTimeMs);
        payload.put("whiteMove", game.whiteMove);
        send(ResponseMessage.push("gameStarted", payload));
    }

    void sendMove(Game game, String byUser, String moveStr, boolean whiteInCheck, boolean blackInCheck) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("by", byUser);
        payload.put("move", moveStr);
        payload.put("board", game.board.toPrettyString());
        payload.put("whiteTimeMs", game.whiteTimeMs);
        payload.put("blackTimeMs", game.blackTimeMs);
        payload.put("whiteMove", game.whiteMove);
        send(ResponseMessage.push("movePlayed", payload));
    }

    void sendGameOver(Game game, boolean statsOk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id);
        payload.put("result", String.valueOf(game.result));
        payload.put("reason", game.resultReason);
        payload.put("statsOk", statsOk);
        payload.put("board", game.board.toPrettyString());
        send(ResponseMessage.push("gameOver", payload));
    }

    void sendDrawOffered(String gameId, String byUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("by", byUser);
        send(ResponseMessage.push("drawOffered", payload));
    }

    void sendDrawDeclined(String gameId, String byUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("by", byUser);
        send(ResponseMessage.push("drawDeclined", payload));
    }

    void sendInfo(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        send(ResponseMessage.push("info", payload));
    }

    private void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            synchronized (out) {
                out.write(line);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }
}
