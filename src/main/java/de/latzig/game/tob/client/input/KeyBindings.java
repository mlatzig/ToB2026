package de.latzig.game.tob.client.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jme3.input.KeyInput;

import de.latzig.game.tob.client.logic.frontend.KeyTable;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;

/**
 * The default key bindings (spec §5): the ToB 2009 {@code key.*} values of {@code RToB.properties} (identical to the
 * defaults in {@code GamePanelActionIDs.java}), decoded by {@link SjglKeys}, with the cameras moved to F5–F8 (Phase
 * 3b-2, {@link #CAMERAS}), plus the menu and developer keys. Matching is exact as in ToB 2009
 * ({@code GamePanelLogic.java:711-731}): Ctrl+Up is a program key, Up alone moves. Phase 5b: the player's remapped
 * keys ({@link #of}) replace the defaults; the {@link #FIXED} actions keep theirs.
 */
public final class KeyBindings {

    /**
     * The actions whose keys cannot be remapped (Phase 5b decision 5): Esc, Enter, F9, F10, F12. Their keys are
     * reserved: no other action can have them.
     */
    public static final Set<GameAction> FIXED = Collections.unmodifiableSet(EnumSet.of(GameAction.MENU,
            GameAction.MENU_CONFIRM, GameAction.RESTART_TOWER, GameAction.ABANDON_TOWER,
            GameAction.TOGGLE_RECORDING));

    /** ToB 2009 {@code RToB.properties} {@code key.*} values (SJGL compact keys) per action. */
    public static final Map<GameAction, Integer> TOB2009;
    /**
     * The cameras N, E, S, W on F5–F8 instead of ToB 2009's F4–F7 (Phase 3b-2 spec §2: one function-key block per
     * group, F1–F3 spiders, F5–F8 cameras; F4 is unbound). F9 restarts and F10 abandons the attempt, both after
     * ARE YOU SURE? (Phase 5a). F12 is the developer recording key (Phase 3c rework).
     */
    public static final Map<GameAction, KeyChord> CAMERAS;
    /** The pause menu keys (Phase 3b-2 spec §4.3): Esc opens and closes the menu, Enter chooses. */
    public static final Map<GameAction, KeyChord> MENU;
    /** Phase 5a decision 1: F9 restarts, F10 abandons the tower, both after ARE YOU SURE? (NO preselected). */
    public static final Map<GameAction, KeyChord> TOWER_EXIT;
    /**
     * Developer keys (spec §3 {@code InputMapper}): F12 recording (Phase 3c rework; Phase 7 must keep F12 out of the
     * release). P is the ToB 2009 pause key and part of {@link #TOB2009}. Phase 5a removed F10 textures and made F9
     * a game key ({@link #TOWER_EXIT}).
     */
    public static final Map<GameAction, KeyChord> DEVELOPER;

