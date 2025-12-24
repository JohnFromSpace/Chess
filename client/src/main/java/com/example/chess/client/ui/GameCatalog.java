package com.example.chess.client.ui;

import com.example.chess.client.view.ConsoleView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class GameCatalog {

    public static final class Entry {
        public final String id;
        public final String opponent;
        public final String youAre;
        public final String result;
        public final String reason;
        public final long createdAt;

        public Entry(String id, String opponent, String youAre, String result, String reason, long createdAt) {
            this.id = id;
            this.opponent = opponent;
            this.youAre = youAre;
            this.result = result;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }

    private final List<Entry> entries;

    public GameCatalog(List<Entry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public String resolveToGameId(String token) {
        if (token == null) throw new IllegalArgumentException("Empty input.");
        String s = token.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty input.");
        if (s.startsWith("#")) s = s.substring(1).trim();

        // numeric index
        if (s.chars().allMatch(Character::isDigit)) {
            int idx = Integer.parseInt(s);
            if (idx < 1 || idx > entries.size()) throw new IllegalArgumentException("No such game #: " + idx);
            return entries.get(idx - 1).id;
        }

        // exact id match
        for (Entry e : entries) {
            if (e.id.equalsIgnoreCase(s)) return e.id;
        }

        // prefix match
        List<Entry> matches = new ArrayList<>();
        for (Entry e : entries) {
            if (e.id.toLowerCase(Locale.ROOT).startsWith(s.toLowerCase(Locale.ROOT))) matches.add(e);
        }
        if (matches.size() == 1) return matches.get(0).id;
        if (matches.isEmpty()) throw new IllegalArgumentException("No game id/prefix match: " + token);
        throw new IllegalArgumentException("Ambiguous prefix. Matches: " + matches.size());
    }

    public void print(ConsoleView view) {
        if (entries.isEmpty()) {
            view.showMessage("No games found.");
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        view.showMessage("\n=== Your Games (client-side numbering) ===");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String when = e.createdAt > 0 ? fmt.format(Instant.ofEpochMilli(e.createdAt)) : "?";
            String reason = (e.reason == null || e.reason.isBlank()) ? "" : (" (" + e.reason + ")");
            view.showMessage(String.format(
                    "#%d | %s vs %s | %s%s | %s | id=%s",
                    i + 1, e.youAre, e.opponent, e.result, reason, when, e.id
            ));
        }
    }

    @SuppressWarnings("unchecked")
    public static GameCatalog fromListGamesPayload(Map<String, Object> payload) {
        if (payload == null) return new GameCatalog(List.of());
        Object gObj = payload.get("games");
        if (!(gObj instanceof List<?> gl)) return new GameCatalog(List.of());

        List<Entry> out = new ArrayList<>();
        for (Object o : gl) {
            if (!(o instanceof Map<?, ?> m)) continue;

            String id = str(m.get("id"));
            if (id == null || id.isBlank()) continue;

            out.add(new Entry(
                    id,
                    str(m.get("opponent")),
                    str(m.get("youAre")),
                    str(m.get("result")),
                    str(m.get("reason")),
                    longVal(m.get("createdAt"))
            ));
        }

        // newest first
        out.sort(Comparator.comparingLong((Entry e) -> e.createdAt).reversed());
        return new GameCatalog(out);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? 0L : Long.parseLong(String.valueOf(o)); }
        catch (Exception ignored) { return 0L; }
    }
}