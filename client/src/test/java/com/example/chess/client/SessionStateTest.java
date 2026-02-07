package com.example.chess.client;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class SessionStateTest {

    @Test
    public void clearGameResetsState() {
        SessionState state = new SessionState();
        state.setActiveGameId("g1");
        state.setInGame(true);
        state.setWhite(true);
        state.setLastBoard("board");
        state.setWaitingForMatch(true);

        state.clearGame();

        assertNull(state.getActiveGameId());
        assertFalse(state.isInGame());
        assertFalse(state.isWhite());
        assertNull(state.getLastBoard());
        assertFalse(state.isWaitingForMatch());
    }
}
