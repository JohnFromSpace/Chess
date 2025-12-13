package com.example.chess.common.proto;

import java.util.Map;

public final class StatusMessage {
    public final String type;
    public final String corrId;
    public final boolean error;
    public final String message;
    public final Map<String, Object> payload;

    private StatusMessage(String type, String corrId, boolean error, String message, Map<String, Object> payload) {
        this.type = type;
        this.corrId = corrId;
        this.error = error;
        this.message = message;
        this.payload = payload;
    }

    public static StatusMessage from(ResponseMessage r) {
        return new StatusMessage(r.type, r.corrId, r.error, r.message, r.payload);
    }

    public boolean isError() { return error; }
    public String getMessage() { return message; }
}