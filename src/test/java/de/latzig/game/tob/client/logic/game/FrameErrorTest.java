package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameErrorTest {

    @Test
    void keepsSimFailuresOwnTowerAndFrame() {
        SimFailure failure = new SimFailure("original-003", 42, new IllegalStateException("bad state"));

        // towerId/frame of the current attempt are ignored in favour of SimFailure's own, which is where the bad
        // state was actually produced.
        FrameError error = FrameError.of("other-tower", 999, failure);

        assertEquals("Simulation error in original-003 at frame 42", error.message());
        assertEquals("Tower original-003 failed at frame 42", error.logMessage());
    }

    @Test
    void buildsAGenericMessageForAnyOtherRuntimeException() {
        FrameError error = FrameError.of("tutorial-000", 7, new NullPointerException("boom"));

        assertEquals("Game error in tutorial-000 at frame 7: boom", error.message());
        assertEquals("Tower tutorial-000 failed at frame 7", error.logMessage());
    }

    @Test
    void toleratesAMissingExceptionMessage() {
        FrameError error = FrameError.of("tutorial-000", 7, new IllegalStateException());

        assertEquals("Game error in tutorial-000 at frame 7", error.message());
    }
}
