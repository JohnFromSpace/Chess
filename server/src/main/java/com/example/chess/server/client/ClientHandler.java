package com.example.chess.server.client;

import com.example.chess.common.message.MessageCodec;
import com.example.chess.common.UserModels;
import com.example.chess.common.model.Game;
import com.example.chess.common.message.Message;
import com.example.chess.common.message.RequestMessage;
import com.example.chess.common.message.ResponseMessage;
import com.example.chess.server.AuthService;
import com.example.chess.server.core.GameCoordinator;
import com.example.chess.server.core.move.MoveService;
import com.example.chess.server.security.RateLimiter;
import com.example.chess.server.util.Log;
import com.example.chess.server.util.ServerMetrics;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientHandler implements Runnable {

    private static final ConcurrentMap<String, IpLimiter> IP_LIMITERS = new ConcurrentHashMap<>();
    private static final boolean IP_RATE_LIMIT_ENABLED =
            Boolean.parseBoolean(System.getProperty("chess.ratelimit.ip.enabled", "true"));
    private static final long IP_RATE_LIMIT_CAPACITY =
            Long.getLong("chess.ratelimit.ip.capacity", 120L);
    private static final long IP_RATE_LIMIT_REFILL_SECONDS =
            Long.getLong("chess.ratelimit.ip.refillPerSecond", 15L);
    private static final int IP_RATE_LIMIT_MAX_ENTRIES =
            Integer.parseInt(System.getProperty("chess.ratelimit.ip.maxEntries", "10000"));
    private static final long IP_RATE_LIMIT_IDLE_EVICT_MS =
            Long.getLong("chess.ratelimit.ip.idleEvictMs", 900_000L);

    private final Socket socket;

    private final ClientRequestRouter router;
    private final ClientNotifier notifier = new ClientNotifier();
    private final RateLimiter inboundLimiter;
    private final IpLimiter inboundIpLimiter;
    private final Object writeLock = new Object();
    private final String clientIp;
    private final ServerMetrics metrics;

    private BufferedWriter out;

    private volatile UserModels.User currentUser;

    public ClientHandler(Socket socket,
                         AuthService auth,
                         GameCoordinator coordinator,
                         MoveService moves,
                         ServerMetrics metrics) {
        this.socket = socket;
        this.router = new ClientRequestRouter(auth, coordinator, moves, metrics);
        this.clientIp = resolveClientIp(socket);
        this.metrics = metrics;

        boolean rlEnabled = Boolean.parseBoolean(System.getProperty("chess.ratelimit.enabled", "true"));
        long rlCapacity = Long.getLong("chess.ratelimit.capacity", 30L);
        long rlRefillSeconds = Long.getLong("chess.ratelimit.refillPerSecond", 15L);
        this.inboundLimiter = rlEnabled ? new RateLimiter((int) rlCapacity, rlRefillSeconds) : null;
        this.inboundIpLimiter = ipLimiterFor(clientIp);
    }

    public UserModels.User getCurrentUser() { return currentUser; }
    public void setCurrentUser(UserModels.User u) { this.currentUser = u; }

    @Override
    public void run() {
        if (metrics != null) metrics.onConnectionOpen();
        try (Log.ContextScope ignored = Log.withContext(null, clientIp, null);
             socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            int readTimeoutMs =  Integer.parseInt(System.getProperty("chess.socket.readTimeoutMs", "60000"));
            if (readTimeoutMs > 0) socket.setSoTimeout(readTimeoutMs);

            int maxLineChars = Integer.parseInt(System.getProperty("chess.socket.maxLineChars", "16384"));

            String line;
            try {
                while ((line = readLineLimited(in, maxLineChars)) != null) {
                    handleLine(line);
                }
            } catch (LineTooLongException e) {
                if (metrics != null) metrics.onInvalidRequest();
                send(ResponseMessage.error(null, "Request too large."));
            }
        } catch (Exception e) {
            Log.warn("Client disconnected / handler error", e);
        } finally {
            try { router.onDisconnect(this); }
            catch (Exception e) { Log.warn("onDisconnect failed", e); }
            if (metrics != null) metrics.onConnectionClosed();
        }
    }

    private void handleLine(String line) {
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty()) return;

        Message parsed;
        try {
            parsed = MessageCodec.fromJsonLine(line);
        } catch (Exception e) {
            if (metrics != null) metrics.onInvalidRequest();
            send(ResponseMessage.error(null, "Invalid message: " + e.getMessage()));
            return;
        }

        if (!(parsed instanceof RequestMessage req)) {
            if (metrics != null) metrics.onInvalidRequest();
            send(ResponseMessage.error(null, "Client must send request messages."));
            return;
        }

        String username = currentUser != null ? currentUser.getUsername() : null;
        try (Log.ContextScope ignored = Log.withContext(req.corrId, clientIp, username)) {
            if (metrics != null) metrics.onRequest(req.type);
            if (inboundLimiter != null && !inboundLimiter.tryAcquire()) {
                if (metrics != null) metrics.onRateLimited();
                send(ResponseMessage.error(req.corrId, "rate_limited"));
                return;
            }
            if (inboundIpLimiter != null && !inboundIpLimiter.tryAcquire()) {
                if (metrics != null) metrics.onRateLimited();
                send(ResponseMessage.error(req.corrId, "rate_limited"));
                return;
            }

            router.handle(req, this);
        }
    }

    public void send(ResponseMessage m) {
        try {
            String line = MessageCodec.toJsonLine(m);
            synchronized (writeLock) {
                if (out == null) return;
                out.write(line);
                out.flush();
            }
        } catch (Exception e) {
            Log.warn("Failed to send response to client", e);
        }
    }

    public void sendInfo(String message) {
        send(ResponseMessage.push("info", Map.of("message", message)));
    }

    public void pushGameStarted(Game g, boolean isWhite) {
        notifier.gameStarted(this, g, isWhite);
    }

    public void pushMove(Game g, String by, String move, boolean wChk, boolean bChk) {
        notifier.move(this, g, by, move, wChk, bChk);
    }

    public void pushGameOver(Game g, boolean statsOk) {
        notifier.gameOver(this, g, statsOk);
    }

    public void pushDrawOffered(String gameId, String by) {
        notifier.drawOffered(this, gameId, by);
    }

    public void pushDrawDeclined(String gameId, String by) {
        notifier.drawDeclined(this, gameId, by);
    }

    private static String readLineLimited(BufferedReader in, int maxChars) throws IOException {
        if (in == null) return null;
        int limit = Math.max(1, maxChars);
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = in.read();
            if (ch == -1) {
                return sb.isEmpty() ? null : sb.toString();
            }
            if (ch == '\n') {
                return sb.toString();
            }
            if (ch == '\r') {
                if (in.markSupported()) {
                    in.mark(1);
                    int next = in.read();
                    if (next != '\n' && next != -1) {
                        in.reset();
                    }
                }
                return sb.toString();
            }
            sb.append((char) ch);
            if (sb.length() > limit) {
                throw new LineTooLongException("Incoming line exceeds limit: " + limit);
            }
        }
    }

    private static final class LineTooLongException extends IOException {
        private LineTooLongException(String message) {
            super(message);
        }
    }

    private static String resolveClientIp(Socket socket) {
        if (socket == null) return "unknown";
        InetAddress addr = socket.getInetAddress();
        if (addr == null) return "unknown";
        String host = addr.getHostAddress();
        return host == null || host.isBlank() ? "unknown" : host;
    }

    private static IpLimiter ipLimiterFor(String ip) {
        if (!IP_RATE_LIMIT_ENABLED) return null;
        if (ip == null || ip.isBlank()) return null;

        long now = System.currentTimeMillis();
        cleanupIpLimiters(now);

        return IP_LIMITERS.compute(ip, (key, existing) -> {
            boolean expired = IP_RATE_LIMIT_IDLE_EVICT_MS > 0
                    && existing != null
                    && existing.isIdle(now, IP_RATE_LIMIT_IDLE_EVICT_MS);
            if (existing == null || expired) {
                int cap = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, IP_RATE_LIMIT_CAPACITY));
                long refill = Math.max(1L, IP_RATE_LIMIT_REFILL_SECONDS);
                return new IpLimiter(new RateLimiter(cap, refill), now);
            }
            existing.touch(now);
            return existing;
        });
    }

    private static void cleanupIpLimiters(long now) {
        if (IP_RATE_LIMIT_MAX_ENTRIES <= 0) return;
        if (IP_LIMITERS.size() <= IP_RATE_LIMIT_MAX_ENTRIES) return;
        if (IP_RATE_LIMIT_IDLE_EVICT_MS <= 0) return;

        for (Map.Entry<String, IpLimiter> entry : IP_LIMITERS.entrySet()) {
            IpLimiter limiter = entry.getValue();
            if (limiter != null && limiter.isIdle(now, IP_RATE_LIMIT_IDLE_EVICT_MS)) {
                IP_LIMITERS.remove(entry.getKey(), limiter);
            }
        }
    }

    private static final class IpLimiter {
        private final RateLimiter limiter;
        private volatile long lastSeenMs;

        private IpLimiter(RateLimiter limiter, long now) {
            this.limiter = limiter;
            this.lastSeenMs = now;
        }

        private void touch(long now) {
            lastSeenMs = now;
        }

        private boolean isIdle(long now, long idleMs) {
            return now - lastSeenMs >= idleMs;
        }

        private boolean tryAcquire() {
            touch(System.currentTimeMillis());
            return limiter.tryAcquire();
        }
    }
}
