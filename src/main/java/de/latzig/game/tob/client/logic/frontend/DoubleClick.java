package de.latzig.game.tob.client.logic.frontend;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;

/**
 * Tells a double click from two clicks on the start screens (author decision, 2026-10-12: a double click on an
 * Overview cell starts the tower). Every click is reported at once as a click (never delayed); a second click on the
 * same {@link Hit} within {@link #INTERVAL} seconds and {@link #DISTANCE} pixels per axis of the first is reported as
 * a double click instead ({@link GameFlow#doubleClick} includes the click). A third click starts a new pair, a click
 * beside every widget breaks the pair. The values are Windows' defaults ({@code GetDoubleClickTime} 500 ms, the
 * {@code SM_CXDOUBLECLK} box of 4 pixels); the OS setting itself is not read (GLFW does not report it).
 */
public final class DoubleClick {

    /** The longest time between the two clicks, in seconds. */
    public static final double INTERVAL = 0.5;
    /** The farthest the second click may be from the first, in pixels per axis. */
    public static final float DISTANCE = 4f;

    private Hit first;
    private float firstX;
    private float firstY;
    private double firstSeconds;

    /**
     * A left click at ({@code x}, {@code y}) at {@code seconds} (any monotonic clock) over {@code hit}: reported to
     * {@code single} or, if it completes a pair, to {@code pair}.
     */
    public void click(Optional<Hit> hit, float x, float y, double seconds, Consumer<Hit> single, Consumer<Hit> pair) {
        Objects.requireNonNull(single, "single");
        Objects.requireNonNull(pair, "pair");
        if (hit.isEmpty()) {
            reset();
            return;
        }
        Hit h = hit.get();
        if (h.equals(first) && seconds - firstSeconds <= INTERVAL && Math.abs(x - firstX) <= DISTANCE
                && Math.abs(y - firstY) <= DISTANCE) {
            reset();
            pair.accept(h);
            return;
        }
        first = h;
        firstX = x;
        firstY = y;
        firstSeconds = seconds;
        single.accept(h);
    }

    /** {@link #click} into the flow: {@link GameFlow#click} or {@link GameFlow#doubleClick}. */
    public void click(GameFlow flow, Optional<Hit> hit, float x, float y, double seconds) {
        click(hit, x, y, seconds, flow::click, flow::doubleClick);
    }

    /** Forgets the first click (the screens are left or shown again). */
    public void reset() {
        first = null;
    }
}
