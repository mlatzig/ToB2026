package de.latzig.game.tob.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;

import de.latzig.game.tob.client.logic.frontend.KeyTable;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;

class KeyBindingsTest {

    /** The decoded ToB 2009 table by key position (the plan's key mapping table). */
    private static Map<GameAction, KeyChord> expectedToB2009() {
        Map<GameAction, KeyChord> map = new EnumMap<>(GameAction.class);
        map.put(GameAction.FIRE, KeyChord.of(KeyInput.KEY_SPACE));
        map.put(GameAction.FORWARD, KeyChord.of(KeyInput.KEY_UP));
        map.put(GameAction.BACK, KeyChord.of(KeyInput.KEY_DOWN));
        map.put(GameAction.TURN_LEFT, KeyChord.of(KeyInput.KEY_LEFT));
        map.put(GameAction.TURN_RIGHT, KeyChord.of(KeyInput.KEY_RIGHT));
        map.put(GameAction.PAUSE, KeyChord.of(KeyInput.KEY_P));
        map.put(GameAction.PANEL_CHANGE, KeyChord.of(KeyInput.KEY_TAB));
        map.put(GameAction.SELECT_ZAPPER, KeyChord.of(KeyInput.KEY_F1));
        map.put(GameAction.SELECT_PUSHER, KeyChord.of(KeyInput.KEY_F2));
        map.put(GameAction.SELECT_GRABBER, KeyChord.of(KeyInput.KEY_F3));
        map.put(GameAction.CAMERA_NORTH, KeyChord.of(KeyInput.KEY_F4));
        map.put(GameAction.CAMERA_EAST, KeyChord.of(KeyInput.KEY_F5));
        map.put(GameAction.CAMERA_SOUTH, KeyChord.of(KeyInput.KEY_F6));
        map.put(GameAction.CAMERA_WEST, KeyChord.of(KeyInput.KEY_F7));
        map.put(GameAction.PROGRAM_FIRE, KeyChord.ctrl(KeyInput.KEY_SPACE));
        map.put(GameAction.PROGRAM_UP, KeyChord.ctrl(KeyInput.KEY_UP));
        map.put(GameAction.PROGRAM_DOWN, KeyChord.ctrl(KeyInput.KEY_DOWN));
        map.put(GameAction.PROGRAM_LEFT, KeyChord.ctrl(KeyInput.KEY_LEFT));
        map.put(GameAction.PROGRAM_RIGHT, KeyChord.ctrl(KeyInput.KEY_RIGHT));
        map.put(GameAction.PROGRAM_WRITE, KeyChord.of(KeyInput.KEY_W));
        map.put(GameAction.PROGRAM_DELETE, KeyChord.of(KeyInput.KEY_BACK));
        map.put(GameAction.PROGRAM_CLEAR, KeyChord.of(KeyInput.KEY_C));
        map.put(GameAction.PROGRAM_SLOT_1, KeyChord.of(KeyInput.KEY_1));
        map.put(GameAction.PROGRAM_SLOT_2, KeyChord.of(KeyInput.KEY_2));
        map.put(GameAction.PROGRAM_SLOT_3, KeyChord.of(KeyInput.KEY_3));
        map.put(GameAction.PROGRAM_SLOT_4, KeyChord.of(KeyInput.KEY_4));
        map.put(GameAction.PROGRAM_SLOT_5, KeyChord.of(KeyInput.KEY_5));
        map.put(GameAction.PROGRAM_SLOT_6, KeyChord.of(KeyInput.KEY_6));
        map.put(GameAction.PROGRAM_SLOT_7, KeyChord.of(KeyInput.KEY_7));
        map.put(GameAction.PROGRAM_SLOT_8, KeyChord.of(KeyInput.KEY_8));
        map.put(GameAction.PROGRAM_STOP, KeyChord.of(KeyInput.KEY_S));
        map.put(GameAction.PROGRAM_RUN, KeyChord.of(KeyInput.KEY_R));
        map.put(GameAction.PROGRAM_RUN_ALL, KeyChord.ctrl(KeyInput.KEY_R));
        map.put(GameAction.ZOOM_IN, KeyChord.of(KeyInput.KEY_PGUP));
        map.put(GameAction.ZOOM_OUT, KeyChord.of(KeyInput.KEY_PGDN));
        return map;
    }

