package de.latzig.game.tob.client.logic.frontend;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.EditorModel;
import de.latzig.game.tob.client.logic.editor.EditorScreens;
import de.latzig.game.tob.client.logic.editor.LibraryDialog;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerIds;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.ui.CreditsTicker;
import de.latzig.game.tob.client.logic.ui.ScreenFade;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.TextField;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Outcome;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.TowerScore;
import de.latzig.game.tob.core.sim.Rules;

/**
 * The game flow (Phase 5a spec decision 2): ToB 2009's screen graph with the original's rules, engine-free. Loader →
 * main menu → Play (Phase 5c-1; 5a: New Game / Continue) → Overview → Briefing → game → result window → Overview
 * (the next tower preselected); every change fades out to black and in again ({@link ScreenFade}). The app (the
 * front-end state) feeds keys, typed characters, mouse hits and the frame time, draws the state
 * ({@code StartScreens}) and executes the {@link Effect}s: start a tower, prepare a tower's sky for the Briefing's
 * loading bar, quit.
 * <p>
 * Keyboard as ToB 2009's SJGL toolkit (Tab moves the focus, Enter or Space press the focused button, Up/Down move in
 * a list, Enter in a field or list moves on to the first button; the Play list: Enter chooses the row at once) plus
 * Esc = Back on every screen (decision 6): on the main menu Esc and Quit ask ARE YOU SURE? with NO preselected, and
 * so does the window's close button on every screen ({@link #requestQuit}). Only a click, Tab and the arrow keys move
 * the focus; the mouse hovering over a button only highlights it ({@code StartScreens}, final review I2), so typing a
 * name or Up/Down in a list keep working while the cursor rests on a button. The profile is saved at every tower
 * start and every attempt end (decisions 9 and 10); practice on a solved tower leaves it unchanged (decision 8). A
 * developer session ({@code -Dtob.tower}) has no profile: its result window leads to the main menu. The Overview is a
 * 3×3 grid (author decision 2026-10-12): the arrow keys move in it without wrapping, Enter/Play or a double click
 * start the selected tower, a click selects it; previous/next group are buttons.
 * <p>
 * Phase 5b: the main menu's Options lead to {@link Screen#OPTIONS} ({@link OptionsModel} over the {@link LiveSettings}:
 * Apply saves and returns, Back and Esc restore the values from before; Reset to Defaults asks first; while a key is
 * captured the raw keys go to {@link #captureKey} first). Phase 5c-1 (decision 4): its Credits window became the
 * CREDITS tab of {@link Screen#HALL_OF_FAME} ({@link HallOfFame}), beside the SCORES tab with the profiles ranked by
 * total score; Left, Right and Tab switch the tab, the arrow and page keys scroll it.
 * <p>
 * Phase 6b: the main menu's Tower Designer leads to {@link Screen#EDITOR} ({@link TowerDesigner}, drawn by
 * {@link EditorScreens}): mouse presses, moves and releases, keys and typed characters go to the designer, which
 * answers with a {@link TowerDesigner.Request}; its questions are {@link Modal#EDITOR_CONFIRM}. A test play starts the
 * tower like a developer session ({@link #testPlay}: no profile, no score, no recording) and every end of it - the end
 * screen, Abandon, a failed start - returns to the editor as it was (level, cursor, undo history); a restart restarts
 * the test play. Leaving the designer to the main menu forgets its unsaved state; the library it had open is opened
 * again next time (in this session; Phase 6d decision 8: also in the next one, through {@code settings.json}).
 * <p>
 * Phase 5c-1 (spec decisions 1 and 2): the main menu has six entries — Play, Tutorial, Tower Designer, Options, Hall
 * of Fame, Quit. Play opens {@link Screen#PLAY}, one window with the profile list ({@link PlayModel}): "&lt; New
 * Profile &gt;" on top opens the name entry ({@link Screen#NEW_GAME}, whose Back returns to the list), a profile its
 * Overview; at most {@link ProfileStore#MAX_PROFILES} profiles can be created, Delete asks ARE YOU SURE? with NO
 * preselected.
 * <p>
 * Phase 5c-1 (decision 3): Tutorial opens {@link Screen#TUTORIAL} ({@link TutorialModel}, once the app has handed
 * over the 9 tutorial towers, {@link #tutorial(List)}): the Overview's grid with every tower open and without a
 * profile. A tower runs as from the Overview — Briefing, game, result window (no score, Retry after a failure) — and
 * every way back leads to the Tutorial with that tower selected; a completed tutorial tower is marked solved in
 * {@code settings.json} for every profile, and no profile file is written. A tutorial tower without a goal (no
 * klondikes, no robots: tutorial-000 … 006) can never be completed; it is marked done once an attempt on it ended
 * regularly (author decision 2026-10-15); a game left with an error does not count.
 * <p>
 * Phase 5c-2 (decision 6): every result window — the standard game, the Tutorial, a developer start — gets an
 * interlude, one of the original's demos 0-5 after a success or 6-11 after a failure or an abandon, chosen by the
 * original's one shared counter ({@link InterludePicker}, one per flow, i.e. per program run); {@link #interlude()}
 * reports it while the result window is shown. A designer's test play has no result window and no interlude.
 * <p>
 * Phase 6d (spec decisions 2–6): the Overview's Library button steps from the original towers through the player's
 * own libraries ({@code <userdir>/towers/}, by name, {@link CustomLibrary}) and back; one that cannot be read or has
 * no tower is skipped; the profile remembers the choice. An own tower runs as a campaign tower — Briefing, game,
 * result window with interlude and music, Retry — but without score, attempts or practice: a completion marks it
 * solved in the profile for its library. A tower without a task or with a check error is refused with the original's
 * hint or the designer's refusal ({@link OverviewModel.Refused}).
 */
public final class GameFlow {

    private static final Logger LOG = LoggerFactory.getLogger(GameFlow.class);

    /** The library of the standard game. */
    public static final String LIBRARY = "original";

    public enum Screen {
        LOADER, MENU, PLAY, NEW_GAME, OVERVIEW, TUTORIAL, BRIEFING, RESULT, GAME, OPTIONS, HALL_OF_FAME, EDITOR
    }

    /**
     * A modal dialog over the screen: ARE YOU SURE? for Quit, Delete or Reset to Defaults (Phase 5b), or one of the
     * Tower Designer's questions (Phase 6b, {@link #editorQuestion}), or an error message with OK.
     */
    public enum Modal {
        NONE, CONFIRM_QUIT, CONFIRM_DELETE, CONFIRM_RESET_KEYS, MESSAGE, EDITOR_CONFIRM
    }

