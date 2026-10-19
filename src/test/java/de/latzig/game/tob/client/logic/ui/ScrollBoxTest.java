package de.latzig.game.tob.client.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollBoxTest {

    private static ScrollBox box(int visible, int size) {
        ScrollBox box = new ScrollBox(visible);
        box.size(size);
        return box;
    }

    @Test
    void scrollingStopsAtBothEnds() {
        ScrollBox box = box(5, 40);

        assertEquals(35, box.maxFirst());
        box.scrollBy(-1);
        assertEquals(0, box.first(), "the top");
        box.scrollBy(100);
        assertEquals(35, box.first(), "the bottom");
        box.pageUp();
        assertEquals(30, box.first());
        box.home();
        assertEquals(0, box.first());
        box.pageDown();
        assertEquals(5, box.first());
        box.end();
        assertEquals(35, box.first());
        box.size(10);
        assertEquals(5, box.first(), "a shorter list keeps the window inside it");
        assertThrows(IllegalArgumentException.class, () -> new ScrollBox(0));
    }

    @Test
    void theWheelMovesThreeRowsPerNotchAndAwayFromThePlayerIsUp() {
        ScrollBox box = box(5, 40);

        box.wheel(-2);
        assertEquals(2 * ScrollBox.WHEEL_ROWS, box.first(), "towards the player: down");
        box.wheel(1);
        assertEquals(ScrollBox.WHEEL_ROWS, box.first());
        box.wheel(10);
        assertEquals(0, box.first());
    }

    @Test
    void showingARowScrollsJustEnough() {
        ScrollBox box = box(5, 40);

        box.show(7);
        assertEquals(3, box.first(), "row 7 is the last visible one");
        box.show(5);
        assertEquals(3, box.first(), "already visible: no scrolling");
        box.show(1);
        assertEquals(1, box.first());
        box.show(39);
        assertEquals(35, box.first());
    }

    @Test
    void theThumbShowsTheVisiblePartOfTheList() {
        ScrollBox box = box(10, 40);

        assertEquals(0f, box.thumbStart(), 0f);
        assertEquals(0.25f, box.thumbLength(), 1e-6f);
        box.end();
        assertEquals(0.75f, box.thumbStart(), 1e-6f);

        ScrollBox all = box(10, 4);
        assertFalse(all.scrollable());
        assertEquals(0f, all.thumbStart(), 0f);
        assertEquals(1f, all.thumbLength(), 0f, "everything visible: the thumb fills the track");
    }

    @Test
    void pressingTheTrackPagesAndDraggingTheThumbFollowsThePointer() {
        ScrollBox box = box(10, 40);

        box.press(0.9f);
        assertEquals(10, box.first(), "below the thumb: a page down");
        assertFalse(box.dragging());
        box.press(0.1f);
        assertEquals(0, box.first(), "above the thumb: a page up");

        box.press(0.1f);
        assertTrue(box.dragging(), "on the thumb (0 .. 0.25): a drag starts, grabbed 0.1 below its top");
        box.drag(0.475f);
        assertEquals(15, box.first(), "thumb top at 0.375 of the free 0.75: half way, row 15 of 30");
        box.drag(2f);
        assertEquals(30, box.first(), "clamped at the bottom");
        box.drag(-1f);
        assertEquals(0, box.first());
        box.release();
        assertFalse(box.dragging());
        box.drag(0.9f);
        assertEquals(0, box.first(), "no drag after the release");
    }

    /** Task 9 minor (final review): the list shrinks below the visible rows while a drag is in progress. */
    @Test
    void dragDoesNotFailWhenTheListShrinksBelowTheVisibleRowsMidDrag() {
        ScrollBox box = box(10, 40);
        box.press(0.1f);
        assertTrue(box.dragging());

        box.size(4);
        box.drag(0.9f);

        assertEquals(0, box.first(), "everything fits now: no scrolling, and no NaN/exception");
        assertFalse(box.scrollable());
    }

    @Test
    void aListThatFitsDoesNotScroll() {
        ScrollBox box = box(10, 4);

        box.scrollBy(3);
        box.wheel(-1);
        box.press(0.5f);
        box.drag(0.9f);

        assertEquals(0, box.first());
        assertFalse(box.dragging());
    }
}
