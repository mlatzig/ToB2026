package de.latzig.game.tob.client.logic.game;

/**
 * Whether the developer recording is on (Phase 3c rework, author's decision of 2026-10-09: recordings exist only as a
 * development tool for regression tests). Off unless the game starts with {@code -Dtob.record=true}; F12 in a game
 * toggles it for the rest of the session. One instance per game, shared by its attempts ({@link AttemptRecorder}).
 * Phase 7c: a game started from a package uses {@link #locked()}, which stays off whatever F12 does.
 */
public final class RecordingSwitch {

    private final boolean locked;
    private boolean on;

    public RecordingSwitch(boolean on) {
        this(on, false);
    }

    private RecordingSwitch(boolean on, boolean locked) {
        this.on = on;
        this.locked = locked;
    }

    /** Phase 7c: the switch of a packaged game - off, and F12 ({@link #toggle}) leaves it off. */
    public static RecordingSwitch locked() {
        return new RecordingSwitch(false, true);
    }

    public boolean isOn() {
        return on;
    }

    /** F12; does nothing on a {@link #locked()} switch. */
    public void toggle() {
        if (!locked) {
            on = !on;
        }
    }
}