    /**
     * Everything that can have the focus or be clicked. {@code LIST} is the Play window's list (hit row = the
     * {@link PlayModel} row, 0 = "&lt; New Profile &gt;"), {@code GRID} the Overview's or the Tutorial's 3×3 grid (hit
     * row = cell index 0–8, A–I), {@code PREVIOUS_GROUP} and {@code NEXT_GROUP} the Overview's group buttons,
     * {@code LIBRARY} its Library button (Phase 6d). Phase 5c-1: {@code TAB} also on the Hall of Fame (hit row = the
     * {@link HallOfFame.Tab}'s ordinal). Phase 5b, the Options window: {@code TAB} (hit row = the
     * {@link OptionsModel.Tab}'s ordinal), {@code OPTION_MINUS}, {@code OPTION_PLUS} and {@code OPTION_SWITCH} (hit row
     * = the {@link OptionsModel.Option}'s ordinal), {@code KEY_ROW} (hit row = the action's index in the CONTROLS tab's
     * key list), {@code KEYS_RESET}, {@code APPLY}; {@code SCROLL_TRACK} is the scroll bar of the CONTROLS key list,
     * the Play list, the Hall of Fame's shown tab or the designer's lists (the input turns the pointer into a fraction
     * of the track).
     */
    public enum Widget {
        MENU_PLAY, MENU_TUTORIAL, MENU_DESIGNER, MENU_OPTIONS, MENU_HALL_OF_FAME, MENU_QUIT,
        NAME, LIST, GRID, PREVIOUS_GROUP, NEXT_GROUP, LIBRARY, START, DELETE, PLAY, BACK, OVERVIEW, RETRY, YES, NO, OK,
        TAB, OPTION_MINUS, OPTION_PLUS, OPTION_SWITCH, KEY_ROW, KEYS_RESET, APPLY, SCROLL_TRACK,
        // Phase 6b, the Tower Designer (TowerDesigner): EDITOR_MENU (hit row = the menu's ordinal), EDITOR_ITEM (the
        // command's ordinal), EDITOR_LIST (the tower's index), EDITOR_CELL (y * 16 + x), EDITOR_PALETTE (the palette
        // index), EDITOR_LEVEL (the level), EDITOR_TITLE, EDITOR_DIALOG (a dialog's button, TowerDesigner.DIALOG_OK
        // ...) and EDITOR_DIALOG_LIST (a library dialog's row); Phase 6b-2: EDITOR_FIELD (a part of a Settings,
        // Effects or colour dialog's field, EditorForm.hitRow) and EDITOR_PREVIEW (the 3D preview)
        EDITOR_MENU, EDITOR_ITEM, EDITOR_LIST, EDITOR_CELL, EDITOR_PALETTE, EDITOR_LEVEL, EDITOR_TITLE, EDITOR_DIALOG,
        EDITOR_DIALOG_LIST, EDITOR_FIELD, EDITOR_PREVIEW
    }

    /** The Overview's focus order (Tab), left to right / top to bottom (Phase 6d: Library left of Play). */
    public static final List<Widget> OVERVIEW_FOCUS = List.of(Widget.GRID, Widget.PREVIOUS_GROUP, Widget.NEXT_GROUP,
            Widget.LIBRARY, Widget.PLAY, Widget.BACK);
    /** The Tutorial's focus order (Tab): the grid, Play, Back. */
    public static final List<Widget> TUTORIAL_FOCUS = List.of(Widget.GRID, Widget.PLAY, Widget.BACK);

    /**
     * The main menu entries, top to bottom (Phase 5c-1 decision 1). The Tower Designer and the Tutorial are greyed
     * until the app has handed over their libraries ({@link #designer(TowerLibraries)}, {@link #tutorial(List)}).
     */
    public static final List<Widget> MENU = List.of(Widget.MENU_PLAY, Widget.MENU_TUTORIAL, Widget.MENU_DESIGNER,
            Widget.MENU_OPTIONS, Widget.MENU_HALL_OF_FAME, Widget.MENU_QUIT);

    /** A mouse hit: a widget, and the row of the Play window's list or the cell index 0–8 of a 3×3 grid. */
    public record Hit(Widget widget, int row) {

        public static Hit of(Widget widget) {
            return new Hit(widget, -1);
        }
    }

    /** What the app has to do. */
    public sealed interface Effect {
    }

    /** Start (or restart) {@code tower}; {@code practice} for a solved tower. */
    public record StartTower(TowerDefinition tower, boolean practice) implements Effect {
    }

    /** The Briefing shows {@code tower}: generate its sky textures and report the progress ({@link #skyProgress}). */
    public record PrepareTower(TowerDefinition tower) implements Effect {
    }

    public record Quit() implements Effect {
    }

    private final Texts texts;
    private final ProfileStore store;
    private final LiveSettings settings;
    private final OptionsModel options;
    private final HallOfFame hallOfFame;
    private final ScreenFade fade = new ScreenFade();
    private final CreditsTicker ticker;
    /** Phase 5c-2 decision 6: the original's shared interlude counter, one per program run. */
    private final InterludePicker interludes = new InterludePicker();
    private final TextField name = new TextField();
    private final Deque<Effect> effects = new ArrayDeque<>();
    private Campaign campaign;
    private Screen screen = Screen.LOADER;
    private Modal modal = Modal.NONE;
    private boolean yes;
    /** The screen a running fade-out leads to; null for Quit (Phase 5c-2: the music fades out with the screen). */
    private Screen leavingTo;
    private String message = "";
    /** The message box's caption key: {@code frame.error}, or {@code frame.notice} for a hint ({@link #inform}). */
    private String messageCaption = "frame.error";
    private Widget focus = Widget.MENU_PLAY;
    private float loadProgress;
    private PlayModel play = new PlayModel(List.of(), Optional.empty());
    private OverviewModel overview;
    /** Phase 6d: the own library the Overview shows (null: the original towers). */
    private CustomLibrary custom;
    /** Phase 6d: the own library whose tower is played (null: a campaign, tutorial, developer or test tower). */
    private String customPlaying;
    /** Phase 5c-1: the tutorial towers by index (null: the entry stays greyed), its window, its attempt running. */
    private List<TowerDefinition> tutorialTowers;
    private TutorialModel tutorial;
    private boolean inTutorial;
    private Profile profile;
    private TowerDefinition tower;
    private boolean practice;
    private String preselect;
    private BriefingText briefing;
    private float skyProgress;
    private ResultText result;
    /** The demo number of the last result window's interlude (Phase 5c-2), -1 before the first. */
    private int interlude = -1;
    private boolean retry;
    private List<String> allSolved = List.of();
    private double clock;
    /**
     * A save failure not yet shown (spec decision 10, review Important #1): {@link #save()} never pops up a MESSAGE
     * that a screen change (tower start, F9/pause restart) would wipe unseen; it is deferred here and shown at the
     * latest when the attempt ends, over the RESULT screen.
     */
    private String pendingSaveError;
    /** Phase 6b: the Tower Designer's libraries (null: the entry stays greyed), its screen, its test play. */
    private TowerLibraries libraries;
    private TowerDesigner designer;
    private TowerDesigner.Test test;
    private TowerLibraries.Entry lastLibrary;
    private String editorQuestion = "";
    /** Phase 6d decision 7: where the designer's new towers get their ids. */
    private TowerIds towerIds = new TowerIds();
    /** Phase 6d decision 8: the designer's library as {@code settings.json} holds it (null: none). */
    private String rememberedLibrary;

    /** A flow with the options of the store's settings file and no key table (tests). */
    public GameFlow(Texts texts, ProfileStore store) {
        this(texts, store, LiveSettings.load(store.settings()), new KeyTable(Map.of(), Set.of(), Map.of()));
    }

    /**
     * @param settings the app's options (Phase 5b), previewed and saved by the Options window
     * @param keys     the default keys, the fixed actions and the key names for the KEYS tab
     */
    public GameFlow(Texts texts, ProfileStore store, LiveSettings settings, KeyTable keys) {
        this(texts, store, settings, keys, false);
    }

    /**
     * @param settings         the app's options (Phase 5b), previewed and saved by the Options window
     * @param keys             the default keys, the fixed actions and the key names for the KEYS tab
     * @param masterOverridden M4 (final review): whether {@code -Dtob.volume} replaces Master
     */
    public GameFlow(Texts texts, ProfileStore store, LiveSettings settings, KeyTable keys,
            boolean masterOverridden) {
        this(texts, store, settings, keys, masterOverridden, true);
    }

