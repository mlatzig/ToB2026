package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.game.PanelSize;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.UiKey;

/**
 * The Options window's state (Phase 5b spec decisions 1–6), engine-free: four tabs in ToB 2005's model —
 * {@link Tab#SOUND}, {@link Tab#VIDEO}, {@link Tab#GAME}, {@link Tab#CONTROLS} — the keyboard focus, and the edited
 * options, which go straight into {@link LiveSettings#preview} so that every change shows (and sounds) at once;
 * Apply and Back are the flow's ({@link #key} reports them). A value is a percent (−/+ around a bar, steps of 10), a
 * choice (−/+ around a text) or a switch (one button showing its state).
 * <p>
 * Keyboard (as the other start screens): Tab cycles tab bar → rows → (Reset) → Apply → Back; on the tab bar
 * Left/Right switch the tab and Down enters the rows; on a row Up/Down move, Left/Right change the value, Enter or
 * Space toggle a switch or step a choice; on the KEYS list Page Up/Down, Home and End move the selection and Enter
 * asks for a key; Esc is Back.
 * <p>
 * Key capture (decision 5): "Press a key"; the next key, with left Ctrl as the only modifier, becomes the action's
 * key. A key bound to another action is refused ({@link Capture#USED}, "(already used)"), so is a fixed one
 * (Esc, Enter, F9, F10, F12: {@link Capture#RESERVED}, "(reserved)"); both keep waiting. Esc cancels; a key without a
 * name (a modifier alone) is ignored. The fixed actions are listed but cannot be captured. A key equal to the
 * action's default removes its override.
 * <p>
 * Author's extra (2026-10-14): the former KEYS tab is now CONTROLS, with the "Hide Mouse Cursor" switch
 * ({@link Option#HIDE_CURSOR}) above the key list; {@link #controlsListFocused()} tells the key list from the
 * switch for keyboard navigation and drawing. Up from the list's first row reaches the switch, not the tab bar; Up
 * from the switch reaches the tab bar; Down from the switch enters the list. The switch is otherwise an ordinary
 * {@link Kind#SWITCH} option, applied live by {@link #change}.
 * <p>
 * Phase 5c-2 decision 4: the SOUND tab's last row is the Music switch ({@link Option#MUSIC}), previewed at once like
 * every option, so the menu music stops or starts while the window is open; Back restores it.
 * <p>
 * Phase 5e decision 5: the VIDEO tab's last row is the Look switch ({@link Option#LOOK}, Remake or Classic); it is
 * previewed like every option. A running tower (where Options cannot be open) and the designer's preview read it
 * only when they are built (a tower start, entering the designer); the menu background rebuilds itself at once
 * while the menus are shown, so Apply (and a later Back/Esc) takes effect right away without a game start (author's
 * acceptance tweak, 2026-10-16; {@code MenuBackgroundState.lookChanged()}).
 */
public final class OptionsModel {

    /** The four tabs, left to right. */
    public enum Tab {
        SOUND, VIDEO, GAME, CONTROLS;

