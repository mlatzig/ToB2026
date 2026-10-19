package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The game's (GameState) mouse cursor auto-hide (author's extra, 2026-10-14): engine-free, so it is tested on its
 * own; {@link de.latzig.game.tob.client.GameState} feeds it every render frame of active play and applies
 * {@link CursorAutoHide#visible()} to the software cursor.
 */
class CursorAutoHideTest {

    @Test
    void itHidesAfterThreeIdleSecondsOfActivePlay() {
        CursorAutoHide cursor = new CursorAutoHide();

        cursor.update(2.9, false, true);
        assertTrue(cursor.visible(), "not idle long enough yet");
        cursor.update(0.1, false, true);
        assertFalse(cursor.visible(), "3 s without movement");
    }

    @Test
    void anyMovementOrClickShowsItAgainAtOnceAndRestartsTheTimer() {
        CursorAutoHide cursor = new CursorAutoHide();
        cursor.update(3.0, false, true);
        assertFalse(cursor.visible());

        cursor.update(0.0, true, true);
        assertTrue(cursor.visible(), "a movement or a click shows it at once");

        cursor.update(2.9, false, true);
        assertTrue(cursor.visible(), "the idle timer restarted");
        cursor.update(0.1, false, true);
        assertFalse(cursor.visible(), "3 s after the movement, not after the original hide");
    }

    @Test
    void itNeverHidesWhileTheOptionIsOff() {
        CursorAutoHide cursor = new CursorAutoHide();

        cursor.update(30.0, false, false);

        assertTrue(cursor.visible(), "the option is off: no hiding, ever");
    }

    @Test
    void turningTheOptionBackOnStartsAFreshIdleTimer() {
        CursorAutoHide cursor = new CursorAutoHide();
        cursor.update(30.0, false, false);

        cursor.update(2.9, false, true);
        assertTrue(cursor.visible(), "freshly on: not idle long enough yet");
        cursor.update(0.1, false, true);
        assertFalse(cursor.visible());
    }

    /**
     * Author's clarification (2026-10-14): the cursor hides only during active play; paused with the menu open (or
     * its ARE YOU SURE?/QUIT? dialogs), on the end screen, or at the result window it always stays visible.
     * {@link de.latzig.game.tob.client.GameState} enforces this by calling {@link CursorAutoHide#reset()} instead
     * of {@link CursorAutoHide#update} whenever play is not active, which is exactly what this test exercises
     * directly on the engine-free timer.
     */
    @Test
    void pausedWithTheMenuOpenKeepsTheCursorVisible() {
        CursorAutoHide cursor = new CursorAutoHide();
        cursor.update(3.0, false, true);
        assertFalse(cursor.visible(), "hidden during active play");

        cursor.reset();

        assertTrue(cursor.visible(), "paused with the menu open: visible again at once");
        // Paused, GameState no longer calls update(): the cursor stays visible for as long as the menu is open,
        // however long that takes, unlike active play where idle time alone would hide it again.
        assertTrue(cursor.visible(), "still visible: nothing fed the timer while paused");
    }

    @Test
    void resetShowsItAgainAndClearsTheIdleTime() {
        CursorAutoHide cursor = new CursorAutoHide();
        cursor.update(3.0, false, true);
        assertFalse(cursor.visible());

        cursor.reset();
        assertTrue(cursor.visible());

        // the idle time was cleared, not just the visibility: less than 3 s after the reset it is still visible
        cursor.update(2.9, false, true);
        assertTrue(cursor.visible());
    }
}
