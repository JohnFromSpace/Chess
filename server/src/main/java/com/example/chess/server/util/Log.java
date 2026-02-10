package com.example.chess.server.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Log {
    private static final Logger L = Logger.getLogger("ChessServer");
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    private Log() {}

    public static void init() {
        if (!INIT.compareAndSet(false, true)) return;

        Level level = parseLevel(System.getProperty("chess.log.level", "INFO"));
        L.setLevel(level);
        L.setUseParentHandlers(false);

        Formatter formatter = new PlainFormatter();

        if (Boolean.parseBoolean(System.getProperty("chess.log.console", "true"))) {
            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(level);
            console.setFormatter(formatter);
            L.addHandler(console);
        }

        int maxBytes = parseInt(System.getProperty("chess.log.maxBytes", "10485760"), 10485760);
        int fileCount = parseInt(System.getProperty("chess.log.fileCount", "5"), 5);
        String logDir = System.getProperty("chess.log.dir", "logs");
        if (maxBytes > 0 && fileCount > 0) {
            try {
                Files.createDirectories(Path.of(logDir));
                String pattern = Path.of(logDir, "server-%g.log").toString();
                FileHandler file = new FileHandler(pattern, maxBytes, fileCount, true);
                file.setLevel(level);
                file.setFormatter(formatter);
                L.addHandler(file);
            } catch (Exception e) {
                L.log(Level.WARNING, "Failed to initialize file logging.", e);
            }
        }
    }

    public static void shutdown() {
        for (Handler handler : L.getHandlers()) {
            try {
                handler.flush();
                handler.close();
            } catch (Exception e) {
                L.log(Level.WARNING, "Failed to close log handler.", e);
            }
        }
    }

    public static ContextScope withContext(String corrId, String clientIp, String username) {
        Context prev = CTX.get();
        Context next = new Context(corrId, clientIp, username);
        CTX.set(next);
        return new ContextScope(prev);
    }

    static Context currentContext() {
        return CTX.get();
    }

    public static void info(String msg) {
        L.log(Level.INFO, msg);
    }

    public static void warn(String msg, Throwable t) {
        L.log(Level.WARNING, msg, t);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Level parseLevel(String level) {
        try {
            return Level.parse(level.toUpperCase());
        } catch (Exception e) {
            return Level.INFO;
        }
    }

    private static final class Context {
        final String corrId;
        final String clientIp;
        final String username;

        private Context(String corrId, String clientIp, String username) {
            this.corrId = normalize(corrId);
            this.clientIp = normalize(clientIp);
            this.username = normalize(username);
        }

        private static String normalize(String value) {
            if (value == null) return null;
            String v = value.trim();
            return v.isEmpty() ? null : v;
        }
    }

    public static final class ContextScope implements AutoCloseable {
        private final Context prev;

        private ContextScope(Context prev) {
            this.prev = prev;
        }

        @Override
        public void close() {
            CTX.set(prev);
        }
    }

    private static final class PlainFormatter extends Formatter {
        private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(256);
            sb.append(TS.format(Instant.ofEpochMilli(record.getMillis())));
            sb.append(' ');
            sb.append(record.getLevel().getName());
            sb.append(' ');
            sb.append(record.getLoggerName());
            sb.append(" [");
            sb.append(Thread.currentThread().getName());
            sb.append("] ");
            sb.append(formatMessage(record));

            Context ctx = currentContext();
            if (ctx != null) {
                appendField(sb, "corrId", ctx.corrId);
                appendField(sb, "clientIp", ctx.clientIp);
                appendField(sb, "user", ctx.username);
            }

            sb.append('\n');

            Throwable thrown = record.getThrown();
            if (thrown != null) {
                sb.append(stackTrace(thrown));
            }

            return sb.toString();
        }

        private static String stackTrace(Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        }

        private static void appendField(StringBuilder sb, String key, String value) {
            if (value == null) return;
            sb.append(' ');
            sb.append(key);
            sb.append('=');
            sb.append(value);
        }
    }
}