    static {
        Map<GameAction, Integer> tob = new EnumMap<>(GameAction.class);
        tob.put(GameAction.FIRE, 49);
        tob.put(GameAction.FORWARD, 53);
        tob.put(GameAction.BACK, 54);
        tob.put(GameAction.TURN_LEFT, 55);
        tob.put(GameAction.TURN_RIGHT, 56);
        tob.put(GameAction.PAUSE, 26);
        tob.put(GameAction.PANEL_CHANGE, 51);
        tob.put(GameAction.SELECT_ZAPPER, 37);
        tob.put(GameAction.SELECT_PUSHER, 38);
        tob.put(GameAction.SELECT_GRABBER, 39);
        tob.put(GameAction.CAMERA_NORTH, 40);
        tob.put(GameAction.CAMERA_EAST, 41);
        tob.put(GameAction.CAMERA_SOUTH, 42);
        tob.put(GameAction.CAMERA_WEST, 43);
        tob.put(GameAction.PROGRAM_FIRE, 1073);
        tob.put(GameAction.PROGRAM_UP, 1077);
        tob.put(GameAction.PROGRAM_DOWN, 1078);
        tob.put(GameAction.PROGRAM_LEFT, 1079);
        tob.put(GameAction.PROGRAM_RIGHT, 1080);
        tob.put(GameAction.PROGRAM_WRITE, 33);
        tob.put(GameAction.PROGRAM_DELETE, 63);
        tob.put(GameAction.PROGRAM_CLEAR, 13);
        tob.put(GameAction.PROGRAM_SLOT_1, 2);
        tob.put(GameAction.PROGRAM_SLOT_2, 3);
        tob.put(GameAction.PROGRAM_SLOT_3, 4);
        tob.put(GameAction.PROGRAM_SLOT_4, 5);
        tob.put(GameAction.PROGRAM_SLOT_5, 6);
        tob.put(GameAction.PROGRAM_SLOT_6, 7);
        tob.put(GameAction.PROGRAM_SLOT_7, 8);
        tob.put(GameAction.PROGRAM_SLOT_8, 9);
        tob.put(GameAction.PROGRAM_STOP, 29);
        tob.put(GameAction.PROGRAM_RUN, 28);
        tob.put(GameAction.PROGRAM_RUN_ALL, 1052);
        tob.put(GameAction.ZOOM_IN, 57);
        tob.put(GameAction.ZOOM_OUT, 58);
        TOB2009 = Collections.unmodifiableMap(tob);

        Map<GameAction, KeyChord> cameras = new EnumMap<>(GameAction.class);
        cameras.put(GameAction.CAMERA_NORTH, KeyChord.of(KeyInput.KEY_F5));
        cameras.put(GameAction.CAMERA_EAST, KeyChord.of(KeyInput.KEY_F6));
        cameras.put(GameAction.CAMERA_SOUTH, KeyChord.of(KeyInput.KEY_F7));
        cameras.put(GameAction.CAMERA_WEST, KeyChord.of(KeyInput.KEY_F8));
        CAMERAS = Collections.unmodifiableMap(cameras);

        Map<GameAction, KeyChord> menu = new EnumMap<>(GameAction.class);
        menu.put(GameAction.MENU, KeyChord.of(KeyInput.KEY_ESCAPE));
        menu.put(GameAction.MENU_CONFIRM, KeyChord.of(KeyInput.KEY_RETURN));
        MENU = Collections.unmodifiableMap(menu);

        Map<GameAction, KeyChord> exit = new EnumMap<>(GameAction.class);
        exit.put(GameAction.RESTART_TOWER, KeyChord.of(KeyInput.KEY_F9));
        exit.put(GameAction.ABANDON_TOWER, KeyChord.of(KeyInput.KEY_F10));
        TOWER_EXIT = Collections.unmodifiableMap(exit);

        Map<GameAction, KeyChord> developer = new EnumMap<>(GameAction.class);
        developer.put(GameAction.TOGGLE_RECORDING, KeyChord.of(KeyInput.KEY_F12));
        DEVELOPER = Collections.unmodifiableMap(developer);
    }

    private final Map<GameAction, KeyChord> chords;
    private final Map<KeyChord, GameAction> actions = new HashMap<>();

    private KeyBindings(Map<GameAction, KeyChord> chords) {
        this.chords = Collections.unmodifiableMap(new EnumMap<>(chords));
        chords.forEach((action, chord) -> {
            GameAction previous = actions.put(chord, action);
            if (previous != null) {
                throw new IllegalArgumentException(chord + " bound to " + previous + " and " + action);
            }
        });
    }

    /** The ToB 2009 bindings translated by key position, the cameras on F5–F8, the menu and developer keys. */
    public static KeyBindings defaults() {
        return new KeyBindings(defaultChords());
    }

    private static Map<GameAction, KeyChord> defaultChords() {
        Map<GameAction, KeyChord> chords = new EnumMap<>(GameAction.class);
        TOB2009.forEach((action, code) -> chords.put(action, SjglKeys.decode(code)));
        chords.putAll(CAMERAS);
        chords.putAll(MENU);
        chords.putAll(TOWER_EXIT);
        chords.putAll(DEVELOPER);
        return chords;
    }

    /**
     * The defaults with the settings' remapped keys (Phase 5b decision 5). An override of a {@link #FIXED} action, or
     * one that would leave a chord bound twice (a hand-edited {@code settings.json}; the Options window never writes
     * one), is logged and dropped: that action keeps its default key.
     * <p>
     * Task 8 minor (final review): the conflict resolution lives in {@link KeyTable#bindings}, shared with the
     * Options window's KEYS list ({@link #table()}), so both always agree on a hand-edited conflict.
     */
    public static KeyBindings of(Map<GameAction, KeyChord> overrides) {
        // R4 (final review re-review): log == true here only; OptionsModel's own, every-frame KeyTable.bindings
        // call (the KEYS list) must stay silent, or a hand-edited conflicting file would spam the log.
        return new KeyBindings(table().bindings(overrides, true));
    }

    /** The defaults, the fixed actions and the key names for the Options window (Phase 5b). */
    public static KeyTable table() {
        return new KeyTable(defaultChords(), FIXED, KeyNames.NAMES);
    }

    /** The action of a key press; exact match on the left Ctrl state. */
    public Optional<GameAction> lookup(int keyCode, boolean leftCtrl) {
        return Optional.ofNullable(actions.get(new KeyChord(keyCode, leftCtrl)));
    }

    public KeyChord chord(GameAction action) {
        return chords.get(action);
    }

    public Map<GameAction, KeyChord> chords() {
        return chords;
    }
}