    @Test
    void everyToB2009BindingDecodesByKeyPosition() {
        Map<GameAction, KeyChord> decoded = new EnumMap<>(GameAction.class);
        KeyBindings.TOB2009.forEach((action, code) -> decoded.put(action, SjglKeys.decode(code)));

        assertEquals(expectedToB2009(), decoded);
        assertEquals(35, KeyBindings.TOB2009.size());
    }

    @Test
    void theDecoderRejectsNoKeyAndModifiersToB2009NeverUsed() {
        assertThrows(IllegalArgumentException.class, () -> SjglKeys.decode(0));
        assertThrows(IllegalArgumentException.class, () -> SjglKeys.decode(80));
        assertThrows(IllegalArgumentException.class, () -> SjglKeys.decode(SjglKeys.RIGHT_CTRL + 53));
        assertThrows(IllegalArgumentException.class, () -> SjglKeys.decode(SjglKeys.LEFT_SHIFT + 53));
        assertEquals(KeyChord.of(KeyInput.KEY_Y), SjglKeys.decode(35), "SJGL KEY_Y = the German Z key position");
    }

    @Test
    void lookupIsExactOnLeftCtrlAndEveryChordIsUnique() {
        KeyBindings bindings = KeyBindings.defaults();

        assertEquals(Optional.of(GameAction.FORWARD), bindings.lookup(KeyInput.KEY_UP, false));
        assertEquals(Optional.of(GameAction.PROGRAM_UP), bindings.lookup(KeyInput.KEY_UP, true));
        assertEquals(Optional.of(GameAction.MENU), bindings.lookup(KeyInput.KEY_ESCAPE, false));
        assertEquals(Optional.of(GameAction.MENU_CONFIRM), bindings.lookup(KeyInput.KEY_RETURN, false));
        assertEquals(Optional.empty(), bindings.lookup(KeyInput.KEY_ESCAPE, true));
        assertEquals(KeyChord.of(KeyInput.KEY_F9), bindings.chord(GameAction.RESTART_TOWER));
        assertEquals(GameAction.values().length, bindings.chords().size(), "every action has a key");
    }

    @Test
    void f12TogglesTheDeveloperRecording() {
        KeyBindings bindings = KeyBindings.defaults();

        assertEquals(Optional.of(GameAction.TOGGLE_RECORDING), bindings.lookup(KeyInput.KEY_F12, false));
        assertEquals(GameAction.Kind.DEVELOPER, GameAction.TOGGLE_RECORDING.kind());
        assertEquals(KeyChord.of(KeyInput.KEY_F12), KeyBindings.DEVELOPER.get(GameAction.TOGGLE_RECORDING));
    }

    @Test
    void f9RestartsF10AbandonsF12IsTheOnlyDeveloperKeyAndF11IsUnbound() {
        KeyBindings bindings = KeyBindings.defaults();

        assertEquals(Optional.of(GameAction.RESTART_TOWER), bindings.lookup(KeyInput.KEY_F9, false));
        assertEquals(Optional.of(GameAction.ABANDON_TOWER), bindings.lookup(KeyInput.KEY_F10, false),
                "Phase 5a decision 1");
        assertEquals(GameAction.Kind.CLIENT, GameAction.RESTART_TOWER.kind());
        assertEquals(GameAction.Kind.CLIENT, GameAction.ABANDON_TOWER.kind());
        assertEquals(Optional.empty(), bindings.lookup(KeyInput.KEY_F11, false),
                "acceptance 2026-10-10: the colour pattern toggle is removed");
        assertEquals(1, KeyBindings.DEVELOPER.size());
        assertEquals(2, KeyBindings.TOWER_EXIT.size());
    }