    /**
     * @param settings         the app's options (Phase 5b), previewed and saved by the Options window
     * @param keys             the default keys, the fixed actions and the key names for the KEYS tab
     * @param masterOverridden M4 (final review): whether {@code -Dtob.volume} replaces Master
     * @param sound            Phase 7c: whether the game has an audio device (else Options disables SOUND)
     */
    public GameFlow(Texts texts, ProfileStore store, LiveSettings settings, KeyTable keys,
            boolean masterOverridden, boolean sound) {
        this.texts = texts;
        this.store = store;
        this.settings = settings;
        this.options = new OptionsModel(settings, keys, masterOverridden, sound);
        this.hallOfFame = new HallOfFame(List.of(texts.get("credits.text").split("\n", -1)));
        this.ticker = new CreditsTicker(texts.get("menu.ticker"));
    }

    // ------------------------------------------------------------------ the app's calls

    /** The loader's progress, 0..1. */
    public void loadProgress(float progress) {
        loadProgress = Math.clamp(progress, 0f, 1f);
    }

    /** Loading is done: the main menu follows. */
    public void loaded(Campaign loadedCampaign) {
        this.campaign = loadedCampaign;
        loadProgress = 1f;
        change(Screen.MENU);
    }

    /** A developer start ({@code -Dtob.tower}): no profile, the game runs at once. */
    public void developerSession(Campaign loadedCampaign, TowerDefinition started) {
        this.campaign = loadedCampaign;
        this.profile = null;
        this.inTutorial = false;
        this.customPlaying = null;
        this.tower = started;
        this.practice = false;
        screen = Screen.GAME;
    }

    public void update(double seconds) {
        clock += seconds;
        fade.update(seconds);
        if (screen == Screen.MENU) {
            ticker.advance(seconds);
        }
        if (screen == Screen.EDITOR && designer != null) {
            designer.update(seconds);
        }
    }

    /** Phase 6b: the Tower Designer's libraries; the main menu's entry is enabled from now on. */
    public void designer(TowerLibraries towerLibraries) {
        this.libraries = towerLibraries;
    }

    /** Phase 6d: where the next designer's new towers get their ids (default random UUIDs; tests: a fixed source). */
    public void towerIds(TowerIds ids) {
        this.towerIds = Objects.requireNonNull(ids, "ids");
    }

    /**
     * Phase 5c-1: the 9 tutorial towers; the main menu's Tutorial entry is enabled from now on. Any other set (a
     * tower that failed to load) leaves it greyed; that is logged.
     */
    public void tutorial(List<TowerDefinition> towers) {
        try {
            tutorialTowers = TutorialModel.sorted(towers);
        } catch (IllegalArgumentException e) {
            LOG.warn("The Tutorial stays greyed: {}", e.getMessage());
            tutorialTowers = null;
        }
    }

    /** The Tutorial window's state while it is shown (Phase 5c-1), else the last one or null. */
    public TutorialModel tutorial() {
        return tutorial;
    }

    /** Whether the tower played (or shown in the Briefing or result window) comes from the Tutorial. */
    public boolean inTutorial() {
        return inTutorial;
    }

    /** The Tower Designer while it is open (also during its test play), else null. */
    public TowerDesigner designer() {
        return designer;
    }

    /** The designer's test play running now, if any: no profile, no score, no recording (spec decision 6). */
    public Optional<TowerDesigner.Test> testPlay() {
        return Optional.ofNullable(test);
    }

    /** The caption of the designer's open question ({@link Modal#EDITOR_CONFIRM}). */
    public String editorQuestion() {
        return editorQuestion;
    }

    /** The Briefing's sky preparation, 0..1; Play is enabled at 1 (decision 14). */
    public void skyProgress(float progress) {
        skyProgress = Math.clamp(progress, 0f, 1f);
    }

    /**
     * The tower was restarted in the game (F9 or the pause menu's RESTART): the original's counter +1 and a new
     * attempt, saved; nothing in practice or without a profile. The screen stays GAME, where no modal is visible, so
     * a save failure here is only logged and deferred ({@link #save()}); it surfaces at the latest when the attempt
     * ends.
     */
    public void attemptRestarted() {
        if (test == null && profile != null && !practice && customPlaying == null) {
            profile = profile.ended(tower.id(), Outcome.RESTARTED, 0, 0, Instant.EPOCH).profile().started(tower.id());
            save();
        }
    }

    /**
     * The attempt is over (completed, failed, timed out or abandoned): the profile is updated and saved, and the
     * result window shows (decision 11).
     *
     * @param survivors the player spiders present and alive at the end (the survivor bonus)
     * @param frames    the attempt's simulation frames
     */
    public void attemptEnded(int exitCode, int survivors, int frames, Instant now) {
        if (test != null) {
            // Phase 6b: a test play ends back in the editor, its result in the status line; no profile, no score
            test = null;
            designer.model().testEnded(exitCode);
            screen = Screen.EDITOR;
            modal = Modal.NONE;
            fade.fadeIn();
            return;
        }
        boolean completed = exitCode == Rules.EXIT_COMPLETED;
        if (inTutorial && (completed || !hasGoal(tower))) {
            // author 2026-10-15: a goal-less tutorial tower is done once an attempt on it ended regularly (completed,
            // failed, timed out or abandoned); the guard: only in the Tutorial, never a campaign tower, a practice
            // replay, a developer start or a test play
            solveTutorial(tower.id());
        }
        Optional<TowerScore> score = Optional.empty();
        boolean solvedBefore = profile != null && campaign.allSolved(profile);
        if (customPlaying != null) {
            if (completed && profile != null) {
                // Phase 6d decision 5: an own tower scores nothing; it is marked solved for its library and saved
                profile = profile.withCustomSolved(customPlaying, tower.id());
                save();
            }
        } else if (profile != null && !practice) {
            Profile.Ended ended = profile.ended(tower.id(), completed ? Outcome.COMPLETED : Outcome.FAILED, survivors,
                    frames, now);
            profile = ended.profile();
            score = ended.score();
            save();
        }
        long total = profile == null ? 0L : profile.score();
        result = customPlaying != null ? ResultText.custom(exitCode, texts)
                : ResultText.of(exitCode, score, total, practice, texts);
        interlude = interludes.next(completed);
        LOG.info("Result window: {}, interlude demo {}", completed ? "completed" : "not completed", interlude);
        retry = !completed;
        allSolved = profile != null && !solvedBefore && campaign.allSolved(profile)
                ? ResultText.allSolved(total, texts) : List.of();
        // Phase 6d: back on an own library's Overview the tower just played stays selected, its group shown
        preselect = completed && !inTutorial && customPlaying == null ? null : tower.id();
        screen = Screen.RESULT;
        focus = Widget.OVERVIEW;
        flushPendingSaveError();
        fade.fadeIn();
    }

