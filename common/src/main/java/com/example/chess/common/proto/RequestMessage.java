package com.example.chess.common.proto;

import java.util.HashMap;
import java.util.Map;

public class RequestMessage extends Message {
    public final Map<String, Object> payload;

    public RequestMessage(String type, String corrId, Map<String, Object> payload) {
        super(type, corrId);
        this.payload = payload;
    }

    public static RequestMessage of(String type) {
        return new RequestMessage(type,
                java.util.UUID.randomUUID().toString(),
                new HashMap<>());
    }

    public RequestMessage with(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }
}
