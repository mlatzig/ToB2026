package de.latzig.game.tob.client.logic.ui;

/**
 * The 0.5 s fade between start screens (Phase 5a spec decision 2; ToB 2009 {@code FaderUtil.DEFAULT_DURATION}): a
 * screen fades in from black; a change fades out to black, then the change happens and the next screen fades in.
 * While fading out, input is ignored.
 */
public final class ScreenFade {

    public static final double SECONDS = 0.5;

    private enum Phase {
        IN, SHOWN, OUT
    }

    private Phase phase = Phase.IN;
    private double time;
    private Runnable atBlack;

    /** Starts the fade-in of a new screen (from black). */
    public void fadeIn() {
        phase = Phase.IN;
        time = 0.0;
        atBlack = null;
    }

    /** Fades out, runs {@code change} once black, then fades in; ignored while already fading out. */
    public void fadeOut(Runnable change) {
        if (phase == Phase.OUT) {
            return;
        }
        phase = Phase.OUT;
        time = 0.0;
        atBlack = change;
    }

    public void update(double seconds) {
        if (phase == Phase.SHOWN) {
            return;
        }
        time += seconds;
        if (time < SECONDS) {
            return;
        }
        if (phase == Phase.IN) {
            phase = Phase.SHOWN;
            return;
        }
        Runnable change = atBlack;
        fadeIn();
        if (change != null) {
            change.run();
        }
    }

    /** The black overlay's alpha: 1 at black, 0 when the screen is fully shown. */
    public float alpha() {
        return switch (phase) {
            case SHOWN -> 0f;
            case IN -> (float) Math.max(0.0, 1.0 - time / SECONDS);
            case OUT -> (float) Math.min(1.0, time / SECONDS);
        };
    }

    /** While fading out, the screen takes no input. */
    public boolean isLeaving() {
        return phase == Phase.OUT;
    }
}