    @Test
    void theCamerasMoveToF5ToF8AndF4IsUnbound() {
        KeyBindings bindings = KeyBindings.defaults();

        assertEquals(KeyChord.of(KeyInput.KEY_F5), bindings.chord(GameAction.CAMERA_NORTH));
        assertEquals(KeyChord.of(KeyInput.KEY_F6), bindings.chord(GameAction.CAMERA_EAST));
        assertEquals(KeyChord.of(KeyInput.KEY_F7), bindings.chord(GameAction.CAMERA_SOUTH));
        assertEquals(KeyChord.of(KeyInput.KEY_F8), bindings.chord(GameAction.CAMERA_WEST));
        assertEquals(Optional.empty(), bindings.lookup(KeyInput.KEY_F4, false), "F4 is unbound");
        assertEquals(Optional.of(GameAction.SELECT_GRABBER), bindings.lookup(KeyInput.KEY_F3, false));
        assertEquals(Optional.of(GameAction.RESTART_TOWER), bindings.lookup(KeyInput.KEY_F9, false));
    }

    /** Phase 5b decision 5: the settings' overrides replace the defaults; the fixed actions keep their keys. */
    @Test
    void remappedKeysReplaceTheDefaultsAndTheFixedKeysStay() {
        KeyBindings bindings = KeyBindings.of(Map.of(GameAction.FIRE, KeyChord.of(KeyInput.KEY_F11),
                GameAction.FORWARD, KeyChord.of(KeyInput.KEY_DOWN), GameAction.BACK, KeyChord.of(KeyInput.KEY_UP),
                GameAction.MENU, KeyChord.of(KeyInput.KEY_Q)));

        assertEquals(Optional.of(GameAction.FIRE), bindings.lookup(KeyInput.KEY_F11, false));
        assertEquals(Optional.empty(), bindings.lookup(KeyInput.KEY_SPACE, false), "Space is free now");
        assertEquals(Optional.of(GameAction.FORWARD), bindings.lookup(KeyInput.KEY_DOWN, false), "a swap is fine");
        assertEquals(Optional.of(GameAction.BACK), bindings.lookup(KeyInput.KEY_UP, false));
        assertEquals(Optional.of(GameAction.MENU), bindings.lookup(KeyInput.KEY_ESCAPE, false), "Esc is fixed");
        assertEquals(Optional.empty(), bindings.lookup(KeyInput.KEY_Q, false));
        assertEquals(KeyBindings.defaults().chords(), KeyBindings.of(Map.of()).chords());
    }

    /** A hand-edited settings.json must never stop the game: a chord bound twice falls back to the defaults. */
    @Test
    void anOverrideThatWouldBindAKeyTwiceFallsBackToTheDefault() {
        KeyBindings bindings = KeyBindings.of(Map.of(GameAction.FIRE, KeyChord.of(KeyInput.KEY_UP),
                GameAction.PAUSE, KeyChord.of(KeyInput.KEY_F9)));

        assertEquals(KeyChord.of(KeyInput.KEY_SPACE), bindings.chord(GameAction.FIRE), "Up is Forward's");
        assertEquals(KeyChord.of(KeyInput.KEY_P), bindings.chord(GameAction.PAUSE), "F9 is fixed");
        assertEquals(Optional.of(GameAction.FORWARD), bindings.lookup(KeyInput.KEY_UP, false));
    }

    /**
     * Task 8 minor (final review): the KEYS list ({@code KeyTable.bindings}) and the game's actual bindings
     * ({@code KeyBindings.of}) must agree on a hand-edited "bound twice" conflict, so the window never shows a key
     * it cannot actually give the action.
     */
    @Test
    void theKeyTableAgreesWithKeyBindingsOfOnABoundTwiceConflict() {
        Map<GameAction, KeyChord> overrides = Map.of(GameAction.FIRE, KeyChord.of(KeyInput.KEY_UP),
                GameAction.PAUSE, KeyChord.of(KeyInput.KEY_F9));
        KeyTable table = KeyBindings.table();

        assertEquals(KeyBindings.of(overrides).chords(), table.bindings(overrides));
    }