        public String textKey() {
            return "options.tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a value is shown and changed. */
    public enum Kind {
        PERCENT, CHOICE, SWITCH
    }

    /** Every option, in its tab's order. */
    public enum Option {
        MASTER_VOLUME(Tab.SOUND, Kind.PERCENT, "options.master"),
        BACKGROUND_VOLUME(Tab.SOUND, Kind.PERCENT, "options.background"),
        SOUND_DETAIL(Tab.SOUND, Kind.CHOICE, "options.detail"),
        /** Phase 5c-2 decision 4: the menu music on or off, default On ({@link Settings#DEFAULTS}). */
        MUSIC(Tab.SOUND, Kind.SWITCH, "options.music"),
        DISPLAY(Tab.VIDEO, Kind.SWITCH, "options.display"),
        BRIGHTNESS(Tab.VIDEO, Kind.PERCENT, "options.brightness"),
        MOON_DETAIL(Tab.VIDEO, Kind.SWITCH, "options.moon"),
        AMBIENT_EFFECTS(Tab.VIDEO, Kind.SWITCH, "options.effects"),
        FLASH_STRENGTH(Tab.VIDEO, Kind.PERCENT, "options.flash"),
        PANEL_SIZE(Tab.VIDEO, Kind.SWITCH, "options.panel"),
        /** Phase 5e decision 5: Remake (default) or Classic, applied at the next scene build. */
        LOOK(Tab.VIDEO, Kind.SWITCH, "options.look"),
        BACKWARDS_ALLOWED(Tab.GAME, Kind.SWITCH, "options.backwards"),
        /** Author's extra (2026-10-14): above the CONTROLS tab's key list, default On ({@link Settings#DEFAULTS}). */
        HIDE_CURSOR(Tab.CONTROLS, Kind.SWITCH, "options.cursor.hide");

        private final Tab tab;
        private final Kind kind;
        private final String textKey;

        Option(Tab tab, Kind kind, String textKey) {
            this.tab = tab;
            this.kind = kind;
            this.textKey = textKey;
        }

        public Tab tab() {
            return tab;
        }

        public Kind kind() {
            return kind;
        }

        public String textKey() {
            return textKey;
        }
    }

    /** Where the keyboard focus is. */
    public enum Focus {
        TABS, ROWS, RESET, APPLY, BACK
    }

    /** The key capture's state: none, waiting, or waiting after a refused key. */
    public enum Capture {
        NONE, WAITING, USED, RESERVED
    }

    /** What a key asks of the flow. */
    public enum Result {
        NONE, APPLY, BACK, RESET
    }

    /**
     * The CONTROLS tab's visible key list rows: one less than before the author's extra (2026-10-14) added the
     * "Hide Mouse Cursor" switch above it, so the tab still fits the body (same margin as the switch-free layout).
     */
    public static final int KEY_ROWS = 4;

    private final LiveSettings live;
    private final KeyTable keys;
    private final List<GameAction> actions = List.of(GameAction.values());
    private final ScrollBox keyList = new ScrollBox(KEY_ROWS);
    /** The display mode the app runs in: a change applies only after a restart (decision 2). */
    private final boolean runningFullscreen;
    /** M4 (final review): {@code -Dtob.volume} is set and replaces Master; the Master row is shown disabled. */
    private final boolean masterOverridden;
    /** Phase 7c: whether the game has an audio device; without one the SOUND rows are disabled, with a hint. */
    private final boolean sound;
    private Tab tab = Tab.SOUND;
    private Focus focus = Focus.TABS;
    private int row;
    /**
     * CONTROLS tab only, meaningful while {@code focus == ROWS}: whether the "Hide Mouse Cursor" switch (true) or
     * the key list (false, {@link #row} the action index) holds the row focus (author's extra, 2026-10-14).
     */
    private boolean onSwitch = true;
    private Capture capture = Capture.NONE;
    private KeyChord refused;
    /** The values shown while the window fades out after it was left ({@link #leave}); null while it is open. */
    private Settings shownWhileLeaving;

    public OptionsModel(LiveSettings live, KeyTable keys) {
        this(live, keys, false);
    }

    /** @param masterOverridden whether {@code -Dtob.volume} replaces Master (M4: shown disabled, with a note) */
    public OptionsModel(LiveSettings live, KeyTable keys, boolean masterOverridden) {
        this(live, keys, masterOverridden, true);
    }

    /**
     * @param masterOverridden whether {@code -Dtob.volume} replaces Master (M4: shown disabled, with a note)
     * @param sound            Phase 7c: whether the game has an audio device (else the SOUND rows are disabled)
     */
    public OptionsModel(LiveSettings live, KeyTable keys, boolean masterOverridden, boolean sound) {
        this.live = Objects.requireNonNull(live, "live");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.runningFullscreen = live.saved().fullscreen();
        this.masterOverridden = masterOverridden;
        this.sound = sound;
        keyList.size(actions.size());
    }

    /** M4: whether {@code -Dtob.volume} replaces Master; the Master row is then disabled with a note. */
    public boolean masterOverridden() {
        return masterOverridden;
    }

    /** Phase 7c: whether the game has an audio device; without one the SOUND tab's rows are disabled. */
    public boolean soundAvailable() {
        return sound;
    }

    /**
     * Whether {@code option} can be changed: not a SOUND row without an audio device (Phase 7c), not Master while
     * {@code -Dtob.volume} replaces it (M4). A disabled row is drawn grey and ignores changes.
     */
    public boolean enabled(Option option) {
        return !(option.tab() == Tab.SOUND && !sound) && !(option == Option.MASTER_VOLUME && masterOverridden);
    }

    /** Phase 7c: the hint below the current tab's rows - "No audio device found" on SOUND without a device. */
    public Optional<String> hint(Texts texts) {
        return tab == Tab.SOUND && !sound ? Optional.of(texts.get("options.sound.none")) : Optional.empty();
    }

    /** The window opens: the SOUND tab, the focus on the tab bar, the saved options shown. */
    public void open() {
        shownWhileLeaving = null;
        live.revert();
        tab = Tab.SOUND;
        focus = Focus.TABS;
        row = 0;
        onSwitch = true;
        capture = Capture.NONE;
        keyList.home();
    }

    public Tab tab() {
        return tab;
    }

    public Focus focus() {
        return focus;
    }

    /** The focused row: an option of the tab, or on CONTROLS (list focused) the selected action's index. */
    public int row() {
        return row;
    }

    /** The options of the current tab, top to bottom (one on CONTROLS: the "Hide Mouse Cursor" switch). */
    public List<Option> options() {
        List<Option> options = new ArrayList<>();
        for (Option option : Option.values()) {
            if (option.tab() == tab) {
                options.add(option);
            }
        }
        return options;
    }

    /**
     * CONTROLS tab only: whether the key list, not the "Hide Mouse Cursor" switch above it, holds the conceptual row
     * focus (author's extra, 2026-10-14); the caller also checks {@link #focus()} {@code == ROWS}.
     */
    public boolean controlsListFocused() {
        return tab == Tab.CONTROLS && !onSwitch;
    }

    /** Every game action, in the CONTROLS tab's key list order. */
    public List<GameAction> actions() {
        return actions;
    }

    public ScrollBox keyList() {
        return keyList;
    }

    /** The options as edited (and previewed); while the window fades out after {@link #leave}, the values it showed. */
    public Settings edited() {
        return shownWhileLeaving != null ? shownWhileLeaving : live.current();
    }

    /**
     * The window is left (acceptance 2026-10-14): it keeps showing its current values while it fades out, even when
     * Back restores the live values at once, so they do not visibly jump back. {@link #open} shows live values again.
     */
    public void leave() {
        shownWhileLeaving = live.current();
    }

    /** Whether the display mode shown differs from the one the game runs in (decision 2: after a restart). */
    public boolean restartPending() {
        return edited().fullscreen() != runningFullscreen;
    }

    // ------------------------------------------------------------------ keyboard

    public Result key(UiKey key) {
        if (capturing()) {
            return Result.NONE;
        }
        if (key == UiKey.ESCAPE) {
            return Result.BACK;
        }
        if (key == UiKey.TAB) {
            cycleFocus();
            return Result.NONE;
        }
        return switch (focus) {
            case TABS -> tabsKey(key);
            case ROWS -> tab == Tab.CONTROLS ? controlsKey(key) : rowsKey(key);
            case RESET -> resetKey(key);
            case APPLY, BACK -> buttonsKey(key);
        };
    }

    private Result tabsKey(UiKey key) {
        // Task 10 minor (final review): selectTab sets Focus.TABS and enterRows sets Focus.ROWS already; the
        // former "focus = focus == Focus.ROWS ? Focus.ROWS : Focus.TABS" reassignment never changed anything.
        switch (key) {
            case LEFT -> selectTab(Tab.values()[Math.max(0, tab.ordinal() - 1)]);
            case RIGHT -> selectTab(Tab.values()[Math.min(Tab.values().length - 1, tab.ordinal() + 1)]);
            case DOWN, ENTER, SPACE -> enterRows();
            default -> {
            }
        }
        return Result.NONE;
    }

    private Result rowsKey(UiKey key) {
        List<Option> options = options();
        switch (key) {
            // fix wave (Task 3-4 review #5): Up and Down pass over disabled rows, as the mouse cannot use them
            case UP -> {
                int previous = usableRow(row - 1, -1);
                if (previous < 0) {
                    focus = Focus.TABS;
                } else {
                    row = previous;
                }
            }
            case DOWN -> {
                int next = usableRow(row + 1, 1);
                if (next < 0) {
                    focus = Focus.APPLY;
                } else {
                    row = next;
                }
            }
            case LEFT -> change(options.get(row), -1);
            case RIGHT -> change(options.get(row), 1);
            case ENTER, SPACE -> {
                Option option = options.get(row);
                if (option.kind() != Kind.PERCENT) {
                    change(option, option.kind() == Kind.SWITCH ? 0 : 1);
                }
            }
            default -> {
            }
        }
        return Result.NONE;
    }

    /**
     * The CONTROLS tab while {@code focus == ROWS} (author's extra, 2026-10-14): on the "Hide Mouse Cursor" switch
     * Up leaves to the tab bar, Down enters the key list at its first row, and Left/Right/Enter/Space toggle it; on
     * the key list it behaves as the former KEYS tab did, except Up from its first row returns to the switch
     * instead of the tab bar.
     */
    private Result controlsKey(UiKey key) {
        return onSwitch ? switchKey(key) : keyListKey(key);
    }

    private Result switchKey(UiKey key) {
        switch (key) {
            case UP -> focus = Focus.TABS;
            case DOWN -> {
                onSwitch = false;
                selectKey(0);
            }
            case LEFT, RIGHT, ENTER, SPACE -> change(Option.HIDE_CURSOR, 0);
            default -> {
            }
        }
        return Result.NONE;
    }

    private Result keyListKey(UiKey key) {
        int last = actions.size() - 1;
        switch (key) {
            case UP -> {
                if (row == 0) {
                    onSwitch = true;
                } else {
                    selectKey(row - 1);
                }
            }
            case DOWN -> {
                if (row == last) {
                    focus = Focus.RESET;
                } else {
                    selectKey(row + 1);
                }
            }
            case PAGE_UP -> selectKey(Math.max(0, row - KEY_ROWS));
            case PAGE_DOWN -> selectKey(Math.min(last, row + KEY_ROWS));
            case HOME -> selectKey(0);
            case END -> selectKey(last);
            case ENTER, SPACE -> startCapture();
            default -> {
            }
        }
        return Result.NONE;
    }

    private Result resetKey(UiKey key) {
        switch (key) {
            case ENTER, SPACE -> {
                return Result.RESET;
            }
            case UP -> focus = Focus.ROWS;
            case DOWN, RIGHT -> focus = Focus.APPLY;
            default -> {
            }
        }
        return Result.NONE;
    }

    private Result buttonsKey(UiKey key) {
        switch (key) {
            case LEFT, RIGHT -> focus = focus == Focus.APPLY ? Focus.BACK : Focus.APPLY;
            case UP -> {
                if (tab == Tab.CONTROLS) {
                    focus = Focus.RESET;
                } else {
                    backToRows();
                }
            }
            case ENTER, SPACE -> {
                return focus == Focus.APPLY ? Result.APPLY : Result.BACK;
            }
            default -> {
            }
        }
        return Result.NONE;
    }

    private void cycleFocus() {
        focus = switch (focus) {
            case TABS -> Focus.ROWS;
            case ROWS -> tab == Tab.CONTROLS ? Focus.RESET : Focus.APPLY;
            case RESET -> Focus.APPLY;
            case APPLY -> Focus.BACK;
            case BACK -> Focus.TABS;
        };
        if (focus == Focus.ROWS) {
            enterRows();
        }
    }

    /**
     * Entering the rows always lands on the first one: on CONTROLS that is the "Hide Mouse Cursor" switch; elsewhere
     * the first usable one - a tab without any (SOUND without an audio device) passes the focus on to Apply.
     */
    private void enterRows() {
        focus = Focus.ROWS;
        if (tab == Tab.CONTROLS) {
            onSwitch = true;
            return;
        }
        int clamped = Math.clamp(row, 0, options().size() - 1);
        int usable = usableRow(clamped, 1);
        if (usable < 0) {
            usable = usableRow(clamped, -1);
        }
        if (usable < 0) {
            focus = Focus.APPLY;
        } else {
            row = usable;
        }
    }

    /** Up from Apply or Back (not on CONTROLS): the row left last if usable, else the last usable one, else tabs. */
    private void backToRows() {
        List<Option> options = options();
        int usable = row >= 0 && row < options.size() && enabled(options.get(row)) ? row
                : usableRow(options.size() - 1, -1);
        if (usable < 0) {
            focus = Focus.TABS;
        } else {
            focus = Focus.ROWS;
            row = usable;
        }
    }

    /** The first enabled row of the current tab from {@code from} on in {@code step}'s direction; -1 if none. */
    private int usableRow(int from, int step) {
        List<Option> options = options();
        for (int i = from; i >= 0 && i < options.size(); i += step) {
            if (enabled(options.get(i))) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ mouse and changes

    /**
     * A click on a tab, or Left/Right on the tab bar: the new tab's row index resets to the first (not entered
     * yet; Task 10 minor, final review) and the focus (back) on the tab bar.
     */
    public void selectTab(Tab selected) {
        if (selected != tab) {
            tab = selected;
            row = 0;
            onSwitch = true;
            keyList.home();
        }
        capture = Capture.NONE;
        focus = Focus.TABS;
    }

    /** A click on Reset, Apply or Back moves the focus there (the flow then acts). */
    public void focus(Focus target) {
        focus = target;
        capture = Capture.NONE;
    }

    /**
     * Changes {@code option} by one step in {@code direction} (−1, +1; a switch toggles on any direction, 0
     * included) and previews it; the option's row takes the focus.
     */
    public void change(Option option, int direction) {
        tab = option.tab();
        focus = Focus.ROWS;
        row = options().indexOf(option);
        if (tab == Tab.CONTROLS) {
            // the only CONTROLS option is the switch (author's extra, 2026-10-14): a click or a key change on it
            // always means the switch, not the key list below
            onSwitch = true;
        }
        if (!enabled(option)) {
            // M4: the override replaces Master anyway; Phase 7c: without an audio device nothing is heard - the
            // row is shown disabled and ignores changes
            return;
        }
        Settings s = edited();
        live.preview(switch (option) {
            case MASTER_VOLUME -> s.withMasterVolume(step(s.masterVolume(), direction, Settings.VOLUME_STEP));
            case BACKGROUND_VOLUME -> s.withBackgroundVolume(step(s.backgroundVolume(), direction,
                    Settings.VOLUME_STEP));
            case SOUND_DETAIL -> s.withSoundDetail(s.soundDetail() + Integer.signum(direction));
            case DISPLAY -> s.withFullscreen(!s.fullscreen());
            case BRIGHTNESS -> s.withBrightness(step(s.brightness(), direction, Settings.BRIGHTNESS_STEP));
            case MOON_DETAIL -> s.withHalfMoonDetail(!s.halfMoonDetail());
            case AMBIENT_EFFECTS -> s.withAmbientEffects(!s.ambientEffects());
            case FLASH_STRENGTH -> s.withFlashStrength(step(s.flashStrength(), direction, Settings.FLASH_STEP));
            case PANEL_SIZE -> s.withPanelSize(s.panelSize() == PanelSize.FULL_WIDTH ? PanelSize.FIT_HEIGHT
                    : PanelSize.FULL_WIDTH);
            case BACKWARDS_ALLOWED -> s.withBackwardsAllowed(!s.backwardsAllowed());
            case HIDE_CURSOR -> s.withHideMouseCursor(!s.hideMouseCursor());
            case MUSIC -> s.withMusic(!s.music());
            case LOOK -> s.withClassicLook(!s.classicLook());
        });
    }

    /** The next value on the {@code step} grid in {@code direction} (a value off the grid moves onto it). */
    private static int step(int value, int direction, int step) {
        if (direction > 0) {
            return Math.floorDiv(value, step) * step + step;
        }
        if (direction < 0) {
            return Math.floorDiv(value + step - 1, step) * step - step;
        }
        return value;
    }

    /** A click on the CONTROLS tab's key list row {@code index}: it is selected and, unless fixed, asks for a key. */
    public void clickKey(int index) {
        tab = Tab.CONTROLS;
        focus = Focus.ROWS;
        selectKey(index);
        startCapture();
    }

    /** Selects key list row {@code index}: the list (not the switch above it) takes the row focus. */
    private void selectKey(int index) {
        onSwitch = false;
        row = Math.clamp(index, 0, actions.size() - 1);
        keyList.show(row);
        capture = Capture.NONE;
    }

    private void startCapture() {
        if (tab == Tab.CONTROLS && focus == Focus.ROWS && !onSwitch && !fixed(actions.get(row))) {
            capture = Capture.WAITING;
        }
    }

    // ------------------------------------------------------------------ key capture

    public boolean capturing() {
        return capture != Capture.NONE;
    }

    public Capture capture() {
        return capture;
    }

    /**
     * A raw key press while asking for a key: Esc cancels, a key without a name is ignored, a reserved or used one
     * is refused (still waiting), any other becomes the selected action's key.
     *
     * @return whether the key was taken by the capture (always while capturing)
     */
    public boolean captured(int keyCode, boolean leftCtrl) {
        if (!capturing()) {
            return false;
        }
        KeyChord escape = keys.defaults().get(GameAction.MENU);
        if (escape != null && keyCode == escape.keyCode()) {
            capture = Capture.NONE;
            return true;
        }
        if (!keys.nameable(keyCode)) {
            return true;
        }
        KeyChord chord = new KeyChord(keyCode, leftCtrl);
        GameAction action = actions.get(row);
        if (keys.reserved(keyCode)) {
            capture = Capture.RESERVED;
            refused = chord;
            return true;
        }
        for (Map.Entry<GameAction, KeyChord> binding : bindings().entrySet()) {
            if (binding.getValue().equals(chord) && binding.getKey() != action) {
                capture = Capture.USED;
                refused = chord;
                return true;
            }
        }
        Map<GameAction, KeyChord> overrides = new EnumMap<>(GameAction.class);
        overrides.putAll(edited().keys());
        if (chord.equals(keys.defaults().get(action))) {
            overrides.remove(action);
        } else {
            overrides.put(action, chord);
        }
        live.preview(edited().withKeys(overrides));
        capture = Capture.NONE;
        return true;
    }

    /** "Reset to defaults", after the flow's ARE YOU SURE?: every remapped key goes (previewed). */
    public void resetKeys() {
        capture = Capture.NONE;
        live.preview(edited().withKeys(Map.of()));
    }

    public boolean fixed(GameAction action) {
        return keys.fixed().contains(action);
    }

    /** The key {@code action} has now. */
    public KeyChord chord(GameAction action) {
        return bindings().get(action);
    }

    private Map<GameAction, KeyChord> bindings() {
        return keys.bindings(edited().keys());
    }

    // ------------------------------------------------------------------ texts

    /** The KEYS list's key column for action {@code index}: its key, or the capture's prompt. */
    public String keyText(int index, Texts texts) {
        if (capturing() && index == row) {
            return switch (capture) {
                case USED -> texts.format("options.keys.used", keys.name(refused));
                case RESERVED -> texts.format("options.keys.reserved", keys.name(refused));
                default -> texts.get("options.keys.press");
            };
        }
        KeyChord chord = chord(actions.get(index));
        return chord == null ? "" : keys.name(chord);
    }

    /**
     * The label of {@code option}, left of its value (R2, final review re-review): plain, except Master shows it
     * is overridden there, not squeezed into the much narrower value column where "100% (overridden by
     * -Dtob.volume)" shrinks to a few pixels high.
     */
    public String label(Option option, Texts texts) {
        if (option == Option.MASTER_VOLUME && masterOverridden) {
            return texts.get("options.master.overridden");
        }
        return texts.get(option.textKey());
    }

    /** The value of {@code option} as shown. */
    public String value(Option option, Texts texts) {
        Settings s = edited();
        return switch (option) {
            case MASTER_VOLUME -> texts.format("options.percent", s.masterVolume());
            case BACKGROUND_VOLUME -> texts.format("options.percent", s.backgroundVolume());
            case SOUND_DETAIL -> texts.get("options.detail." + s.soundDetail());
            case DISPLAY -> texts.get(s.fullscreen() ? "options.display.fullscreen" : "options.display.window")
                    + (restartPending() ? " " + texts.get("options.restart") : "");
            case BRIGHTNESS -> texts.format("options.percent", s.brightness());
            case MOON_DETAIL -> texts.get(s.halfMoonDetail() ? "options.moon.half" : "options.moon.full");
            case AMBIENT_EFFECTS -> texts.get(s.ambientEffects() ? "options.on" : "options.off");
            case FLASH_STRENGTH -> texts.format("options.percent", s.flashStrength());
            case PANEL_SIZE -> texts.get(s.panelSize() == PanelSize.FIT_HEIGHT ? "options.panel.fit"
                    : "options.panel.full");
            case BACKWARDS_ALLOWED -> texts.get(s.backwardsAllowed() ? "options.on" : "options.off");
            case HIDE_CURSOR -> texts.get(s.hideMouseCursor() ? "options.on" : "options.off");
            case MUSIC -> texts.get(s.music() ? "options.on" : "options.off");
            case LOOK -> texts.get(s.classicLook() ? "options.look.classic" : "options.look.remake");
        };
    }

    /** A percent option's bar, 0..1 between its minimum and maximum; 0 for the others. */
    public float fraction(Option option) {
        Settings s = edited();
        return switch (option) {
            case MASTER_VOLUME -> (float) s.masterVolume() / Settings.VOLUME_MAX;
            case BACKGROUND_VOLUME -> (float) s.backgroundVolume() / Settings.VOLUME_MAX;
            case BRIGHTNESS -> (float) (s.brightness() - Settings.BRIGHTNESS_MIN)
                    / (Settings.BRIGHTNESS_MAX - Settings.BRIGHTNESS_MIN);
            case FLASH_STRENGTH -> (float) s.flashStrength() / Settings.FLASH_MAX;
            default -> 0f;
        };
    }
}