    /**
     * The game was left without a result: with an error (a missing asset or a failed frame, spec §6), shown in a
     * message box, or by the app ({@code error} empty). Back to the Tutorial from a tutorial tower, to the Overview
     * with a profile, else to the main menu.
     */
    public void gameLeft(String error) {
        if (test != null) {
            test = null;
            enter(Screen.EDITOR);
            if (!error.isEmpty()) {
                show(error);
            }
            fade.fadeIn();
            return;
        }
        if ((profile != null || inTutorial) && tower != null) {
            // final review M11: back on the Overview the tower just played (or failed to load) is selected
            preselect = tower.id();
        }
        enter(inTutorial ? Screen.TUTORIAL : profile != null && campaign != null ? Screen.OVERVIEW : Screen.MENU);
        if (!error.isEmpty()) {
            show(error);
        } else {
            flushPendingSaveError();
        }
        fade.fadeIn();
    }

    /** The next effect for the app, if any. */
    public Optional<Effect> poll() {
        return Optional.ofNullable(effects.poll());
    }

    // ------------------------------------------------------------------ input

    public void key(UiKey key) {
        if (!takesInput()) {
            return;
        }
        if (modal != Modal.NONE) {
            modalKey(key);
            return;
        }
        switch (screen) {
            case MENU -> menuKey(key);
            case PLAY -> playKey(key);
            case NEW_GAME -> newGameKey(key);
            case OVERVIEW -> gridKey(key, OVERVIEW_FOCUS);
            case TUTORIAL -> gridKey(key, TUTORIAL_FOCUS);
            case BRIEFING -> buttonsKey(key, List.of(Widget.PLAY, Widget.BACK));
            case RESULT -> buttonsKey(key, resultButtons());
            case OPTIONS -> optionsKey(key);
            case HALL_OF_FAME -> hallOfFameKey(key);
            case EDITOR -> handle(designer.key(key));
            default -> {
            }
        }
    }

    /** Whether the Options window waits for a key (Phase 5b): the app then hands raw keys to {@link #captureKey}. */
    public boolean capturing() {
        return screen == Screen.OPTIONS && modal == Modal.NONE && options.capturing();
    }

    /**
     * A raw key press (jME key code, left Ctrl held) while the Options window waits for a key (decision 5).
     *
     * @return whether the capture took it; if not, the key goes on as a menu key
     */
    public boolean captureKey(int keyCode, boolean leftCtrl) {
        return takesInput() && capturing() && options.captured(keyCode, leftCtrl);
    }

    /** {@code notches} of the mouse wheel (positive: up) for the KEYS list, the Hall of Fame's tab or the Play list. */
    public void wheel(int notches) {
        if (takesInput() && modal == Modal.NONE) {
            scrollBox().ifPresent(box -> box.wheel(notches));
        }
    }

    /** The scroll box shown: the key list on the Options window's CONTROLS tab, the Hall of Fame's, the Play list. */
    public Optional<ScrollBox> scrollBox() {
        if (screen == Screen.OPTIONS && options.tab() == OptionsModel.Tab.CONTROLS) {
            return Optional.of(options.keyList());
        }
        if (screen == Screen.PLAY) {
            return Optional.of(play.box());
        }
        if (screen == Screen.EDITOR && designer != null) {
            // Phase 6b: the designer's tower list, or its library dialog's list
            return Optional.of(designer.libraryDialog().map(LibraryDialog::box).orElse(designer.model().list()));
        }
        return screen == Screen.HALL_OF_FAME ? Optional.of(hallOfFame.box()) : Optional.empty();
    }

    /** A press on the scroll bar at {@code fraction} of its track (0 = top): pages, or grabs the thumb. */
    public void scrollPress(float fraction) {
        if (takesInput() && modal == Modal.NONE) {
            scrollBox().ifPresent(box -> box.press(fraction));
        }
    }

    /** The pointer moved to {@code fraction} of the track while the thumb is held. */
    public void scrollDrag(float fraction) {
        scrollBox().ifPresent(box -> box.drag(fraction));
    }

    /** The mouse button was released: a thumb drag ends. */
    public void scrollRelease() {
        options.keyList().release();
        hallOfFame.release();
        play.box().release();
    }

    /** A typed character (the OS's keyboard layout applies); only the name field takes it. */
    public void typed(char c) {
        if (takesInput() && modal == Modal.NONE && screen == Screen.NEW_GAME && focus == Widget.NAME) {
            name.type(c);
        }
        if (takesInput() && modal == Modal.NONE && screen == Screen.EDITOR) {
            designer.typed(c);
        }
    }

    /**
     * Phase 6b: a mouse button went down on the designer's screen over {@code hit} (empty: nothing), the right one
     * removes; the left one may begin a painting stroke that ends with {@link #editorRelease}.
     */
    public void editorPress(Optional<Hit> hit, boolean right) {
        if (takesInput() && modal == Modal.NONE && screen == Screen.EDITOR) {
            handle(designer.press(hit, right));
        }
    }

    /** Phase 6b: the mouse moved on the designer's screen over {@code hit}, the left button {@code held} or not. */
    public void editorPointer(Optional<Hit> hit, boolean held) {
        if (takesInput() && modal == Modal.NONE && screen == Screen.EDITOR) {
            designer.pointer(hit, held);
        }
    }

    /**
     * Phase 6b-2: the mouse is at ({@code x}, {@code y}) (GUI pixels) on the designer's screen; while the left
     * button holds the 3D preview, a move turns and tilts it. Called before {@link #editorPointer} and
     * {@link #editorPress}.
     */
    public void editorMouse(float x, float y) {
        if (takesInput() && modal == Modal.NONE && screen == Screen.EDITOR) {
            designer.mouse(x, y);
        }
    }

    /** Phase 6b: the mouse button went up; a painting stroke ends. */
    public void editorRelease() {
        if (designer != null) {
            designer.release();
        }
    }

    /**
     * The window's close button or Alt+F4 on the start screens (final review I1, decision 6): asks ARE YOU SURE? with
     * NO preselected like Quit and Esc on the main menu, on whatever screen is shown (a message or the Delete
     * confirmation gives way to it). A second request while it is open quits at once, as does a request while a
     * screen change is fading out or the flow is in the game (nothing to ask there; the game asks itself).
     */
    public void requestQuit() {
        if (modal == Modal.CONFIRM_QUIT || fade.isLeaving() || screen == Screen.LOADER || screen == Screen.GAME) {
            effects.addFirst(new Quit());
            return;
        }
        askQuit();
    }

    public void click(Hit hit) {
        if (!takesInput()) {
            return;
        }
        if (modal != Modal.NONE) {
            switch (hit.widget()) {
                case YES -> answer(true);
                case NO -> answer(false);
                case OK -> modal = Modal.NONE;
                default -> {
                }
            }
            return;
        }
        if (screen == Screen.EDITOR) {
            editorPress(Optional.of(hit), false);
            editorRelease();
            return;
        }
        Widget widget = hit.widget();
        if (screen == Screen.OPTIONS && optionsClick(hit)) {
            return;
        }
        if (screen == Screen.HALL_OF_FAME && widget == Widget.TAB) {
            hallOfFame.select(HallOfFame.Tab.values()[hit.row()]);
            return;
        }
        if (widget == Widget.NAME && screen == Screen.NEW_GAME) {
            focus = Widget.NAME;
        } else if (widget == Widget.LIST && screen == Screen.PLAY) {
            if (play.select(hit.row())) {
                focus = Widget.LIST;
            }
        } else if (widget == Widget.GRID && screen == Screen.OVERVIEW) {
            overview.select(hit.row());
            focus = Widget.GRID;
        } else if (widget == Widget.GRID && screen == Screen.TUTORIAL) {
            tutorial.select(hit.row());
            focus = Widget.GRID;
        } else if (isEnabled(widget)) {
            focus = widget;
            press(widget);
        }
    }

