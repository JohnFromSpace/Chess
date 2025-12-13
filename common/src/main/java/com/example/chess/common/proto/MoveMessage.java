package com.example.chess.common.proto;

import java.util.HashMap;
import java.util.UUID;

public final class MoveMessage extends RequestMessage {
    public MoveMessage(String gameId, String move) {
        super("makeMove", UUID.randomUUID().toString(), new HashMap<>());
        with("gameId", gameId);
        with("move", move);
    }
}