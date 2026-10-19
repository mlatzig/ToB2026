package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.input.FrontEndInput;
import de.latzig.game.tob.client.logic.audio.SoundTuning;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.HallOfFame;
import de.latzig.game.tob.client.logic.frontend.LiveSettings;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.frontend.ResultText;
import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.KeyChord;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PanelSize;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.game.SoftlySoftlySolutionTest;
import de.latzig.game.tob.client.logic.ui.ScrollBox;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.progress.TowerProgress;
import de.latzig.game.tob.core.sim.ObjectStatus;
import de.latzig.game.tob.core.sim.SpiderId;

/**
 * Phase 5a spec §4 (headless): loader → main menu → Play → "&lt; New Profile &gt;" (Phase 5c-1) → "Smoke" →
 * Overview → Briefing (waiting for the sky) → the first tower → F10, YES → the result window "Attempt
 * Abandoned" → Overview; the profile file is written to the temporary user folder, and the menu
 * background plays its demo script meanwhile.
 * <p>
 * Final review fix wave: the window's close button (I1) on the main menu and in a game, with a profile and in a
 * developer start; typing and clicking through the real input path while the cursor rests on a button (I2, M9); and
 * the end screen's 3 s (or a key) leading to "Tower Completed" with the survivor bonus saved (M8).
 */
class GameFlowSmokeTest {

    private static final int UNTHROTTLED = Integer.MAX_VALUE;

    private static final class Player extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<GameFlow.Screen> screens = new ArrayList<>();
        private boolean playPressed;
        private boolean newProfile;
        private boolean named;
        private boolean chosen;
        private boolean started;
        private boolean abandoned;
        private boolean resultLeft;
        private volatile String reason;
        private volatile String tower;
        private volatile boolean backwardsAllowed;
        private volatile String preparedSky;
        private volatile Boolean moonReadyAtStart;
        private volatile int demoSteps;
        /** Phase 5c-2: "SCREEN:SONG" as seen every frame (the music follows one frame later), the interlude. */
        private final Set<String> music = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        private volatile int interlude = -1;

        @Override
        protected void initialize(Application app) {
        }

        @Override
        protected void cleanup(Application app) {
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }

        @Override
        public void update(float tpf) {
            GameFlow flow = ((TobApp) getApplication()).flow();
            if (flow == null || done.getCount() == 0) {
                return;
            }
            if (screens.isEmpty() || screens.get(screens.size() - 1) != flow.screen()) {
                screens.add(flow.screen());
            }
            MenuBackgroundState background = getState(MenuBackgroundState.class);
            if (background != null && background.player() != null) {
                demoSteps = Math.max(demoSteps, background.player().step());
            }
            TobApp app = (TobApp) getApplication();
            music.add(flow.screen() + ":" + app.music().song().map(Enum::name).orElse("none"));
            if (flow.screen() == GameFlow.Screen.RESULT && background != null && background.isEnabled()
                    && background.player() != null) {
                interlude = background.interlude();
            }
            switch (flow.screen()) {
                case MENU -> {
                    if (!playPressed) {
                        playPressed = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case PLAY -> {
                    if (!newProfile) {
                        newProfile = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case NEW_GAME -> {
                    if (!named) {
                        named = true;
                        for (char c : "Smoke".toCharArray()) {
                            flow.typed(c);
                        }
                        flow.key(UiKey.ENTER);
                        flow.key(UiKey.ENTER);
                    }
                }
                case OVERVIEW -> {
                    if (!chosen) {
                        chosen = true;
                        flow.key(UiKey.ENTER);
                    } else if (resultLeft) {
                        done.countDown();
                    }
                }
                case BRIEFING -> {
                    if (!started && flow.isEnabled(GameFlow.Widget.PLAY)) {
                        started = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case GAME -> {
                    GameState game = getState(GameState.class);
                    if (moonReadyAtStart == null && game != null && game.driver() != null
                            && game.driver().ticks() >= 1) {
                        // after the game's first update (one simulation step per render frame in fast-forward)
                        moonReadyAtStart = game.moonReady();
                    }
                    if (!abandoned && game != null && game.driver() != null && game.driver().ticks() >= 5) {
                        abandoned = true;
                        tower = game.tower().id();
                        preparedSky = game.preparedSky().map(sky -> sky.towerId() + (sky.isDone() ? " done" : ""))
                                .orElse("none");
                        backwardsAllowed = game.options().backwardsAllowed();
                        game.onAction(GameAction.ABANDON_TOWER);
                        game.onAction(GameAction.TURN_LEFT);
                        game.onAction(GameAction.MENU_CONFIRM);
                    }
                }
                case RESULT -> {
                    if (!resultLeft) {
                        resultLeft = true;
                        reason = flow.result().reason();
                        flow.key(UiKey.ENTER);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static Path assumeAssets() {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        return assets;
    }

    /** Runs {@code app} headless until {@code done} (at most 60 s), then stops it. */
    private static boolean run(TobApp app, CountDownLatch done, String name) throws InterruptedException {
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();
        app.start(JmeContext.Type.Headless);
        boolean finished = done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("GameFlowSmokeTest: " + name + " in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        return finished;
    }

    /** A state that only watches; subclasses act in {@link #update}. */
    private abstract static class Driver extends BaseAppState {

        final CountDownLatch done = new CountDownLatch(1);

        @Override
        protected void initialize(Application app) {
        }

        @Override
        protected void cleanup(Application app) {
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }

        TobApp app() {
            return (TobApp) getApplication();
        }
    }

    /**
     * Final review I1, I2 and M9: on the main menu the window's X asks QUIT? (NO preselected, NO keeps the menu); New
     * Game is typed and clicked through {@link FrontEndInput} with the cursor resting on Start, which must not take
     * the name field's focus; in the game the X pauses and asks QUIT? (NO preselected), NO resumes the game,
     * and the next X asks again; YES ends the attempt as abandoned, saves the +2 and quits.
     */
    private static final class CloseDriver extends Driver {

        private int step;
        private volatile boolean menuAskedWithNo;
        private volatile boolean menuKeptAfterNo;
        private volatile String typedWhileHovering;
        private volatile GameFlow.Widget focusWhileHovering;
        private volatile boolean gameAskedQuitWithNo;
        private volatile boolean noResumedTheGame;
        private volatile boolean askedAgain;
        private volatile boolean quitting;
        private volatile GameFlow.Screen screenAfterQuit;

        @Override
        public void update(float tpf) {
            GameFlow flow = app().flow();
            if (flow == null || done.getCount() == 0) {
                return;
            }
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && flow.fadeAlpha() == 0f) {
                        app().requestClose(false);
                        menuAskedWithNo = flow.modal() == GameFlow.Modal.CONFIRM_QUIT && !flow.modalYes();
                        flow.key(UiKey.ESCAPE);
                        menuKeptAfterNo = flow.modal() == GameFlow.Modal.NONE && !app().isQuitting();
                        flow.key(UiKey.ENTER);
                        step++;
                    }
                }
                case 1 -> newGameByInput(flow);
                case 2 -> {
                    if (flow.screen() == GameFlow.Screen.OVERVIEW) {
                        flow.key(UiKey.ENTER);
                        step++;
                    }
                }
                case 3 -> {
                    if (flow.screen() == GameFlow.Screen.BRIEFING && flow.isEnabled(GameFlow.Widget.PLAY)) {
                        flow.key(UiKey.ENTER);
                        step++;
                    }
                }
                case 4 -> closeInGame(flow);
                default -> {
                }
            }
        }

        private void newGameByInput(GameFlow flow) {
            if (flow.screen() == GameFlow.Screen.PLAY && flow.fadeAlpha() == 0f) {
                // Phase 5c-1: "< New Profile >" is preselected in the empty Play window
                flow.key(UiKey.ENTER);
                return;
            }
            FrontEndState frontEnd = getState(FrontEndState.class);
            if (flow.screen() != GameFlow.Screen.NEW_GAME || frontEnd == null || frontEnd.frame() == null
                    || frontEnd.frame().areas().stream().noneMatch(a -> a.hit().widget() == GameFlow.Widget.START)) {
                return;
            }
            PanelLayout.Rect start = frontEnd.frame().areas().stream()
                    .filter(a -> a.hit().widget() == GameFlow.Widget.START).findFirst().orElseThrow().rect();
            int x = Math.round(start.x() + start.width() / 2f);
            int y = Math.round(start.y() + start.height() / 2f);
            FrontEndInput input = frontEnd.input();
            input.onMouseMotionEvent(new MouseMotionEvent(x, y, 1, 1, 0, 0));
            for (char c : "Quit".toCharArray()) {
                input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, c, true, false));
                input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, c, false, false));
            }
            typedWhileHovering = flow.name().text();
            focusWhileHovering = flow.focus();
            input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, x, y));
            input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, x, y));
            step++;
        }

        private void closeInGame(GameFlow flow) {
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || game.driver().ticks() < 5) {
                return;
            }
            app().requestClose(false);
            PauseMenu menu = game.menu();
            gameAskedQuitWithNo = game.driver().isPaused() && menu.level() == PauseMenu.Level.CONFIRM
                    && menu.choice() == PauseMenu.Choice.QUIT && !menu.yesHighlighted();
            game.onAction(GameAction.MENU_CONFIRM);
            noResumedTheGame = menu.level() == PauseMenu.Level.CLOSED && !game.driver().isPaused()
                    && !app().isQuitting();
            app().requestClose(false);
            askedAgain = menu.level() == PauseMenu.Level.CONFIRM && menu.choice() == PauseMenu.Choice.QUIT;
            game.onAction(GameAction.TURN_LEFT);
            game.onAction(GameAction.MENU_CONFIRM);
            quitting = app().isQuitting();
            screenAfterQuit = flow.screen();
            step++;
            done.countDown();
        }
    }

    @Test
    void theWindowCloseAsksFirstAndAConfirmedQuitInAGameIsSavedAsAnAbandon(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = assumeAssets();
        CloseDriver driver = new CloseDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);

        boolean finished = run(app, driver.done, "window close, menu and game");

        assertTrue(finished, "the flow within 60 s");
        assertEquals(List.of(), app.errors());
        assertTrue(driver.menuAskedWithNo, "the menu asks first, NO preselected");
        assertTrue(driver.menuKeptAfterNo, "NO keeps the menu");
        assertEquals("Quit", driver.typedWhileHovering, "I2: typing works with the cursor resting on Start");
        assertEquals(GameFlow.Widget.NAME, driver.focusWhileHovering, "hovering does not take the focus");
        assertTrue(driver.gameAskedQuitWithNo, "the game pauses and asks QUIT? with NO preselected");
        assertTrue(driver.noResumedTheGame, "NO resumes the game (acceptance 2026-10-13)");
        assertTrue(driver.askedAgain, "a close after NO asks again");
        assertTrue(driver.quitting, "YES quits");
        assertEquals(GameFlow.Screen.RESULT, driver.screenAfterQuit, "the attempt was ended before the app stops");
        TowerProgress progress = new ProfileStore(userDir).find("Quit").orElseThrow().progress("original-000");
        assertEquals(1, progress.attempts());
        assertEquals(2, progress.counter(), "closing the window costs the abandon's +2 (S:6771-6773)");
    }

    /**
     * Phase 5b §4 (headless): main menu → Options by mouse; Master +2 by keys; on KEYS a click on Fire's row and
     * Ctrl+F11 through the raw input; the wheel scrolls the list; Apply → menu. Then the Hall of Fame by mouse
     * (Phase 5c-1), its CREDITS tab by mouse; the scroll bar's thumb is dragged to the bottom; Esc → menu.
     */
    private static final class OptionsByInputDriver extends Driver {

        private int step;
        private volatile int masterPreview;
        private volatile int keyListFirst = -1;
        private volatile int creditsFirst = -1;
        private volatile int creditsLast = -2;

        @Override
        public void update(float tpf) {
            GameFlow flow = app().flow();
            FrontEndState frontEnd = getState(FrontEndState.class);
            if (flow == null || frontEnd == null || frontEnd.frame() == null || done.getCount() == 0
                    || flow.fadeAlpha() > 0f) {
                return;
            }
            FrontEndInput input = frontEnd.input();
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU
                            && click(frontEnd, Hit.of(GameFlow.Widget.MENU_OPTIONS))) {
                        step++;
                    }
                }
                case 1 -> {
                    if (flow.screen() == GameFlow.Screen.OPTIONS) {
                        for (int key : new int[] {KeyInput.KEY_DOWN, KeyInput.KEY_RIGHT, KeyInput.KEY_RIGHT}) {
                            input.onKeyEvent(new KeyInputEvent(key, '\0', true, false));
                            input.onKeyEvent(new KeyInputEvent(key, '\0', false, false));
                        }
                        masterPreview = app().settings().current().masterVolume();
                        flow.click(new Hit(GameFlow.Widget.TAB, 3));
                        step++;
                    }
                }
                case 2 -> {
                    int fire = flow.options().actions().indexOf(GameAction.FIRE);
                    // Author's extra (2026-10-14): the "Hide Mouse Cursor" switch took one row from the CONTROLS
                    // tab's key list (OptionsModel.KEY_ROWS 5 -> 4), so FIRE's row (index 4) is no longer visible
                    // without scrolling first.
                    flow.options().keyList().show(fire);
                    if (click(frontEnd, new Hit(GameFlow.Widget.KEY_ROW, fire))) {
                        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', true, false));
                        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_F11, '\0', true, false));
                        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_F11, '\0', false, false));
                        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL, '\0', false, false));
                        // back to the top first, so the wheel scroll below starts from the same baseline as before
                        // the author's extra scrolled to FIRE's row
                        flow.options().keyList().home();
                        input.onMouseMotionEvent(new MouseMotionEvent(10, 10, 0, 0, -120, -120));
                        keyListFirst = flow.options().keyList().first();
                        step++;
                    }
                }
                case 3 -> {
                    if (click(frontEnd, Hit.of(GameFlow.Widget.APPLY))) {
                        step++;
                    }
                }
                case 4 -> {
                    if (flow.screen() == GameFlow.Screen.MENU
                            && click(frontEnd, Hit.of(GameFlow.Widget.MENU_HALL_OF_FAME))) {
                        step++;
                    }
                }
                case 5 -> {
                    if (flow.screen() == GameFlow.Screen.HALL_OF_FAME
                            && click(frontEnd, new Hit(GameFlow.Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()))) {
                        step++;
                    }
                }
                case 6 -> {
                    PanelLayout.Rect track = area(frontEnd, Hit.of(GameFlow.Widget.SCROLL_TRACK));
                    if (flow.hallOfFame().tab() == HallOfFame.Tab.CREDITS && track != null) {
                        creditsFirst = flow.credits().first();
                        int x = Math.round(track.x() + track.width() / 2f);
                        int top = Math.round(track.y() + track.height() - 8f);
                        input.onMouseMotionEvent(new MouseMotionEvent(x, top, 0, 0, 0, 0));
                        input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, x, top));
                        input.onMouseMotionEvent(new MouseMotionEvent(x, Math.round(track.y()), 0, -top, 0, 0));
                        input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, x,
                                Math.round(track.y())));
                        creditsLast = flow.credits().first() == flow.credits().maxFirst() ? flow.credits().first()
                                : -flow.credits().first();
                        input.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', true, false));
                        step++;
                    }
                }
                case 7 -> {
                    if (flow.screen() == GameFlow.Screen.MENU) {
                        done.countDown();
                    }
                }
                default -> {
                }
            }
        }

        private static PanelLayout.Rect area(FrontEndState frontEnd, Hit hit) {
            return frontEnd.frame().areas().stream().filter(a -> a.hit().equals(hit)).findFirst()
                    .map(StartScreens.Area::rect).orElse(null);
        }

        /** A left click through the input on {@code hit}'s area, if the last frame shows it. */
        private static boolean click(FrontEndState frontEnd, Hit hit) {
            PanelLayout.Rect rect = area(frontEnd, hit);
            if (rect == null) {
                return false;
            }
            int x = Math.round(rect.x() + rect.width() / 2f);
            int y = Math.round(rect.y() + rect.height() / 2f);
            frontEnd.input().onMouseMotionEvent(new MouseMotionEvent(x, y, 0, 0, 0, 0));
            frontEnd.input().onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, x, y));
            frontEnd.input().onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, x, y));
            return true;
        }
    }

    @Test
    void theOptionsAndTheCreditsWorkThroughTheRealInput(@TempDir Path userDir) throws InterruptedException {
        Path assets = assumeAssets();
        OptionsByInputDriver driver = new OptionsByInputDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);

        boolean finished = run(app, driver.done, "options and credits by input");

        assertTrue(finished, "within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(120, driver.masterPreview, "previewed at once");
        assertEquals(ScrollBox.WHEEL_ROWS, driver.keyListFirst, "the wheel scrolled the KEYS list");
        Settings saved = LiveSettings.load(new ProfileStore(userDir).settings()).saved();
        assertEquals(120, saved.masterVolume(), "Apply saved; a fresh start reads it back");
        assertEquals(Map.of(GameAction.FIRE, KeyChord.ctrl(KeyInput.KEY_F11)), saved.keys(), "Ctrl+F11 captured");
        assertEquals(0, driver.creditsFirst);
        assertTrue(driver.creditsLast > 0, "the thumb dragged to the bottom shows the last lines: "
                + driver.creditsLast);
    }

    /** Phase 5b decision 4: the attempt takes Backwards Allowed from the global settings, here a developer start. */
    private static final class OptionsDriver extends Driver {

        private volatile Boolean backwardsAllowed;
        private volatile int soundDetail = -1;
        private volatile float sunRed = -1f;
        private volatile float ownSunRed = -1f;
        private volatile PanelSize panelSize;
        private volatile Optional<GameAction> f11;

        @Override
        public void update(float tpf) {
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null || game.driver().ticks() < 1) {
                return;
            }
            backwardsAllowed = game.options().backwardsAllowed();
            soundDetail = game.cues().detail();
            sunRed = game.sunColor().r;
            ownSunRed = game.ownSunColor().r;
            panelSize = game.panelSize();
            f11 = game.bindings().lookup(KeyInput.KEY_F11, false);
            done.countDown();
        }
    }

    @Test
    void backwardsAllowedComesFromTheSettings(@TempDir Path userDir) throws Exception {
        Path assets = assumeAssets();
        LiveSettings saved = LiveSettings.load(new ProfileStore(userDir).settings());
        saved.preview(Settings.DEFAULTS.withBackwardsAllowed(false).withSoundDetail(SoundTuning.DETAIL_LOW)
                .withBrightness(50).withFlashStrength(0).withPanelSize(PanelSize.FIT_HEIGHT)
                .withKeys(Map.of(GameAction.FIRE, KeyChord.of(KeyInput.KEY_F11))));
        saved.save();
        OptionsDriver driver = new OptionsDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir), driver);

        boolean finished = run(app, driver.done, "Backwards Allowed from the settings");

        assertTrue(finished);
        assertEquals(List.of(), app.errors());
        assertEquals(Boolean.FALSE, driver.backwardsAllowed, "off in settings.json: off in the simulation");
        assertEquals(SoundTuning.DETAIL_LOW, driver.soundDetail, "the tower's sound cues take the sound detail");
        assertEquals(driver.ownSunRed * 0.5f, driver.sunRed, 1e-6f, "brightness 50 %, no flash at 0 %");
        assertEquals(PanelSize.FIT_HEIGHT, driver.panelSize);
        assertEquals(Optional.of(GameAction.FIRE), driver.f11, "the remapped key works in the tower");
        assertFalse(app.settings().current().backwardsAllowed());
    }

    /** Final review I1: a developer start asks too; a second X while asked quits at once; no profile is written. */
    private static final class DeveloperCloseDriver extends Driver {

        private volatile boolean asked;
        private volatile boolean quitting;
        private volatile String reason;

        @Override
        public void update(float tpf) {
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null || game.driver().ticks() < 5) {
                return;
            }
            app().requestClose(false);
            asked = game.menu().level() == PauseMenu.Level.CONFIRM && game.menu().choice() == PauseMenu.Choice.QUIT
                    && !app().isQuitting();
            app().requestClose(false);
            quitting = app().isQuitting();
            reason = app().flow().result().reason();
            done.countDown();
        }
    }

    @Test
    void aDeveloperStartQuitsOnTheSecondCloseWithoutTouchingAnyProfile(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = assumeAssets();
        DeveloperCloseDriver driver = new DeveloperCloseDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir), driver);

        boolean finished = run(app, driver.done, "window close, developer start");

        assertTrue(finished);
        assertEquals(List.of(), app.errors());
        assertTrue(driver.asked, "the first close asks");
        assertTrue(driver.quitting, "the second close quits");
        assertEquals("Attempt Abandoned", driver.reason);
        assertEquals(List.of(), new ProfileStore(userDir).list(), "no profile");
        assertFalse(Files.exists(userDir.resolve("settings.json")), "no settings");
    }

    /**
     * Final review M8 (decision 11): original-000 solved in the game; the end screen stays
     * {@link GameState#END_SECONDS} (fast-forward: {@link SimDriver#FRAME_SECONDS} per frame), or a key skips it (F12
     * does not); then the result window shows "Tower Completed" with the survivor bonus, and the profile holds the
     * tower's score.
     */
    private static final class SolveDriver extends Driver {

        private final boolean skip;
        private boolean named;
        private boolean chosen;
        private boolean started;
        private int next;
        private int endFrames = -1;
        private volatile int framesToResult;
        private volatile int alive;
        private volatile boolean f12Skipped;
        private volatile ResultText result;

        SolveDriver(boolean skip) {
            this.skip = skip;
        }

        @Override
        public void update(float tpf) {
            GameFlow flow = app().flow();
            if (flow == null || done.getCount() == 0) {
                return;
            }
            switch (flow.screen()) {
                case MENU, PLAY -> {
                    if (!named && flow.fadeAlpha() == 0f) {
                        flow.key(UiKey.ENTER);
                    }
                }
                case NEW_GAME -> {
                    if (!named) {
                        named = true;
                        for (char c : "Solver".toCharArray()) {
                            flow.typed(c);
                        }
                        flow.key(UiKey.ENTER);
                        flow.key(UiKey.ENTER);
                    }
                }
                case OVERVIEW -> {
                    if (!chosen) {
                        chosen = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case BRIEFING -> {
                    if (!started && flow.isEnabled(GameFlow.Widget.PLAY)) {
                        started = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case GAME -> play();
                case RESULT -> {
                    framesToResult = endFrames;
                    result = flow.result();
                    done.countDown();
                }
                default -> {
                }
            }
        }

        private void play() {
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null) {
                return;
            }
            SimDriver driver = game.driver();
            if (!driver.simulation().isFinished()) {
                if (next < SoftlySoftlySolutionTest.KEYS.size()
                        && driver.ticks() >= next * SoftlySoftlySolutionTest.FRAMES_PER_KEY) {
                    GameAction key = SoftlySoftlySolutionTest.KEYS.get(next++);
                    game.onAction(key);
                    game.onRelease(key);
                }
                return;
            }
            if (endFrames < 0) {
                endFrames = 0;
                int count = 0;
                for (SpiderId id : SpiderId.values()) {
                    if (driver.simulation().world().spider(id).status() == ObjectStatus.ALIVE) {
                        count++;
                    }
                }
                alive = count;
                if (skip) {
                    game.onAction(GameAction.TOGGLE_RECORDING);
                    f12Skipped = app().flow().screen() == GameFlow.Screen.RESULT;
                    game.onAction(GameAction.PAUSE);
                }
                return;
            }
            endFrames++;
        }
    }

    @ParameterizedTest(name = "skip {0}")
    @ValueSource(booleans = {false, true})
    void theEndScreenLeadsToTheCompletedResultWithTheSurvivorBonus(boolean skip, @TempDir Path userDir)
            throws InterruptedException {
        Path assets = assumeAssets();
        SolveDriver driver = new SolveDriver(skip);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);

        boolean finished = run(app, driver.done, "solve original-000, skip " + skip);

        assertTrue(finished, "solved and shown within 60 s");
        assertEquals(List.of(), app.errors());
        int endScreen = (int) Math.ceil(GameState.END_SECONDS / SimDriver.FRAME_SECONDS);
        if (skip) {
            assertFalse(driver.f12Skipped, "F12 does not skip the end screen");
            assertEquals(0, driver.framesToResult, "a key skips at once");
        } else {
            // the frame that ends the attempt already counts, and this state sees the end one frame late
            assertTrue(Math.abs(driver.framesToResult - endScreen) <= 3,
                    driver.framesToResult + " frames on the end screen, expected about " + endScreen);
        }
        assertEquals("Tower Completed", driver.result.caption());
        assertTrue(driver.alive >= 1, "survivors " + driver.alive);
        int bonus = 10 * driver.alive;
        assertEquals(List.of(new ResultText.Line("Tower Score", "100"), new ResultText.Line("Retry Bonus", "100"),
                new ResultText.Line("Survivor Bonus", Integer.toString(bonus)),
                new ResultText.Line("Total Score:", String.format(Locale.ROOT, "%06d", 200 + bonus))),
                driver.result.score());
        Profile saved = new ProfileStore(userDir).find("Solver").orElseThrow();
        assertTrue(saved.progress("original-000").completed());
        assertEquals(200 + bonus, saved.score(), "tower score, retry bonus and survivor bonus saved");
    }

    @Test
    void fromTheMenuThroughATowerToTheResultAndBackSavesTheProfile(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Player player = new Player();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), player);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = player.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("GameFlowSmokeTest: menu to result and back in " + (System.nanoTime() - start) / 1_000_000
                + " ms; screens " + player.screens);

        assertTrue(finished, "the whole flow within 60 s: " + player.screens);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(List.of(GameFlow.Screen.LOADER, GameFlow.Screen.MENU, GameFlow.Screen.PLAY,
                GameFlow.Screen.NEW_GAME, GameFlow.Screen.OVERVIEW, GameFlow.Screen.BRIEFING, GameFlow.Screen.GAME,
                GameFlow.Screen.RESULT, GameFlow.Screen.OVERVIEW), player.screens);
        assertEquals("original-000", player.tower, "group 01, tower A");
        assertEquals("Attempt Abandoned", player.reason);
        assertEquals("original-000 done", player.preparedSky, "the Briefing's finished sky preparation is handed to "
                + "the game");
        assertEquals(Boolean.TRUE, player.moonReadyAtStart, "the Briefing's prepared sky is handed to the game: "
                + "the moon shows from its first update");
        assertTrue(player.backwardsAllowed, "the settings' Backwards Allowed (on, the original's default; Phase 5b "
                + "decision 4) reaches the simulation");
        Profile saved = new ProfileStore(userDir).find("Smoke").orElseThrow();
        TowerProgress progress = saved.progress("original-000");
        assertEquals(1, progress.attempts(), "saved at the tower start");
        assertEquals(2, progress.counter(), "an abandoned attempt adds 2 (S:6771-6773)");
        assertEquals(Optional.of("Smoke"), new ProfileStore(userDir).lastProfile());
        assertTrue(player.demoSteps > 0, "the menu background played its demo script");
        assertTrue(player.music.containsAll(List.of("MENU:DREAMLAND", "BRIEFING:DREAMLAND", "GAME:none",
                "RESULT:FADEOUT", "OVERVIEW:DREAMLAND")), "Phase 5c-2 decisions 1-3: " + player.music);
        assertEquals(7, player.interlude, "Phase 5c-2 decision 6: the first failure's interlude, demo 7");
    }

    /**
     * Phase 5c-1 §4 (headless): main menu → Tutorial by mouse → tower A → Briefing → the tower → F10, YES → "Attempt
     * Abandoned" → back to the Tutorial with A selected; no profile file is written, and the goal-less A is marked
     * done (author 2026-10-15: played and left regularly).
     */
    private static final class TutorialDriver extends Driver {

        private final List<GameFlow.Screen> screens = new ArrayList<>();
        private boolean chosen;
        private boolean started;
        private boolean abandoned;
        private boolean resultLeft;
        private volatile String tower;
        private volatile String reason;
        private volatile int selectedAfter = -1;

        @Override
        public void update(float tpf) {
            GameFlow flow = app().flow();
            FrontEndState frontEnd = getState(FrontEndState.class);
            if (flow == null || done.getCount() == 0) {
                return;
            }
            if (screens.isEmpty() || screens.get(screens.size() - 1) != flow.screen()) {
                screens.add(flow.screen());
            }
            switch (flow.screen()) {
                case MENU -> {
                    if (flow.fadeAlpha() == 0f && frontEnd != null && frontEnd.frame() != null) {
                        OptionsByInputDriver.click(frontEnd, Hit.of(GameFlow.Widget.MENU_TUTORIAL));
                    }
                }
                case TUTORIAL -> {
                    if (!chosen) {
                        chosen = true;
                        flow.key(UiKey.ENTER);
                    } else if (resultLeft) {
                        selectedAfter = flow.tutorial().selected();
                        done.countDown();
                    }
                }
                case BRIEFING -> {
                    if (!started && flow.isEnabled(GameFlow.Widget.PLAY)) {
                        started = true;
                        flow.key(UiKey.ENTER);
                    }
                }
                case GAME -> {
                    GameState game = getState(GameState.class);
                    if (!abandoned && game != null && game.driver() != null && game.driver().ticks() >= 5) {
                        abandoned = true;
                        tower = game.tower().id();
                        game.onAction(GameAction.ABANDON_TOWER);
                        game.onAction(GameAction.TURN_LEFT);
                        game.onAction(GameAction.MENU_CONFIRM);
                    }
                }
                case RESULT -> {
                    if (!resultLeft) {
                        resultLeft = true;
                        reason = flow.result().reason();
                        flow.key(UiKey.ENTER);
                    }
                }
                default -> {
                }
            }
        }
    }

    @Test
    void aTutorialTowerIsPlayedWithoutAProfile(@TempDir Path userDir) throws InterruptedException {
        Path assets = assumeAssets();
        TutorialDriver driver = new TutorialDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);

        boolean finished = run(app, driver.done, "tutorial");

        assertTrue(finished, "within 60 s: " + driver.screens);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(List.of(GameFlow.Screen.LOADER, GameFlow.Screen.MENU, GameFlow.Screen.TUTORIAL,
                GameFlow.Screen.BRIEFING, GameFlow.Screen.GAME, GameFlow.Screen.RESULT, GameFlow.Screen.TUTORIAL),
                driver.screens);
        assertEquals("tutorial-000", driver.tower, "the app handed the tutorial library to the flow");
        assertEquals("Attempt Abandoned", driver.reason);
        assertEquals(0, driver.selectedAfter, "back on the Tutorial with the tower just played");
        assertFalse(Files.exists(userDir.resolve("savegames")), "no profile file");
        assertEquals(Set.of("tutorial-000"), new ProfileStore(userDir).settings().read().solvedTutorials(),
                "goal-less A: done once played and left (author 2026-10-15)");
    }
}