    @Test
    void theKeyTableNamesTheKeysAndKnowsTheFixedOnes() {
        KeyTable table = KeyBindings.table();

        assertEquals("Space", table.name(KeyChord.of(KeyInput.KEY_SPACE)));
        assertEquals("Ctrl+Up", table.name(KeyChord.ctrl(KeyInput.KEY_UP)));
        assertEquals("Page Down", table.name(KeyChord.of(KeyInput.KEY_PGDN)));
        assertEquals("Y", table.name(KeyChord.of(KeyInput.KEY_Y)), "positional: the German Z key");
        assertEquals("Key 255", table.name(KeyChord.of(255)));
        assertEquals(KeyBindings.FIXED, table.fixed());
        for (int reserved : new int[] {KeyInput.KEY_ESCAPE, KeyInput.KEY_RETURN, KeyInput.KEY_F9, KeyInput.KEY_F10,
                KeyInput.KEY_F12}) {
            assertTrue(table.reserved(reserved), "reserved " + reserved);
        }
        assertFalse(table.reserved(KeyInput.KEY_F11));
        assertFalse(table.nameable(KeyInput.KEY_LCONTROL), "a modifier alone is no key");
        for (KeyChord chord : table.defaults().values()) {
            assertTrue(table.nameable(chord.keyCode()), "every default key has a name: " + chord);
        }
        assertEquals(KeyBindings.defaults().chords(), table.bindings(Map.of()));
        assertEquals(KeyChord.of(KeyInput.KEY_F11),
                table.bindings(Map.of(GameAction.FIRE, KeyChord.of(KeyInput.KEY_F11))).get(GameAction.FIRE));
    }

    @Test
    void theRawListenerReportsTheReleaseOfThePressedActionButNoOsRepeat() {
        List<String> fired = new ArrayList<>();
        KeyboardInput input = new KeyboardInput(KeyBindings.defaults(), action -> fired.add("+" + action),
                action -> fired.add("-" + action));

        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));
        // the OS auto-repeat of the held key: pressed=false, repeating=true, twice
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_Q, 'q', false, false));

        assertEquals(List.of("+FORWARD", "-FORWARD", "+PROGRAM_UP", "-PROGRAM_UP"), fired,
                "a key releases the action it was pressed as; unbound keys release nothing");
    }

    @Test
    void theRawListenerFiresOncePerPressAndTracksLeftCtrl() {
        List<GameAction> fired = new ArrayList<>();
        KeyboardInput input = new KeyboardInput(KeyBindings.defaults(), fired::add);

        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, false));
        KeyInputEvent unbound = new KeyInputEvent(KeyInput.KEY_Q, 'q', true, false);
        input.onKeyEvent(unbound);
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_SPACE, ' ', true, false));

        assertEquals(List.of(GameAction.FORWARD, GameAction.PROGRAM_UP, GameAction.FIRE), fired);
        assertTrue(!unbound.isConsumed(), "unbound keys stay free for the GUI");
    }

    /**
     * Ctrl repeat finding (review, must-fix before 3b): jME/GLFW auto-repeat delivers a held key as
     * {@code pressed=false, repeating=true} ({@code javap} on {@code GlfwKeyInput$1}). Before the fix,
     * {@code KeyboardInput} applied that to left Ctrl like any other event, so holding Ctrl past the OS repeat
     * delay reset {@code leftCtrl} to false and the program keys stopped firing.
     */
    @Test
    void aKeyRepeatEventForLeftCtrlDoesNotResetTheModifier() {
        List<GameAction> fired = new ArrayList<>();
        KeyboardInput input = new KeyboardInput(KeyBindings.defaults(), fired::add);

        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        // the OS auto-repeat for the still-held left Ctrl: pressed=false, repeating=true.
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));

        assertEquals(List.of(GameAction.PROGRAM_UP), fired,
                "left Ctrl must still read as held after its own repeat event");
    }

    @Test
    void aGenuineLeftCtrlReleaseStillClearsTheModifier() {
        List<GameAction> fired = new ArrayList<>();
        KeyboardInput input = new KeyboardInput(KeyBindings.defaults(), fired::add);

        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, true));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, false));
        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UP, '\0', true, false));

        assertEquals(List.of(GameAction.FORWARD), fired, "a real release (non-repeating) still clears leftCtrl");
    }
}
