package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.frontend.OptionsModel.Capture;
import de.latzig.game.tob.client.logic.frontend.OptionsModel.Focus;
import de.latzig.game.tob.client.logic.frontend.OptionsModel.Option;
import de.latzig.game.tob.client.logic.frontend.OptionsModel.Result;
import de.latzig.game.tob.client.logic.frontend.OptionsModel.Tab;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.UiKey;

class OptionsModelTest {

    /** Key codes as jME's (KeyInput), written out: client.logic must not use jME. */
    static final int ESC = 1;
    static final int ENTER = 28;
    static final int SPACE = 57;
    static final int UP = 200;
    static final int F9 = 67;
    static final int F11 = 87;
    static final int Q = 16;
    static final int LCTRL = 29;

    private static final Texts TEXTS = Texts.english();

    /** A key table like {@code KeyBindings.table()}: every action on its own code, a few named keys. */
    static KeyTable table() {
        Map<GameAction, KeyChord> defaults = new EnumMap<>(GameAction.class);
        int code = 100;
        for (GameAction action : GameAction.values()) {
            defaults.put(action, KeyChord.of(code++));
        }
        defaults.put(GameAction.FIRE, KeyChord.of(SPACE));
        defaults.put(GameAction.FORWARD, KeyChord.of(UP));
        defaults.put(GameAction.PROGRAM_UP, KeyChord.ctrl(UP));
        defaults.put(GameAction.MENU, KeyChord.of(ESC));
        defaults.put(GameAction.MENU_CONFIRM, KeyChord.of(ENTER));
        defaults.put(GameAction.RESTART_TOWER, KeyChord.of(F9));
        Map<Integer, String> names = new java.util.HashMap<>();
        for (KeyChord chord : defaults.values()) {
            names.put(chord.keyCode(), "K" + chord.keyCode());
        }
        names.put(SPACE, "Space");
        names.put(UP, "Up");
        names.put(F11, "F11");
        names.put(Q, "Q");
        return new KeyTable(defaults, Set.of(GameAction.MENU, GameAction.MENU_CONFIRM, GameAction.RESTART_TOWER,
                GameAction.ABANDON_TOWER, GameAction.TOGGLE_RECORDING), names);
    }

    private static OptionsModel model(LiveSettings live) {
        OptionsModel model = new OptionsModel(live, table());
        model.open();
        return model;
    }

    private static void keys(OptionsModel model, UiKey... keys) {
        for (UiKey key : keys) {
            model.key(key);
        }
    }

