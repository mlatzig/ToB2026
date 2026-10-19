package de.latzig.game.tob.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;

import de.latzig.game.tob.client.logic.ui.UiKey;

class FrontEndInputTest {

    private final List<String> events = new ArrayList<>();
    private final FrontEndInput input = new FrontEndInput(new FrontEndInput.Listener() {
        @Override
        public void key(UiKey key) {
            events.add(key.name());
        }

        @Override
        public void typed(char c) {
            events.add("'" + c + "'");
        }

        @Override
        public void moved(float x, float y) {
            events.add("moved " + (int) x + "," + (int) y);
        }

        @Override
        public void clicked(float x, float y) {
            events.add("clicked " + (int) x + "," + (int) y);
        }
    });

    @Test
    void keyCodesBecomeUiKeysAndOnlyPrintableAsciiCharactersBecomeTypedText() {
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_Z, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, 'y', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, 'y', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, 'ü', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, 'ß', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, '\u007F', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, ' ', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, '~', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_RETURN, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_RETURN, '\0', false, false));

        assertEquals(List.of("'y'", "' '", "'~'", "ENTER"), events,
                "the Z key of a QWERTZ keyboard types y; umlauts, sharp s and DEL never reach the flow");
    }

    @Test
    void navigationRepeatsWhileHeldButEnterAndEscDoNot() {
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_DOWN, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_RETURN, '\0', false, true));

        assertEquals(List.of("DOWN", "BACKSPACE"), events);
        assertEquals(Optional.of(UiKey.ENTER), FrontEndInput.uiKey(KeyInput.KEY_NUMPADENTER));
        assertEquals(Optional.empty(), FrontEndInput.uiKey(KeyInput.KEY_F1));
    }

    /**
     * Phase 5b decision 5: every key press is offered raw first, with the left Ctrl state; a taken one becomes no
     * menu key. The OS repeat and Ctrl itself are not offered.
     */
    @Test
    void rawKeyPressesWithLeftCtrlComeFirstAndATakenOneIsNoMenuKey() {
        List<String> seen = new ArrayList<>();
        FrontEndInput capture = new FrontEndInput(new FrontEndInput.Listener() {
            @Override
            public boolean pressed(int keyCode, boolean leftCtrl) {
                seen.add("raw " + keyCode + (leftCtrl ? " ctrl" : ""));
                return keyCode == KeyInput.KEY_TAB;
            }

            @Override
            public void key(UiKey key) {
                seen.add(key.name());
            }

            @Override
            public void typed(char c) {
            }

            @Override
            public void moved(float x, float y) {
            }

            @Override
            public void clicked(float x, float y) {
            }
        });

        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, true));
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_TAB, '\0', true, false));
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, false));
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_PGDN, '\0', true, false));
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_PGDN, '\0', false, true));

        assertEquals(List.of("raw " + KeyInput.KEY_TAB + " ctrl", "raw " + KeyInput.KEY_PGDN, "PAGE_DOWN",
                "PAGE_DOWN"), seen, "Ctrl+Tab taken; Page Down a menu key, repeated while held");
        assertEquals(Optional.of(UiKey.PAGE_UP), FrontEndInput.uiKey(KeyInput.KEY_PGUP));
    }

    /**
     * M2 (final review): a stale left-Ctrl state must not leak into a later key capture across a detach, e.g. Ctrl
     * held while the game state takes over and released there; back in the Options window the next raw key must
     * see Ctrl as not held.
     */
    @Test
    void resetClearsAStaleLeftCtrlState() {
        List<String> seen = new ArrayList<>();
        FrontEndInput capture = new FrontEndInput(new FrontEndInput.Listener() {
            @Override
            public boolean pressed(int keyCode, boolean leftCtrl) {
                seen.add("raw " + keyCode + (leftCtrl ? " ctrl" : ""));
                return true;
            }

            @Override
            public void key(UiKey key) {
            }

            @Override
            public void typed(char c) {
            }

            @Override
            public void moved(float x, float y) {
            }

            @Override
            public void clicked(float x, float y) {
            }
        });

        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        capture.reset();
        capture.onKeyEvent(new KeyInputEvent(KeyInput.KEY_TAB, '\0', true, false));

        assertEquals(List.of("raw " + KeyInput.KEY_TAB), seen, "reset() must clear the left-Ctrl state");
    }

    @Test
    void theWheelReportsNotchesAndTheReleaseIsReported() {
        List<String> seen = new ArrayList<>();
        FrontEndInput mouse = new FrontEndInput(new FrontEndInput.Listener() {
            @Override
            public void key(UiKey key) {
            }

            @Override
            public void typed(char c) {
            }

            @Override
            public void moved(float x, float y) {
                seen.add("moved");
            }

            @Override
            public void clicked(float x, float y) {
                seen.add("clicked");
            }

            @Override
            public void wheel(int notches) {
                seen.add("wheel " + notches);
            }

            @Override
            public void released(float x, float y) {
                seen.add("released " + (int) x + "," + (int) y);
            }
        });

        mouse.onMouseMotionEvent(new MouseMotionEvent(5, 6, 0, 0, 120, 120));
        mouse.onMouseMotionEvent(new MouseMotionEvent(5, 6, 0, 0, -240, -360));
        mouse.onMouseButtonEvent(new MouseButtonEvent(com.jme3.input.MouseInput.BUTTON_LEFT, true, 7, 8));
        mouse.onMouseButtonEvent(new MouseButtonEvent(com.jme3.input.MouseInput.BUTTON_LEFT, false, 9, 10));

        assertEquals(List.of("wheel 1", "moved", "wheel -3", "moved", "clicked", "released 9,10"), seen);
        assertEquals(1, FrontEndInput.notches(40), "a small delta is still a notch");
        assertEquals(0, FrontEndInput.notches(0));
    }

    @Test
    void theMouseReportsMotionAndLeftClicks() {
        input.onMouseMotionEvent(new MouseMotionEvent(10, 20, 1, 1, 0, 0));
        input.onMouseButtonEvent(new MouseButtonEvent(com.jme3.input.MouseInput.BUTTON_LEFT, true, 12, 22));
        input.onMouseButtonEvent(new MouseButtonEvent(com.jme3.input.MouseInput.BUTTON_LEFT, false, 12, 22));
        input.onMouseButtonEvent(new MouseButtonEvent(com.jme3.input.MouseInput.BUTTON_RIGHT, true, 12, 22));

        assertEquals(List.of("moved 10,20", "clicked 12,22"), events);
        assertEquals(12f, input.x());
    }
}
