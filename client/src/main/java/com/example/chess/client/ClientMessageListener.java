package com.example.chess.client;

import com.example.chess.common.proto.ResponseMessage;

public interface ClientMessageListener {
    void onMessage(ResponseMessage msg);
}

