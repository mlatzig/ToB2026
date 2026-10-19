package de.latzig.game.tob.client.logic.ui;

/**
 * A scrolled window over a list of rows (Phase 5b spec §3), shared by the KEYS tab's key list, the Play list
 * and the Hall of Fame's SCORES and CREDITS tabs (Phase 5c-1): {@link #visible} rows are shown from {@link #first},
 * which always stays inside the list. The scroll bar is a track with a thumb ({@link #thumbStart},
 * {@link #thumbLength}: fractions of the track, top = 0) as ToB 2009's GUI library built its controls from the
 * progress and inset skins. A press on the track pages towards the pointer, a press on
 * the thumb drags it with the grab point kept under the pointer; one wheel notch moves {@value #WHEEL_ROWS} rows.
 * Engine-free.
 */
public final class ScrollBox {

    /** Rows per mouse wheel notch. */
    public static final int WHEEL_ROWS = 3;

    private final int visible;
    private int size;
    private int first;
    /** While dragging: the pointer's distance below the thumb's top (track fractions); NaN otherwise. */
    private float grab = Float.NaN;

    public ScrollBox(int visible) {
        if (visible < 1) {
            throw new IllegalArgumentException("at least one visible row: " + visible);
        }
        this.visible = visible;
    }

    public int visible() {
        return visible;
    }

    public int size() {
        return size;
    }

    /** The first visible row. */
    public int first() {
        return first;
    }

    /** The list now has {@code rows} rows; the window stays inside it. */
    public void size(int rows) {
        size = Math.max(0, rows);
        scrollTo(first);
    }

    public int maxFirst() {
        return Math.max(0, size - visible);
    }

    public boolean scrollable() {
        return size > visible;
    }

    public void scrollBy(int rows) {
        scrollTo(first + rows);
    }

    public void scrollTo(int row) {
        first = Math.clamp(row, 0, maxFirst());
    }

    public void pageUp() {
        scrollBy(-visible);
    }

    public void pageDown() {
        scrollBy(visible);
    }

    public void home() {
        scrollTo(0);
    }

    public void end() {
        scrollTo(maxFirst());
    }

    /** {@code notches} of the mouse wheel; positive (away from the player) scrolls up. */
    public void wheel(int notches) {
        scrollBy(-notches * WHEEL_ROWS);
    }

    /** Scrolls just enough to show {@code row}. */
    public void show(int row) {
        if (row < first) {
            scrollTo(row);
        } else if (row >= first + visible) {
            scrollTo(row - visible + 1);
        }
    }

    /** The thumb's top as a fraction of the track. */
    public float thumbStart() {
        return scrollable() ? (float) first / size : 0f;
    }

    /** The thumb's length as a fraction of the track (1: everything visible). */
    public float thumbLength() {
        return scrollable() ? (float) visible / size : 1f;
    }

    /** A press on the track at {@code fraction} (0 = top): above or below the thumb it pages, on it a drag starts. */
    public void press(float fraction) {
        if (!scrollable()) {
            return;
        }
        if (fraction < thumbStart()) {
            pageUp();
        } else if (fraction >= thumbStart() + thumbLength()) {
            pageDown();
        } else {
            grab = fraction - thumbStart();
        }
    }

    public boolean dragging() {
        return !Float.isNaN(grab);
    }

    /** The pointer moved to {@code fraction} of the track while dragging: the thumb follows it. */
    public void drag(float fraction) {
        if (!dragging()) {
            return;
        }
        float free = 1f - thumbLength();
        // Task 9 minor (final review): the list may have shrunk below the visible rows mid-drag (thumbLength() = 1,
        // free = 0); 0f / 0f is NaN, so the guard avoids relying on Math.round(NaN) == 0 by chance.
        if (free <= 0f) {
            return;
        }
        float top = Math.clamp(fraction - grab, 0f, free);
        scrollTo(Math.round(top / free * maxFirst()));
    }

    /** The mouse button is released: the drag ends. */
    public void release() {
        grab = Float.NaN;
    }
}
