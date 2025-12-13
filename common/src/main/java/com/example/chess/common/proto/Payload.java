package com.example.chess.common.proto;

import java.util.List;
import java.util.Map;

public final class Payload {
    private Payload() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object v) {
        if (v == null) return null;
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Expected Map but got: " + v.getClass());
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object v) {
        if (v == null) return null;
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new IllegalArgumentException("Expected List but got: " + v.getClass());
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static int intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(str(v));
    }

    public static boolean boolVal(Object v) {
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(str(v));
    }
}