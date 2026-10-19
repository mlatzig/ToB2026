package de.latzig.game.tob.client.logic.game;

/**
 * The game's (GameState) mouse cursor auto-hide (author's extra, 2026-10-14): while the "Hide Mouse Cursor" option
 * is on, the software cursor hides after {@value #IDLE_SECONDS} s of active play without a mouse movement or a
 * click, and shows again at once on either. Engine-free, tested on its own;
 * {@link de.latzig.game.tob.client.GameState} feeds it with every render frame's time and whether the mouse moved
 * or was clicked since the last one, but only while play is active
 * (not paused with the menu open or its ARE YOU SURE?/QUIT? dialogs, not on the end screen): otherwise it calls
 * {@link #reset()} instead, so the cursor always shows there and starts a fresh idle timer once play resumes. The
 * start screens, the Options window and the result window never touch this class: their own cursor always stays
 * visible.
 */
public final class CursorAutoHide {

    public static final double IDLE_SECONDS = 3.0;

    private double idle;
    private boolean visible = true;

    /**
     * One render frame of active play.
     *
     * @param dt      the frame's time in seconds
     * @param moved   whether the mouse moved or was clicked since the last frame: shows the cursor at once and
     *                restarts the idle timer
     * @param enabled the "Hide Mouse Cursor" option; while it is off the cursor never hides and the timer does not
     *                run, so turning it back on starts a fresh idle timer
     */
    public void update(double dt, boolean moved, boolean enabled) {
        if (!enabled || moved) {
            idle = 0.0;
            visible = true;
            return;
        }
        idle += dt;
        if (idle >= IDLE_SECONDS) {
            visible = false;
        }
    }

    public boolean visible() {
        return visible;
    }

    /** Play is not active (paused, the end screen, or the game is left): visible again at once, the timer cleared. */
    public void reset() {
        idle = 0.0;
        visible = true;
    }
}