    /**
     * Acceptance 2026-10-14: Back restores the live values at once, but the fading window keeps showing the values it
     * was closed with, so they do not visibly jump back while it fades out; the next opening shows the saved values.
     */
    @Test
    void theWindowKeepsShowingItsValuesWhileItFadesOutAfterBack() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS.withMasterVolume(70));
        OptionsModel model = model(live);
        live.preview(live.current().withMasterVolume(10));

        model.leave();
        live.revert();

        assertEquals(70, live.current().masterVolume(), "the live value is restored at once");
        assertEquals(10, model.edited().masterVolume(), "the fading window still shows 10");
        model.open();
        assertEquals(70, model.edited().masterVolume(), "reopened: the saved value");
    }

    @Test
    void itOpensOnSoundWithTheSavedValuesAndTheArrowsSwitchTheTabs() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS.withMasterVolume(70));
        live.preview(live.saved().withMasterVolume(10));
        OptionsModel model = model(live);

        assertEquals(70, live.current().masterVolume(), "opening drops a stale preview");
        assertEquals(Tab.SOUND, model.tab());
        assertEquals(Focus.TABS, model.focus());
        assertEquals(List.of(Option.MASTER_VOLUME, Option.BACKGROUND_VOLUME, Option.SOUND_DETAIL, Option.MUSIC),
                model.options());
        keys(model, UiKey.RIGHT);
        assertEquals(Tab.VIDEO, model.tab());
        assertEquals(7, model.options().size());
        keys(model, UiKey.RIGHT, UiKey.RIGHT, UiKey.RIGHT);
        assertEquals(Tab.CONTROLS, model.tab(), "no wrap");
        keys(model, UiKey.LEFT);
        assertEquals(List.of(Option.BACKWARDS_ALLOWED), model.options());
    }

    @Test
    void leftAndRightChangeTheFocusedValueInStepsAndPreviewItAtOnce() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        keys(model, UiKey.DOWN);
        assertEquals(Focus.ROWS, model.focus());

        keys(model, UiKey.RIGHT);
        assertEquals(110, live.current().masterVolume(), "previewed at once");
        assertEquals(100, live.saved().masterVolume(), "not saved");
        for (int i = 0; i < 20; i++) {
            model.key(UiKey.RIGHT);
        }
        assertEquals(200, live.current().masterVolume());
        assertEquals("200%", model.value(Option.MASTER_VOLUME, TEXTS));
        assertEquals(1f, model.fraction(Option.MASTER_VOLUME), 1e-6f);
        live.preview(live.current().withBrightness(55));
        model.change(Option.BRIGHTNESS, -1);
        assertEquals(50, live.current().brightness(), "off the grid: back onto it");
        model.change(Option.BRIGHTNESS, -1);
        assertEquals(50, live.current().brightness(), "the lower end");
        assertEquals(0f, model.fraction(Option.BRIGHTNESS), 1e-6f);
        model.change(Option.SOUND_DETAIL, -1);
        assertEquals("Medium", model.value(Option.SOUND_DETAIL, TEXTS));
        assertEquals(Focus.ROWS, model.focus(), "a click focuses the row");
        assertEquals(2, model.row());
    }

    /** Phase 5c-2 decision 4: the SOUND tab's last row is the Music switch, previewed at once like every option. */
    @Test
    void theMusicSwitchIsTheSoundTabsLastRowAndTogglesAtOnce() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        keys(model, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN);

        assertEquals(Focus.ROWS, model.focus());
        assertEquals(3, model.row());
        assertEquals("Music", model.label(Option.MUSIC, TEXTS));
        assertEquals("On", model.value(Option.MUSIC, TEXTS));
        keys(model, UiKey.ENTER);
        assertFalse(live.current().music(), "previewed at once");
        assertTrue(live.saved().music(), "not saved");
        assertEquals("Off", model.value(Option.MUSIC, TEXTS));
        assertEquals(0f, model.fraction(Option.MUSIC), 1e-6f);
        keys(model, UiKey.LEFT);
        assertTrue(live.current().music(), "Left toggles a switch as well");
        keys(model, UiKey.DOWN);
        assertEquals(Focus.APPLY, model.focus(), "the last row");
    }

    /** Phase 5e decision 5: the VIDEO tab's last row is the Look, Remake or Classic, previewed like every option. */
    @Test
    void theLookIsTheVideoTabsLastRow() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        model.selectTab(Tab.VIDEO);

        assertEquals(Option.LOOK, model.options().get(6), "after Panel Size");
        assertEquals("Look", model.label(Option.LOOK, TEXTS));
        assertEquals("Remake", model.value(Option.LOOK, TEXTS));
        model.change(Option.LOOK, 0);
        assertTrue(live.current().classicLook(), "previewed");
        assertFalse(live.saved().classicLook(), "not saved");
        assertEquals("Classic", model.value(Option.LOOK, TEXTS));
        assertEquals(6, model.row());
        model.change(Option.LOOK, -1);
        assertFalse(live.current().classicLook(), "Left toggles a switch as well");
    }

    @Test
    void switchesToggleAndTheDisplayModeWaitsForARestart() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        model.selectTab(Tab.VIDEO);
        keys(model, UiKey.DOWN);

        assertEquals("Fullscreen", model.value(Option.DISPLAY, TEXTS));
        assertFalse(model.restartPending());
        keys(model, UiKey.ENTER);
        assertFalse(live.current().fullscreen());
        assertTrue(model.restartPending());
        assertEquals("Window (after restart)", model.value(Option.DISPLAY, TEXTS));
        model.change(Option.PANEL_SIZE, 0);
        assertEquals("4:3", model.value(Option.PANEL_SIZE, TEXTS));
        model.change(Option.AMBIENT_EFFECTS, 1);
        assertEquals("Off", model.value(Option.AMBIENT_EFFECTS, TEXTS));
        model.change(Option.MOON_DETAIL, -1);
        assertEquals("Half", model.value(Option.MOON_DETAIL, TEXTS));
        model.selectTab(Tab.GAME);
        model.change(Option.BACKWARDS_ALLOWED, 0);
        assertFalse(live.current().backwardsAllowed());
    }

    @Test
    void theFocusWalksDownToApplyAndBackAndTabCyclesIt() {
        OptionsModel model = model(LiveSettings.inMemory(Settings.DEFAULTS));

        keys(model, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN);
        assertEquals(Focus.ROWS, model.focus());
        assertEquals(3, model.row(), "Phase 5c-2: SOUND has four rows, the last the Music switch");
        keys(model, UiKey.DOWN);
        assertEquals(Focus.APPLY, model.focus());
        keys(model, UiKey.RIGHT);
        assertEquals(Focus.BACK, model.focus());
        assertEquals(Result.BACK, model.key(UiKey.ENTER));
        keys(model, UiKey.LEFT);
        assertEquals(Result.APPLY, model.key(UiKey.SPACE));
        keys(model, UiKey.UP);
        assertEquals(Focus.ROWS, model.focus());
        keys(model, UiKey.TAB);
        assertEquals(Focus.APPLY, model.focus(), "no Reset outside KEYS");
        keys(model, UiKey.TAB, UiKey.TAB);
        assertEquals(Focus.TABS, model.focus(), "wrapped");
        assertEquals(Result.BACK, model.key(UiKey.ESCAPE), "Esc = Back");
    }

    @Test
    void theKeysTabListsEveryActionAndAFreeKeyIsTaken() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        model.selectTab(Tab.CONTROLS);
        int fire = model.actions().indexOf(GameAction.FIRE);

        assertEquals(GameAction.values().length, model.actions().size());
        assertEquals("Space", model.keyText(fire, TEXTS));
        assertTrue(model.fixed(GameAction.MENU));
        model.clickKey(fire);
        assertTrue(model.capturing());
        assertEquals("Press a key", model.keyText(fire, TEXTS));
        assertTrue(model.captured(LCTRL, true), "Ctrl alone is ignored");
        assertEquals(Capture.WAITING, model.capture());
        assertTrue(model.captured(F11, true));

        assertFalse(model.capturing());
        assertEquals(Map.of(GameAction.FIRE, KeyChord.ctrl(F11)), live.current().keys());
        assertEquals("Ctrl+F11", model.keyText(fire, TEXTS));
        model.clickKey(fire);
        model.captured(SPACE, false);
        assertEquals(Map.of(), live.current().keys(), "back to its default: no override");
        assertFalse(model.captured(Q, false), "not capturing: the key is not taken");
    }

    @Test
    void aUsedOrReservedKeyIsRefusedAndEscCancels() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        model.selectTab(Tab.CONTROLS);
        int fire = model.actions().indexOf(GameAction.FIRE);
        model.clickKey(fire);

        model.captured(UP, true);
        assertEquals(Capture.USED, model.capture(), "Ctrl+Up is PROGRAM_UP's");
        assertEquals("Ctrl+Up (already used)", model.keyText(fire, TEXTS));
        model.captured(F9, false);
        assertEquals(Capture.RESERVED, model.capture());
        assertEquals("K67 (reserved)", model.keyText(fire, TEXTS));
        model.captured(ENTER, true);
        assertEquals(Capture.RESERVED, model.capture(), "Ctrl+Enter: reserved as well");
        model.captured(ESC, false);
        assertFalse(model.capturing(), "Esc cancels");
        assertEquals(Map.of(), live.current().keys());
    }

    @Test
    void theFixedActionsCannotBeCapturedAndResetClearsEveryRemap() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS.withKeys(Map.of(GameAction.FIRE,
                KeyChord.of(Q))));
        OptionsModel model = model(live);
        model.selectTab(Tab.CONTROLS);

        model.clickKey(model.actions().indexOf(GameAction.RESTART_TOWER));
        assertFalse(model.capturing());
        assertEquals(model.actions().indexOf(GameAction.RESTART_TOWER), model.row(), "selected, not captured");
        assertEquals("Q", model.keyText(model.actions().indexOf(GameAction.FIRE), TEXTS));

        model.resetKeys();
        assertEquals(Map.of(), live.current().keys());
        assertEquals(1, live.saved().keys().size(), "only previewed");
    }

    /**
     * Author's extra (2026-10-14): the CONTROLS tab's "Hide Mouse Cursor" switch, default On, above the key list;
     * keyboard navigation includes it (Up from the list's first row reaches it, Down from it enters the list), and
     * so does a mouse click (reusing the ordinary {@link Option#HIDE_CURSOR} switch machinery).
     */
    @Test
    void theControlsTabHasAHideCursorSwitchAboveTheKeyListOnByDefault() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = model(live);
        model.selectTab(Tab.CONTROLS);

        assertEquals(List.of(Option.HIDE_CURSOR), model.options(), "the only CONTROLS option");
        assertEquals("On", model.value(Option.HIDE_CURSOR, TEXTS), "default on");
        keys(model, UiKey.DOWN);
        assertEquals(Focus.ROWS, model.focus());
        assertFalse(model.controlsListFocused(), "lands on the switch, not the key list");

        keys(model, UiKey.ENTER);
        assertFalse(live.current().hideMouseCursor(), "Enter toggles it, previewed at once");
        assertEquals("Off", model.value(Option.HIDE_CURSOR, TEXTS));
        assertFalse(model.controlsListFocused(), "toggling it keeps the focus on the switch");

        keys(model, UiKey.DOWN);
        assertTrue(model.controlsListFocused(), "Down enters the key list");
        assertEquals(0, model.row());
        keys(model, UiKey.UP);
        assertFalse(model.controlsListFocused(), "Up from the list's first row returns to the switch");
        keys(model, UiKey.UP);
        assertEquals(Focus.TABS, model.focus(), "Up from the switch reaches the tab bar");

        model.change(Option.HIDE_CURSOR, 0);
        assertTrue(live.current().hideMouseCursor(), "a mouse click (change) toggles it back on");
        assertEquals(Focus.ROWS, model.focus());
        assertFalse(model.controlsListFocused(), "a click on the switch focuses the switch, not the list");
    }

    /**
     * M4 (final review): with {@code -Dtob.volume} set, the Master row shows it is overridden and keyboard/mouse
     * changes to it have no effect (the override replaces it anyway). R2 (final review re-review): the note is in
     * the label column, not squeezed into the much narrower value column.
     */
    @Test
    void masterOverriddenShowsANoteAndIgnoresChanges() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = new OptionsModel(live, table(), true);
        model.open();

        assertTrue(model.masterOverridden());
        assertEquals("Master (-Dtob.volume)", model.label(Option.MASTER_VOLUME, TEXTS));
        assertEquals("100%", model.value(Option.MASTER_VOLUME, TEXTS), "the value column stays plain");
        model.change(Option.MASTER_VOLUME, 1);
        assertEquals(100, live.current().masterVolume(), "overridden: no change");
        model.change(Option.BACKGROUND_VOLUME, 1);
        assertEquals(110, live.current().backgroundVolume(), "other rows still work");

        OptionsModel plain = model(live);
        assertFalse(plain.masterOverridden());
        assertEquals("Master Volume", plain.label(Option.MASTER_VOLUME, TEXTS));
        assertEquals("100%", plain.value(Option.MASTER_VOLUME, TEXTS));
    }

    /** Phase 7c: without an audio device the SOUND rows are disabled, ignore changes and the tab shows a hint. */
    @Test
    void withoutAnAudioDeviceTheSoundRowsAreDisabledWithAHint() {
        LiveSettings live = LiveSettings.inMemory(Settings.DEFAULTS);
        OptionsModel model = new OptionsModel(live, table(), false, false);
        model.open();

        assertFalse(model.soundAvailable());
        assertEquals(java.util.Optional.of("No audio device found"), model.hint(TEXTS));
        for (Option option : List.of(Option.MASTER_VOLUME, Option.BACKGROUND_VOLUME, Option.SOUND_DETAIL,
                Option.MUSIC)) {
            assertFalse(model.enabled(option), option.name());
            model.change(option, 1);
        }
        assertEquals(Settings.DEFAULTS, live.current(), "no SOUND change is previewed");
        assertTrue(model.enabled(Option.BRIGHTNESS));
        model.change(Option.BRIGHTNESS, 1);
        assertTrue(live.current().brightness() > Settings.DEFAULTS.brightness(), "the other tabs still work");
        assertEquals(java.util.Optional.empty(), model.hint(TEXTS), "the hint belongs to the SOUND tab");

        OptionsModel withSound = model(live);
        assertTrue(withSound.soundAvailable());
        assertTrue(withSound.enabled(Option.MUSIC));
        assertEquals(java.util.Optional.empty(), withSound.hint(TEXTS));
    }

    /**
     * Fix wave (Task 3-4 review #5): the keyboard skips disabled rows, as the mouse cannot use them either - without
     * an audio device Down from the SOUND tab goes straight to Apply; with {@code -Dtob.volume} the Master row is
     * passed over.
     */
    @Test
    void theKeyboardSkipsDisabledRows() {
        OptionsModel silent = new OptionsModel(LiveSettings.inMemory(Settings.DEFAULTS), table(), false, false);
        silent.open();
        keys(silent, UiKey.DOWN);
        assertEquals(Focus.APPLY, silent.focus(), "no SOUND row can be used");
        keys(silent, UiKey.UP);
        assertEquals(Focus.TABS, silent.focus(), "back to the tab bar");
        keys(silent, UiKey.TAB);
        assertEquals(Focus.APPLY, silent.focus(), "Tab passes over the disabled rows as well");
        keys(silent, UiKey.TAB, UiKey.TAB, UiKey.RIGHT, UiKey.DOWN);
        assertEquals(Tab.VIDEO, silent.tab());
        assertEquals(Focus.ROWS, silent.focus(), "the other tabs' rows are reached as before");
        assertEquals(0, silent.row());

        OptionsModel overridden = new OptionsModel(LiveSettings.inMemory(Settings.DEFAULTS), table(), true);
        overridden.open();
        keys(overridden, UiKey.DOWN);
        assertEquals(Focus.ROWS, overridden.focus());
        assertEquals(Option.BACKGROUND_VOLUME, overridden.options().get(overridden.row()), "Master is passed over");
        keys(overridden, UiKey.UP);
        assertEquals(Focus.TABS, overridden.focus(), "Up from the first usable row leaves to the tab bar");
        keys(overridden, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN, UiKey.DOWN);
        assertEquals(Focus.APPLY, overridden.focus());
        keys(overridden, UiKey.UP);
        assertEquals(Option.MUSIC, overridden.options().get(overridden.row()), "back on the last usable row");
    }

    @Test
    void theKeyListScrollsWithTheSelectionAndTheWheel() {
        OptionsModel model = model(LiveSettings.inMemory(Settings.DEFAULTS));
        model.selectTab(Tab.CONTROLS);
        keys(model, UiKey.DOWN);
        assertEquals(Focus.ROWS, model.focus(), "the switch takes the first Down from the tab bar");
        assertFalse(model.controlsListFocused(), "the switch, not the key list, holds the row focus");

        // one extra Down leaves the "Hide Mouse Cursor" switch for the key list's row 0 (author's extra, 2026-10-14)
        for (int i = 0; i < 8; i++) {
            model.key(UiKey.DOWN);
        }
        assertTrue(model.controlsListFocused());
        assertEquals(7, model.row());
        assertEquals(4, model.keyList().first(), "row 7 is the last of four visible rows");
        keys(model, UiKey.END);
        assertEquals(GameAction.values().length - 1, model.row());
        keys(model, UiKey.DOWN);
        assertEquals(Focus.RESET, model.focus());
        assertEquals(Result.RESET, model.key(UiKey.ENTER));
        keys(model, UiKey.UP, UiKey.HOME);
        assertEquals(0, model.row());
        model.keyList().wheel(-1);
        assertEquals(ScrollBox.WHEEL_ROWS, model.keyList().first());
        assertEquals(OptionsModel.KEY_ROWS, model.keyList().visible());
        keys(model, UiKey.ENTER);
        assertTrue(model.capturing(), "Enter on a key row asks for a key");
    }
}