    /**
     * A double click (author decision 2026-10-12): on an Overview cell it selects the tower and starts it if it is
     * playable, on a Tutorial cell it starts the tower; on a row of the Play window it chooses the row (Phase 5c-1);
     * anywhere else it is a click.
     */
    public void doubleClick(Hit hit) {
        click(hit);
        if (!takesInput() || modal != Modal.NONE) {
            return;
        }
        if (screen == Screen.OVERVIEW && hit.widget() == Widget.GRID && hit.row() == overview.selected()) {
            activateOverview();
        } else if (screen == Screen.TUTORIAL && hit.widget() == Widget.GRID && hit.row() == tutorial.selected()) {
            activateTutorial();
        } else if (screen == Screen.PLAY && hit.widget() == Widget.LIST && hit.row() == play.selected()) {
            choose();
        }
    }

    // ------------------------------------------------------------------ state for the screens

    public Screen screen() {
        return screen;
    }

    public Modal modal() {
        return modal;
    }

    /** In a confirmation: whether YES (not NO) is highlighted. */
    public boolean modalYes() {
        return yes;
    }

    public String message() {
        return message;
    }

    /** The text key of the message box's caption: an error ({@code frame.error}) or a hint ({@code frame.notice}). */
    public String messageCaption() {
        return messageCaption;
    }

    public Widget focus() {
        return focus;
    }

    /** The black fade overlay's alpha. */
    public float fadeAlpha() {
        return fade.alpha();
    }

    /** Whether the screen fades out to black now (Phase 5c-2: the music follows the fade). */
    public boolean fadingOut() {
        return fade.isLeaving();
    }

    /** The screen a running fade-out leads to; empty while no fade-out runs and for Quit (Phase 5c-2). */
    public Optional<Screen> nextScreen() {
        return fade.isLeaving() ? Optional.ofNullable(leavingTo) : Optional.empty();
    }

    public float loadProgress() {
        return loadProgress;
    }

    /** Seconds since the flow started (blinking cursors). */
    public double clock() {
        return clock;
    }

    public CreditsTicker ticker() {
        return ticker;
    }

    public TextField name() {
        return name;
    }

    /** The Play window's list (Phase 5c-1). */
    public PlayModel play() {
        return play;
    }

    public OverviewModel overview() {
        return overview;
    }

    public BriefingText briefing() {
        return briefing;
    }

    public boolean practice() {
        return practice;
    }

    public float skyProgress() {
        return skyProgress;
    }

    public ResultText result() {
        return result;
    }

    public boolean retryAvailable() {
        return retry;
    }

    /**
     * Phase 5c-2 decision 6: the demo number (0-11) of the interlude behind the result window while that window is
     * shown, else empty.
     */
    public OptionalInt interlude() {
        return screen == Screen.RESULT && interlude >= 0 ? OptionalInt.of(interlude) : OptionalInt.empty();
    }

    /** The original's "Unbelievable! Amazing! …" lines after the last tower was solved, else empty. */
    public List<String> allSolvedLines() {
        return allSolved;
    }

    /** No profile and not the Tutorial: a developer start or a test play, whose result window leads to the menu. */
    public boolean isDeveloperSession() {
        return profile == null && !inTutorial;
    }

    public Optional<Profile> profile() {
        return Optional.ofNullable(profile);
    }

    public TowerDefinition tower() {
        return tower;
    }

    /** The Options window's state (Phase 5b). */
    public OptionsModel options() {
        return options;
    }

    /** The options the game uses now (Phase 5b). */
    public LiveSettings settings() {
        return settings;
    }

    /** The Hall of Fame window's state (Phase 5c-1). */
    public HallOfFame hallOfFame() {
        return hallOfFame;
    }

    /** The Hall of Fame's CREDITS tab scroll position ({@link HallOfFame#credits}). */
    public ScrollBox credits() {
        return hallOfFame.credits();
    }

    /**
     * The Hall of Fame's credits text, one entry per line; a line ending in ':' is a heading
     * ({@link HallOfFame#creditLines}).
     */
    public List<String> creditLines() {
        return hallOfFame.creditLines();
    }

    /**
     * Whether {@code widget} can be pressed: greyed menu entries, Play while the sky loads or on a locked Overview
     * cell, a group button the rules do not allow, Delete on "&lt; New Profile &gt;", and Retry outside a failed
     * result (Minor #4) cannot.
     */
    public boolean isEnabled(Widget widget) {
        return switch (widget) {
            case PLAY -> screen == Screen.BRIEFING ? skyProgress >= 1f
                    : screen != Screen.OVERVIEW || overview.playable();
            case PREVIOUS_GROUP -> screen == Screen.OVERVIEW && overview.canGoBack();
            case LIBRARY -> screen == Screen.OVERVIEW && libraries != null;
            case NEXT_GROUP -> screen == Screen.OVERVIEW && overview.canGoForward();
            case DELETE -> play.selectedProfile().isPresent();
            case RETRY -> retry && screen == Screen.RESULT;
            case MENU_DESIGNER -> libraries != null;
            case MENU_TUTORIAL -> tutorialTowers != null;
            default -> true;
        };
    }

    /** The result window's buttons, left to right: Retry (failure only) and Overview (Menu in a developer session). */
    public List<Widget> resultButtons() {
        return retry ? List.of(Widget.RETRY, Widget.OVERVIEW) : List.of(Widget.OVERVIEW);
    }

    // ------------------------------------------------------------------ screens

    private boolean takesInput() {
        return !fade.isLeaving() && screen != Screen.LOADER && screen != Screen.GAME;
    }

    private void change(Screen next) {
        if (!fade.isLeaving()) {
            // a change during a fade-out is ignored (ScreenFade.fadeOut), so is its target
            leavingTo = next;
        }
        fade.fadeOut(() -> enter(next));
    }

    private void enter(Screen next) {
        screen = next;
        modal = Modal.NONE;
        switch (next) {
            case MENU -> {
                focus = Widget.MENU_PLAY;
                inTutorial = false;
                leaveDesigner();
            }
            case PLAY -> {
                play = new PlayModel(store.list(), store.lastProfile());
                focus = Widget.LIST;
            }
            case NEW_GAME -> {
                name.clear();
                focus = Widget.NAME;
            }
            case OVERVIEW -> {
                // no own tower is played any more (Retry goes RESULT -> GAME directly, not through the Overview)
                customPlaying = null;
                // Phase 6d decision 5: the profile's last library (read afresh: the designer may have changed it)
                custom = profile.customLibrary().flatMap(this::openCustom).orElse(null);
                overview = new OverviewModel(campaign, profile, texts, Optional.ofNullable(custom));
                if (preselect != null) {
                    overview.preselect(preselect);
                    preselect = null;
                }
                focus = Widget.GRID;
            }
            case TUTORIAL -> {
                profile = null;
                inTutorial = true;
                tutorial = new TutorialModel(tutorialTowers, store.settings().read().solvedTutorials(), texts);
                if (preselect != null) {
                    tutorial.preselect(preselect);
                    preselect = null;
                }
                focus = Widget.GRID;
            }
            case BRIEFING -> {
                briefing = BriefingText.of(tower, texts);
                skyProgress = 0f;
                focus = Widget.PLAY;
                effects.add(new PrepareTower(tower));
            }
            case GAME -> effects.add(new StartTower(tower, practice));
            case OPTIONS -> {
                options.open();
                focus = Widget.TAB;
            }
            case HALL_OF_FAME -> {
                hallOfFame.open(store.list());
                focus = Widget.BACK;
            }
            case EDITOR -> {
                if (designer == null) {
                    designer = new TowerDesigner(libraries, texts, new Random(), towerIds);
                    // Phase 6d decision 8: this session's last library, else the one settings.json remembers if it
                    // still exists (a remembered library deleted since gives a fresh library, as before 6d).
                    // lastLibrary is null both before the first visit and after a visit that left an unsaved new
                    // library (File -> New); either way the last *saved or opened* library (settings.json) reopens -
                    // the unsaved one is discarded on leaving anyway.
                    Optional<String> remembered = store.settings().read().designerLibrary();
                    rememberedLibrary = remembered.orElse(null);
                    TowerLibraries.Entry last = lastLibrary != null ? lastLibrary
                            : remembered.flatMap(libraries::entry).orElse(null);
                    if (last != null) {
                        // Final review M3: reopen()'s Request is handled (not dropped), so a failure to reopen the
                        // last visit's library (the file deleted or corrupted meanwhile) shows as a message instead
                        // of silently leaving a fresh, unsaved library open.
                        handle(designer.reopen(last));
                    }
                }
            }
            default -> {
            }
        }
    }

