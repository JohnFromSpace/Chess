package com.example.chess.server.tools;

import java.nio.file.Path;

final class BackupEntryRules {
    private BackupEntryRules() {}

    static boolean shouldInclude(Path relative, boolean includeCorrupt) {
        if (relative == null) return false;
        String rel = relative.toString().replace('\\', '/');
        if (rel.isBlank()) return false;

        String name = relative.getFileName() != null ? relative.getFileName().toString() : rel;

        if (name.endsWith(".lock") || name.endsWith(".tmp")) return false;
        if (name.contains(".corrupt-")) return includeCorrupt;

        if (rel.equals("users.json")) return true;
        if (rel.equals("server-state.json")) return true;
        return rel.startsWith("games/") && name.endsWith(".json");
    }

    static boolean shouldRestore(String entryName, boolean includeCorrupt) {
        String name = entryName.replace('\\', '/');
        if (name.startsWith("/")) return false;
        if (name.contains("..")) return false;

        String base = name.substring(name.lastIndexOf('/') + 1);
        if (base.endsWith(".lock") || base.endsWith(".tmp")) return false;
        if (base.contains(".corrupt-")) return includeCorrupt;

        if (name.equals("users.json")) return true;
        if (name.equals("server-state.json")) return true;
        return name.startsWith("games/") && base.endsWith(".json");
    }

    static boolean isSafeEntry(String name) {
        if (name == null) return false;
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.startsWith("\\")) return false;
        return !n.contains("..");
    }
}
