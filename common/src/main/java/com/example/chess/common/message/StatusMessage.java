package com.example.chess.common.message;

import java.util.Map;

public final class StatusMessage {
    private final String type;
    private final String corrId;
    private final boolean error;
    private final String message;
    private final Map<String, Object> payload;

    private StatusMessage(String type, String corrId, boolean error, String message, Map<String, Object> payload) {
        this.type = type;
        this.corrId = corrId;
        this.error = error;
        this.message = message;
        this.payload = payload;
    }

    public static StatusMessage from(ResponseMessage r) {
        return new StatusMessage(r.getType(), r.getCorrId(), r.isError(), r.getMessage(), r.getPayload());
    }

    public boolean isError() { return error; }
    public String getMessage() { return message; }

    public String getType() {
        return type;
    }

    public String getCorrId() {
        return corrId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