    /** The designer is left to the main menu: its unsaved state is gone; its library opens again next time. */
    private void leaveDesigner() {
        if (designer != null) {
            EditorModel model = designer.model();
            lastLibrary = model.library().map(name -> new TowerLibraries.Entry(name, model.readOnly())).orElse(null);
            designer = null;
        }
    }

    /** What the designer asks of the flow; its open library is remembered first (Phase 6d decision 8). */
    private void handle(TowerDesigner.Request request) {
        rememberLibrary();
        switch (request) {
            case TowerDesigner.None none -> {
            }
            case TowerDesigner.Exit exit -> change(Screen.MENU);
            case TowerDesigner.Test play -> {
                test = play;
                customPlaying = null;
                tower = play.tower();
                practice = false;
                change(Screen.GAME);
            }
            case TowerDesigner.Confirm confirm -> {
                editorQuestion = confirm.title();
                modal = Modal.EDITOR_CONFIRM;
                yes = false;
            }
            case TowerDesigner.Message message -> show(message.text());
        }
    }

    /**
     * Phase 6d decision 8: the designer's open library, once it has another name than the remembered one (opened or
     * saved as), is written to settings.json, so the next session's designer opens it again; a failure is only logged
     * (and the write is tried again at the designer's next request).
     */
    private void rememberLibrary() {
        Optional<String> open = designer == null ? Optional.empty() : designer.model().library();
        if (open.isEmpty() || open.get().equals(rememberedLibrary)) {
            return;
        }
        try {
            store.settings().update(settings -> settings.withDesignerLibrary(open.get()));
            rememberedLibrary = open.get();
        } catch (IOException e) {
            LOG.warn("Failed to remember the designer's library '{}'", open.get(), e);
        }
    }

    private void optionsKey(UiKey key) {
        switch (options.key(key)) {
            case APPLY -> apply();
            case BACK -> back();
            case RESET -> askResetKeys();
            case NONE -> {
            }
        }
    }

    /** The Hall of Fame (decision 4): Left, Right and Tab switch the tab; the arrow and page keys scroll it. */
    private void hallOfFameKey(UiKey key) {
        ScrollBox box = hallOfFame.box();
        switch (key) {
            case LEFT, RIGHT, TAB -> hallOfFame.switchTab();
            case UP -> box.scrollBy(-1);
            case DOWN -> box.scrollBy(1);
            case PAGE_UP -> box.pageUp();
            case PAGE_DOWN -> box.pageDown();
            case HOME -> box.home();
            case END -> box.end();
            case ENTER, SPACE, ESCAPE -> back();
            default -> {
            }
        }
    }

