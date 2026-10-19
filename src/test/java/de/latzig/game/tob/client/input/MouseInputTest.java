package de.latzig.game.tob.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;

class MouseInputTest {

    private static final int LEFT = com.jme3.input.MouseInput.BUTTON_LEFT;
    private static final int RIGHT = com.jme3.input.MouseInput.BUTTON_RIGHT;

    @Test
    void theLeftButtonReportsPressAndReleaseWithThePositionAndOtherButtonsAreIgnored() {
        List<String> events = new ArrayList<>();
        MouseInput mouse = new MouseInput(new MouseInput.Listener() {
            @Override
            public void leftPressed(float x, float y) {
                events.add("down " + (int) x + "," + (int) y);
            }

            @Override
            public void leftReleased(float x, float y) {
                events.add("up " + (int) x + "," + (int) y);
            }
        });

        mouse.onMouseMotionEvent(new MouseMotionEvent(100, 50, 3, 4, 0, 0));
        assertEquals(100f, mouse.x());
        assertEquals(50f, mouse.y());
        mouse.onMouseButtonEvent(new MouseButtonEvent(RIGHT, true, 100, 50));
        MouseButtonEvent press = new MouseButtonEvent(LEFT, true, 101, 52);
        mouse.onMouseButtonEvent(press);
        assertTrue(mouse.isLeftDown());
        mouse.onMouseButtonEvent(new MouseButtonEvent(LEFT, true, 101, 52));
        mouse.onMouseMotionEvent(new MouseMotionEvent(120, 60, 19, 8, 0, 0));
        mouse.onMouseButtonEvent(new MouseButtonEvent(LEFT, false, 120, 60));
        mouse.onMouseButtonEvent(new MouseButtonEvent(RIGHT, false, 120, 60));

        assertEquals(List.of("down 101,52", "up 120,60"), events, "a second press without release is ignored");
        assertFalse(mouse.isLeftDown());
        assertTrue(press.isConsumed());
    }

    @Test
    void theHiddenCursorIsOneFullyTransparentImage() {
        JmeCursor cursor = MouseInput.hiddenCursor();

        assertEquals(1, cursor.getNumImages());
        assertEquals(4, cursor.getImagesData().capacity());
        for (int i = 0; i < 4; i++) {
            assertEquals(0, cursor.getImagesData().get(i), "ARGB 0: fully transparent");
        }
    }

    /**
     * Task 9 review (addition A): jME 3.9's GLFW mouse input keeps a native cursor per instance for the
     * application's lifetime, so a fresh {@link JmeCursor} on every call (every {@code GameState.onEnable}) would
     * leak one per tower start; the method must create it once and reuse it.
     */
    @Test
    void theHiddenCursorIsCreatedOnceAndReused() {
        assertTrue(MouseInput.hiddenCursor() == MouseInput.hiddenCursor(), "the same instance every time");
    }

    /**
     * Task 9 review (addition B): a release missed while this listener is detached (e.g. {@code
     * GameState.onDisable}) must not leave the left button looking held once a fresh listener is attached.
     */
    @Test
    void resetHeldDropsAHeldLeftButtonWithoutFiringARelease() {
        List<String> events = new ArrayList<>();
        MouseInput mouse = new MouseInput(new MouseInput.Listener() {
            @Override
            public void leftPressed(float x, float y) {
                events.add("down");
            }

            @Override
            public void leftReleased(float x, float y) {
                events.add("up");
            }
        });
        mouse.onMouseButtonEvent(new MouseButtonEvent(LEFT, true, 10, 20));
        assertTrue(mouse.isLeftDown());

        mouse.resetHeld();

        assertFalse(mouse.isLeftDown());
        assertEquals(List.of("down"), events, "no release is fired by the reset");
        mouse.onMouseButtonEvent(new MouseButtonEvent(LEFT, true, 11, 21));
        assertEquals(List.of("down", "down"), events, "the next real press is not swallowed");
    }

    /**
     * Final review M3: {@code GameState.onEnable} seeds the position from {@code InputManager.getCursorPosition()}
     * so the software cursor is not drawn off-window (invisible) until the first real motion event.
     */
    @Test
    void seedPositionSetsXAndYWithoutFiringAPressOrARelease() {
        List<String> events = new ArrayList<>();
        MouseInput mouse = new MouseInput(new MouseInput.Listener() {
            @Override
            public void leftPressed(float x, float y) {
                events.add("down");
            }

            @Override
            public void leftReleased(float x, float y) {
                events.add("up");
            }
        });

        mouse.seedPosition(640f, 360f);

        assertEquals(640f, mouse.x());
        assertEquals(360f, mouse.y());
        assertEquals(List.of(), events, "no press or release is fired by the seed");
        assertFalse(mouse.isLeftDown());
    }
}
