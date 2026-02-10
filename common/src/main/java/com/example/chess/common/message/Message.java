package com.example.chess.common.message;

public abstract class Message {
    private final String type;
    private final String corrId;

    protected Message(String type, String corrId) {
        this.type = type;
        this.corrId = corrId;
    }

    public String getType() {
        return type;
    }

    public String getCorrId() {
        return corrId;
    }
}