    /** A click on the Options window: its own widgets; Back is the common one. */
    private boolean optionsClick(Hit hit) {
        switch (hit.widget()) {
            case TAB -> options.selectTab(OptionsModel.Tab.values()[hit.row()]);
            case OPTION_MINUS -> options.change(OptionsModel.Option.values()[hit.row()], -1);
            case OPTION_PLUS -> options.change(OptionsModel.Option.values()[hit.row()], 1);
            case OPTION_SWITCH -> options.change(OptionsModel.Option.values()[hit.row()], 0);
            case KEY_ROW -> options.clickKey(hit.row());
            case KEYS_RESET -> {
                options.focus(OptionsModel.Focus.RESET);
                askResetKeys();
            }
            case APPLY -> {
                options.focus(OptionsModel.Focus.APPLY);
                apply();
            }
            case BACK -> {
                options.focus(OptionsModel.Focus.BACK);
                back();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Apply (decision 1): the options are saved and the menu follows; a failed save is shown, the window stays. */
    private void apply() {
        try {
            settings.save();
        } catch (IOException e) {
            LOG.warn("Failed to save the options", e);
            show(texts.format("options.error.save", e.getMessage()));
            return;
        }
        change(Screen.MENU);
    }

    private void askResetKeys() {
        modal = Modal.CONFIRM_RESET_KEYS;
        yes = false;
    }

    private void menuKey(UiKey key) {
        switch (key) {
            case UP -> focus = step(MENU, -1);
            case DOWN -> focus = step(MENU, 1);
            case ENTER, SPACE -> press(focus);
            case ESCAPE -> askQuit();
            default -> {
            }
        }
    }

    private void newGameKey(UiKey key) {
        List<Widget> order = List.of(Widget.NAME, Widget.START, Widget.BACK);
        switch (key) {
            case TAB -> focus = cycle(order);
            case ENTER -> {
                if (focus == Widget.NAME) {
                    focus = Widget.START;
                } else {
                    press(focus);
                }
            }
            case SPACE -> {
                if (focus != Widget.NAME) {
                    press(focus);
                }
            }
            case BACKSPACE -> {
                if (focus == Widget.NAME) {
                    name.backspace();
                }
            }
            case LEFT, RIGHT -> {
                if (focus != Widget.NAME) {
                    focus = focus == Widget.START ? Widget.BACK : Widget.START;
                }
            }
            case ESCAPE -> back();
            default -> {
            }
        }
    }

    /**
     * The Play window (Phase 5c-1 decision 2): Up/Down move in the list (over a greyed "&lt; New Profile &gt;"),
     * Enter or Space on the list choose the selected row at once, on a button press it; Delete asks first.
     */
    private void playKey(UiKey key) {
        List<Widget> buttons = List.of(Widget.START, Widget.DELETE, Widget.BACK);
        switch (key) {
            case UP, DOWN -> {
                if (focus == Widget.LIST) {
                    play.move(key == UiKey.UP ? -1 : 1);
                }
            }
            case TAB -> focus = cycle(List.of(Widget.LIST, Widget.START, Widget.DELETE, Widget.BACK));
            case LEFT, RIGHT -> {
                if (buttons.contains(focus)) {
                    focus = step(buttons, key == UiKey.LEFT ? -1 : 1);
                }
            }
            case ENTER, SPACE -> {
                if (focus == Widget.LIST) {
                    choose();
                } else {
                    press(focus);
                }
            }
            case DELETE -> askDelete();
            case ESCAPE -> back();
            default -> {
            }
        }
    }

    /**
     * The Overview and the Tutorial (3×3 grid, author decision 2026-10-12): on the grid the arrow keys move the
     * selection without wrapping; on a button Left/Right move between the buttons and Up/Down return to the grid.
     * Enter or Space on the grid play the selected tower (if playable), on a button press it; Tab follows
     * {@code order}.
     */
    private void gridKey(UiKey key, List<Widget> order) {
        boolean onGrid = focus == Widget.GRID;
        switch (key) {
            case UP, DOWN -> {
                if (onGrid) {
                    moveSelection(key);
                } else {
                    focus = Widget.GRID;
                }
            }
            case LEFT, RIGHT -> {
                if (onGrid) {
                    moveSelection(key);
                } else {
                    focus = step(order.subList(1, order.size()), key == UiKey.LEFT ? -1 : 1);
                }
            }
            case TAB -> focus = cycle(order);
            case ENTER, SPACE -> {
                if (onGrid) {
                    activateGrid();
                } else {
                    press(focus);
                }
            }
            case ESCAPE -> back();
            default -> {
            }
        }
    }

    private void moveSelection(UiKey key) {
        if (screen == Screen.TUTORIAL) {
            switch (key) {
                case UP -> tutorial.up();
                case DOWN -> tutorial.down();
                case LEFT -> tutorial.left();
                default -> tutorial.right();
            }
            return;
        }
        switch (key) {
            case UP -> overview.up();
            case DOWN -> overview.down();
            case LEFT -> overview.left();
            default -> overview.right();
        }
    }

    private void buttonsKey(UiKey key, List<Widget> buttons) {
        switch (key) {
            case LEFT, UP -> focus = step(buttons, -1);
            case RIGHT, DOWN, TAB -> focus = key == UiKey.TAB ? cycle(buttons) : step(buttons, 1);
            case ENTER, SPACE -> press(focus);
            case ESCAPE -> back();
            default -> {
            }
        }
    }

    private void modalKey(UiKey key) {
        switch (key) {
            case LEFT, RIGHT, UP, DOWN, TAB -> yes = !yes;
            case ENTER, SPACE -> {
                if (modal == Modal.MESSAGE) {
                    modal = Modal.NONE;
                } else {
                    answer(yes);
                }
            }
            case ESCAPE -> {
                if (modal == Modal.MESSAGE) {
                    modal = Modal.NONE;
                } else {
                    answer(false);
                }
            }
            default -> {
            }
        }
    }

    private void press(Widget widget) {
        if (!isEnabled(widget)) {
            return;
        }
        switch (widget) {
            case MENU_PLAY -> change(Screen.PLAY);
            case MENU_TUTORIAL -> change(Screen.TUTORIAL);
            case MENU_OPTIONS -> change(Screen.OPTIONS);
            case MENU_HALL_OF_FAME -> change(Screen.HALL_OF_FAME);
            case MENU_DESIGNER -> change(Screen.EDITOR);
            case MENU_QUIT -> askQuit();
            case START -> {
                if (screen == Screen.NEW_GAME) {
                    createProfile();
                } else {
                    choose();
                }
            }
            case DELETE -> askDelete();
            case PREVIOUS_GROUP -> changeGroup(overview.back());
            case NEXT_GROUP -> changeGroup(overview.forward());
            case LIBRARY -> switchLibrary();
            case PLAY -> {
                if (screen == Screen.OVERVIEW || screen == Screen.TUTORIAL) {
                    activateGrid();
                } else {
                    startAttempt();
                }
            }
            case BACK -> back();
            case OVERVIEW -> leaveResult();
            case RETRY -> retryAttempt();
            default -> {
            }
        }
    }

    private void back() {
        switch (screen) {
            case NEW_GAME -> change(Screen.PLAY);
            case PLAY, OVERVIEW, TUTORIAL, HALL_OF_FAME -> change(Screen.MENU);
            case OPTIONS -> {
                // decision 1: Back restores the values from before the window was opened; the fading window keeps
                // showing the values it was left with (acceptance 2026-10-14)
                options.leave();
                settings.revert();
                change(Screen.MENU);
            }
            case BRIEFING -> {
                preselect = tower.id();
                change(inTutorial ? Screen.TUTORIAL : Screen.OVERVIEW);
            }
            case RESULT -> leaveResult();
            default -> {
            }
        }
    }

    private void askQuit() {
        modal = Modal.CONFIRM_QUIT;
        yes = false;
    }

    private void askDelete() {
        if (play.selectedProfile().isPresent()) {
            modal = Modal.CONFIRM_DELETE;
            yes = false;
        }
    }

    private void answer(boolean confirmed) {
        Modal asked = modal;
        modal = Modal.NONE;
        if (asked == Modal.EDITOR_CONFIRM) {
            handle(designer.answer(confirmed));
            return;
        }
        if (!confirmed) {
            return;
        }
        if (asked == Modal.CONFIRM_QUIT) {
            leavingTo = null;
            fade.fadeOut(() -> effects.add(new Quit()));
        } else if (asked == Modal.CONFIRM_RESET_KEYS) {
            options.resetKeys();
        } else if (asked == Modal.CONFIRM_DELETE) {
            String deleted = play.selectedProfile().orElseThrow().name();
            int row = play.selected();
            try {
                store.delete(deleted);
            } catch (IOException e) {
                LOG.warn("Failed to delete the profile '{}'", deleted, e);
                show(texts.format("play.error.delete", e.getMessage()));
            }
            play = new PlayModel(store.list(), Optional.empty());
            play.select(Math.min(row, play.rows() - 1));
            if (!isEnabled(focus)) {
                // Minor #5: deleting the last profile must not leave the focus stuck on the now-disabled DELETE.
                focus = Widget.BACK;
            }
        }
    }

    private void show(String text) {
        message = text;
        messageCaption = "frame.error";
        modal = Modal.MESSAGE;
    }

    /** A hint, not an error (the refusal of an own tower, no own library): the box's caption is neutral. */
    private void inform(String text) {
        show(text);
        messageCaption = "frame.notice";
    }

    private void createProfile() {
        String entered = name.name();
        if (entered.isEmpty()) {
            show(texts.get("new.error.empty"));
            return;
        }
        if (store.full()) {
            show(texts.get("play.full"));
            return;
        }
        if (store.exists(entered)) {
            show(texts.get("new.error.exists"));
            return;
        }
        profile = Profile.create(entered, LIBRARY);
        try {
            store.save(profile);
            store.rememberLast(profile.name());
        } catch (IOException e) {
            LOG.warn("Failed to save the new profile '{}'", entered, e);
            show(texts.format("new.error.save", e.getMessage()));
            profile = null;
            return;
        }
        change(Screen.OVERVIEW);
    }

    /** The Play window's row is chosen: "&lt; New Profile &gt;" opens the name entry, a profile its Overview. */
    private void choose() {
        Optional<Profile> chosen = play.selectedProfile();
        if (chosen.isEmpty()) {
            if (play.newProfileEnabled()) {
                change(Screen.NEW_GAME);
            }
            return;
        }
        try {
            store.rememberLast(chosen.get().name());
        } catch (IOException e) {
            LOG.warn("Failed to remember the last profile '{}'", chosen.get().name(), e);
            show(texts.format("play.error.remember", e.getMessage()));
            return;
        }
        profile = chosen.get();
        change(Screen.OVERVIEW);
    }

    private void activateOverview() {
        overviewAction(overview.activate());
    }

    private void activateGrid() {
        if (screen == Screen.TUTORIAL) {
            activateTutorial();
        } else {
            activateOverview();
        }
    }

    /** Decision 3: the selected tutorial tower's Briefing; every tutorial tower is open, none is practice. */
    private void activateTutorial() {
        tower = tutorial.selectedTower();
        practice = false;
        customPlaying = null;
        change(Screen.BRIEFING);
    }

    /** A tower with a goal: klondikes to collect or robots to destroy ("PRACTICE TOWER - NO GOAL" otherwise). */
    private static boolean hasGoal(TowerDefinition definition) {
        return definition.conditions().klondikes() > 0 || definition.conditions().robots() > 0;
    }

    /**
     * Decision 3: a completed (or, goal-less, played) tutorial tower is marked in settings.json; a failure shows over
     * the result window. Defence in depth (author's guard 2026-10-15): only a {@code tutorial-} tower played without
     * a profile can enter the global list, whatever the Tutorial flag says.
     */
    private void solveTutorial(String towerId) {
        if (profile != null || !towerId.startsWith("tutorial-")) {
            LOG.warn("Not marked as a solved tutorial tower: {} (profile: {})", towerId, profile != null);
            return;
        }
        try {
            store.settings().update(settings -> settings.withSolvedTutorial(towerId));
        } catch (IOException e) {
            LOG.warn("Failed to save the solved tutorial tower {}", towerId, e);
            pendingSaveError = texts.format("tutorial.error.save", e.getMessage());
        }
    }

    /** A group button: the group changes and is saved; a button the change disabled gives the focus to the grid. */
    private void changeGroup(OverviewModel.Action action) {
        overviewAction(action);
        if (!isEnabled(focus)) {
            focus = Widget.GRID;
        }
    }

    private void overviewAction(OverviewModel.Action action) {
        switch (action) {
            case OverviewModel.Play play -> {
                tower = play.tower();
                practice = play.practice();
                customPlaying = custom == null ? null : custom.name();
                change(Screen.BRIEFING);
            }
            case OverviewModel.GroupChanged changed -> {
                profile = changed.profile();
                save();
                flushPendingSaveError();
            }
            case OverviewModel.Refused refused -> inform(refused.message());
            case OverviewModel.Nothing nothing -> {
            }
        }
    }

    /**
     * Saves the attempt start and enters GAME. A save failure ({@link #save()}) is not shown now: {@link #change}
     * would wipe it before the player could read it, and the game must start regardless (review Important #1); it is
     * deferred and shown at the latest once the attempt ends.
     */
    private void startAttempt() {
        if (profile != null && !practice && customPlaying == null) {
            profile = profile.started(tower.id());
            save();
        }
        change(Screen.GAME);
    }

    private void retryAttempt() {
        startAttempt();
    }

    /**
     * Phase 6d decision 2: the Library button steps from the original towers through the own libraries of
     * {@code <userdir>/towers/} (by name) and back to the original towers; a library that cannot be read or has no
     * tower is skipped (logged). The choice is remembered in the profile (decision 5) and saved; the grid changes in
     * place (no fade), the focus stays on the button.
     */
    private void switchLibrary() {
        List<String> names;
        try {
            names = libraries.list().stream().filter(entry -> !entry.readOnly()).map(TowerLibraries.Entry::name)
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot list the own tower libraries", e);
            names = List.of();
        }
        if (names.isEmpty()) {
            // the shown library's file may have been deleted since: back to the original towers first
            if (custom != null) {
                custom = null;
                profile = profile.withCustomLibrary(Optional.empty());
                save();
                overview = new OverviewModel(campaign, profile, texts, Optional.empty());
            }
            informOrSaveError(texts.get("overview.library.none"));
            return;
        }
        CustomLibrary next = null;
        for (int i = custom == null ? 0 : names.indexOf(custom.name()) + 1; i < names.size() && next == null; i++) {
            next = openCustom(names.get(i)).orElse(null);
        }
        if (next == null && custom == null) {
            inform(texts.get("overview.library.unreadable"));
            return;
        }
        custom = next;
        profile = profile.withCustomLibrary(Optional.ofNullable(next).map(CustomLibrary::name));
        save();
        flushPendingSaveError();
        overview = new OverviewModel(campaign, profile, texts, Optional.ofNullable(custom));
    }

    /** A hint; a save error that is pending (the profile could not be saved just now) takes precedence over it. */
    private void informOrSaveError(String text) {
        if (pendingSaveError != null) {
            flushPendingSaveError();
        } else {
            inform(text);
        }
    }

    /** Phase 6d: the own library {@code name} for the Overview, if it can be read and has a tower (else logged). */
    private Optional<CustomLibrary> openCustom(String name) {
        if (libraries == null || !TowerLibraries.validName(name)) {
            return Optional.empty();
        }
        try {
            TowerLibraries.Opened opened = libraries.open(new TowerLibraries.Entry(name, false));
            if (!opened.towers().isEmpty()) {
                return Optional.of(new CustomLibrary(name, opened.towers()));
            }
            LOG.warn("The own tower library '{}' has no tower to play", name);
        } catch (IOException e) {
            // an unreadable or vanished file (TowerFormatException, NoSuchFileException): the reason is enough
            LOG.warn("Cannot open the own tower library '{}': {}", name, e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("Cannot open the own tower library '{}'", name, e);
        }
        return Optional.empty();
    }

    /**
     * Phase 6d: the 1-based position of {@code towerId} in the own library whose tower is played (the title bar's
     * number), else empty.
     */
    public OptionalInt customNumber(String towerId) {
        if (customPlaying == null || custom == null || !custom.name().equals(customPlaying)) {
            return OptionalInt.empty();
        }
        int position = custom.position(towerId);
        return position < 0 ? OptionalInt.empty() : OptionalInt.of(position + 1);
    }

    private void leaveResult() {
        change(inTutorial ? Screen.TUTORIAL : profile == null ? Screen.MENU : Screen.OVERVIEW);
    }

    /**
     * Saves the profile. A failure is logged and kept as {@link #pendingSaveError} rather than shown at once: the
     * caller may be about to change the screen (tower start, F9/pause restart), which would clear a MESSAGE modal
     * before the player ever saw it (review Important #1). {@link #flushPendingSaveError()} shows it once a screen is
     * reached where it survives, at the latest when the attempt ends.
     */
    private void save() {
        try {
            store.save(profile);
        } catch (IOException e) {
            LOG.warn("Failed to save the profile '{}'", profile.name(), e);
            pendingSaveError = texts.format("new.error.save", e.getMessage());
        }
    }

    /** Shows a deferred save error as MESSAGE, or clears the modal if there is none. */
    private void flushPendingSaveError() {
        if (pendingSaveError != null) {
            show(pendingSaveError);
            pendingSaveError = null;
        } else {
            modal = Modal.NONE;
        }
    }

    /** The next enabled widget of {@code order} in {@code direction} from the focus, stopping at the ends. */
    private Widget step(List<Widget> order, int direction) {
        int index = order.indexOf(focus);
        for (int i = index + direction; i >= 0 && i < order.size(); i += direction) {
            if (isEnabled(order.get(i))) {
                return order.get(i);
            }
        }
        return index < 0 ? order.get(0) : focus;
    }

    /** Tab: the next enabled widget of {@code order}, wrapping. */
    private Widget cycle(List<Widget> order) {
        int index = order.indexOf(focus);
        for (int i = 1; i <= order.size(); i++) {
            Widget next = order.get(Math.floorMod(index + i, order.size()));
            if (isEnabled(next)) {
                return next;
            }
        }
        return focus;
    }
}
