package de.latzig.game.tob.client.logic.frontend;

/**
 * The original's interlude choice (Phase 5c-2 spec decision 6; report 17 §6, {@code S:6472-6478},
 * {@code S:6777-6783} (success), {@code S:6779-6785} (failure)): one rotation counter {@code W001690}, shared by
 * successes and failures, starting at 0, pre-incremented before every use and never reset. A success shows demo
 * {@code counter mod 6} (0-5, the "... Demo" towers), a failure or an abandon demo {@code (counter mod 6) + 6} (6-11,
 * the "... Dies" towers), so the first interludes after the start are #1 and #7. ToB 2026 keeps it for one program
 * run, as the original.
 */
public final class InterludePicker {

    /** Demos per kind: 0-5 after a success, 6-11 after a failure. */
    public static final int PER_KIND = 6;

    private int counter;

    /** The next interlude's demo number: 0-5 after a success, 6-11 after a failure or an abandon. */
    public int next(boolean success) {
        counter++;
        int index = counter % PER_KIND;
        return success ? index : PER_KIND + index;
    }
}
