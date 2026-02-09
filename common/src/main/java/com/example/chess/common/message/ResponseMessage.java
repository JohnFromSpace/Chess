package com.example.chess.common.message;

import java.util.HashMap;
import java.util.Map;

public class ResponseMessage extends Message {
    public final boolean error;
    public final String message;
    public final Map<String, Object> payload;

    public ResponseMessage(String type, String corrId,
                           boolean error, String message,
                           Map<String, Object> payload) {
        super(type, corrId);
        this.error = error;
        this.message = message;
        this.payload = payload;
    }

    public static ResponseMessage ok(String type, String corrId) {
        return new ResponseMessage(type, corrId, false, null, new HashMap<>());
    }

    public static ResponseMessage ok(String type, String corrId, Map<String,Object> payload) {
        return new ResponseMessage(type, corrId, false, null, payload);
    }

    public static ResponseMessage push(String type, Map<String, Object> payload) {
        return new ResponseMessage(type, null, false, null, payload != null ? payload : new HashMap<>());
    }

    public static ResponseMessage error(String corrId, String message) {
        return new ResponseMessage("error", corrId, true, message, new HashMap<>());
    }
}