package com.example.chess.client;

import com.example.chess.common.proto.StatusMessage;

public interface ClientMessageListener {
    void onMessage(StatusMessage msg);
}

