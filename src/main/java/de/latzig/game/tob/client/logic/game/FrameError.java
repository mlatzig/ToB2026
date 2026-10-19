package de.latzig.game.tob.client.logic.game;

import java.util.Objects;

/**
 * The log line and the message for an exception during a running game frame (spec §6): a frame runs the
 * simulation step plus every view, camera, sky, HUD and noise update, so beyond a {@link SimFailure} (a bad
 * simulation state, with its own tower id and the frame that produced it) a view, animation or HUD bug can throw
 * too. Either way the game must return to the start screens with a readable message instead of reaching the
 * application's error handler. {@code GameState} catches {@link RuntimeException} once around the frame body and
 * asks this class what to show; {@link SimFailure}'s own message and frame are kept, everything else gets a
 * generic message built from the current tower id and frame.
 */
public final class FrameError {

    private final String logMessage;
    private final String message;

    private FrameError(String logMessage, String message) {
        this.logMessage = logMessage;
        this.message = message;
    }

    /**
     * @param towerId the current attempt's tower id, used unless {@code failure} is a {@link SimFailure}
     * @param frame   the current frame, used unless {@code failure} is a {@link SimFailure} (which carries the
     *                frame where the bad state was produced, not necessarily the current one)
     */
    public static FrameError of(String towerId, int frame, RuntimeException failure) {
        Objects.requireNonNull(towerId, "towerId");
        Objects.requireNonNull(failure, "failure");
        if (failure instanceof SimFailure simFailure) {
            return new FrameError("Tower " + simFailure.towerId() + " failed at frame " + simFailure.frame(),
                    "Simulation error in " + simFailure.towerId() + " at frame " + simFailure.frame());
        }
        String detail = failure.getMessage() == null ? "" : ": " + failure.getMessage();
        return new FrameError("Tower " + towerId + " failed at frame " + frame,
                "Game error in " + towerId + " at frame " + frame + detail);
    }

    /** The log line; the caller still passes the exception itself for the stack trace. */
    public String logMessage() {
        return logMessage;
    }

    /** The message the start screens show (Phase 5a; the tower picker until then). */
    public String message() {
        return message;
    }
}
