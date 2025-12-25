package com.example.chess.common.message;

public abstract class Message {
    public final String type;
    public final String corrId;

    protected Message(String type, String corrId) {
        this.type = type;
        this.corrId = corrId;
    }
}