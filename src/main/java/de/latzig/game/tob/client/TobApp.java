package de.latzig.game.tob.client;

import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;
import com.simsilica.lemur.GuiGlobals;

import de.latzig.game.tob.client.input.KeyBindings;
import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.frontend.Campaign;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.LiveSettings;
import de.latzig.game.tob.client.logic.frontend.MusicChoice;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.game.PickerModel;
import de.latzig.game.tob.client.logic.game.RecordingStore;
import de.latzig.game.tob.client.logic.game.RecordingSwitch;
import de.latzig.game.tob.client.logic.game.TowerEntry;
import de.latzig.game.tob.client.view.AssetLibrary;
import de.latzig.game.tob.client.view.AssetLoadException;
import de.latzig.game.tob.client.view.HazeFirstComparator;
import de.latzig.game.tob.client.view.MusicPlayer;
import de.latzig.game.tob.client.view.OutputLimiter;
import de.latzig.game.tob.client.view.ShadowSetup;
import de.latzig.game.tob.client.view.SkyPreparation;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.SimOptions;

/**
 * The ToB 2026 game: a jME application (LWJGL3; Phase 5b decision 2: fullscreen at the desktop resolution, or a
 * 1280 × 720 window, chosen at the start from the saved options). Phase 5a: the game flow of ToB 2009 with the
 * original's rules ({@link GameFlow}): the start screens ({@link FrontEndState}) over the live menu background
 * ({@link MenuBackgroundState}) — loader, main menu, Play and its name entry, Overview, Tutorial, Briefing,
 * result window, Options, Hall of Fame — and the game ({@link GameState}); the profiles in the user data folder
 * ({@link ProfileStore}). Phase 5b: the options are
 * global ({@link LiveSettings}, {@code <userdir>/settings.json}); every attempt runs with their Backwards Allowed
 * (decision 4). Started with {@code mvn compile exec:java}; {@code tob.assets} overrides the assets root. A developer
 * start ({@code tob.tower}, {@link AppConfig}) loads synchronously and plays the tower at once without a profile;
 * its result window leads to the main menu. Without the default fly camera, stats and debug keys; Esc does not quit the
 * game. Phase 3c: only while the developer recording is on ({@code -Dtob.record=true} or F12 in a game,
 * {@link RecordingSwitch}) are completed attempts, and a crash, saved to the {@link RecordingStore} in the user data
 * folder ({@code tob.userdir}).
 * <p>
 * Input ownership: the start screens and a game are never attached at the same time, and each registers its raw
 * input listeners only while attached, so exactly one of them takes the input.
 * <p>
 * Final review I1: the window's close button and Alt+F4 ({@link #requestClose}) ask first, like Quit and Esc on the
 * main menu (decision 6); in a game the attempt then ends as abandoned and is saved before the app stops (decision 9).
 * <p>
 * Phase 6b: the Tower Designer's libraries are the assets' towers (read-only) and {@code <userdir>/towers/}
 * ({@link TowerLibraries}); its test play starts like any attempt, with the designer's tower number, the enemies
 * frozen if chosen, and a recording switch of its own that is off (no recording of a test play).
 * <p>
 * Phase 5c-2: the menu music ({@link MusicPlayer}) follows the flow every frame ({@link MusicChoice}: Dreamland on
 * the start screens, Improvi or Fadeout on the result window, silence in the game and the Tower Designer, nothing
 * while the Music switch is off); the menu background plays the interlude behind the result window.
 */
public final class TobApp extends SimpleApplication {

    private static final Logger LOG = LoggerFactory.getLogger(TobApp.class);
    /** The window of the Window display mode (Phase 5b decision 2: today's window). */
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    /** The loader's steps: the tower libraries, the sounds, then the main menu. */
    private static final int LOAD_STEPS = 3;

    private final AppConfig config;
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
    private final Texts texts = Texts.english();
    private AssetLibrary assets;
    private PickerModel catalog;
    private Campaign campaign;
    private List<TowerDefinition> demos = List.of();
    private RecordingStore store;
    private RecordingSwitch recordingSwitch;
    /** Phase 5b: the global options, saved and previewed. */
    private LiveSettings settings;
    private GameFlow flow;
    private FrontEndState frontEnd;
    private MenuBackgroundState background;
    /** Phase 5c-2: the menu music. */
    private MusicPlayer music;
    private int loadStep;
    /** The one shadow renderer of the application (final review I1), created at the first tower start. */
    private ShadowSetup shadows;
    /** The app is stopping on the player's request (Quit, or a confirmed window close). */
    private volatile boolean quitting;
    /**
     * M3 (final review): the OpenAL context the limiter was last switched on for, 0 before the first audio renderer.
     * jME 3.9's {@code ALAudioRenderer.checkDevice} recreates the device/context without attributes when the output
     * device disappears, which switches the limiter off again; comparing the current context (a cheap native call)
     * every frame catches that and switches it back on.
     */
    private long limiterContext;
    /**
     * R1 (final review re-review): true once reading the OpenAL context or switching the limiter on has failed
     * outside the handled "device has no audio" case (e.g. the OpenAL natives themselves failed to load; jME's
     * {@code ALAudioRenderer.initOpenAL} then keeps {@code audioRenderer} non-null and runs without sound). Stops
     * {@link #simpleUpdate}'s per-frame watch for good instead of repeating and logging the same failure every frame.
     */
    private boolean limiterWatchFailed;
    /**
     * Phase 7c: whether the start found an audio device ({@link AudioDevice}); false switches Options' SOUND rows
     * off. Headless tests have no audio renderer but keep this true, so their Options behave as with sound.
     */
    private boolean audioDevice = true;

    /** @param extraStates app states attached at start (the headless smoke test drives the game with one) */
    public TobApp(AppConfig config, AppState... extraStates) {
        super(extraStates);
        this.config = config;
    }

    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        // Phase 7c: the log file of this run in the user data folder (the console log stays)
        LogFiles.start(AppConfig.userDir(System.getProperty(AppConfig.USERDIR_PROPERTY)));
        AppConfig config = AppConfig.fromSystemProperties();
        TobApp app = new TobApp(config);
        // Phase 5b decision 2: the display mode applies at the start, from the saved options
        Settings options = LiveSettings.load(new ProfileStore(config.userDir()).settings()).saved();
        AppSettings windowSettings = appSettings(options, desktopRefreshRate());
        windowSettings.setIcons(WindowIcons.load()); // Phase 7c decision 3: the ToB 2005 icon
        // Phase 7c: without an audio device the game starts without sound instead of failing in jME's OpenAL setup
        app.audioDevice = AudioDevice.apply(windowSettings, AudioDevice.available(AudioDevice.OPENAL));
        app.setSettings(windowSettings);
        LOG.info("Display: {}", options.fullscreen() ? "fullscreen at the desktop resolution"
                : "window " + WINDOW_WIDTH + " x " + WINDOW_HEIGHT);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    /**
     * The window of the options (Phase 5b decision 2): fullscreen at the desktop's video mode (window size 0 × 0, the
     * desktop's refresh rate, or GLFW's choice if unknown), or the resizable {@value #WINDOW_WIDTH} ×
     * {@value #WINDOW_HEIGHT} window; 4 × MSAA and VSync either way.
     *
     * @param desktopRefreshRate the desktop's refresh rate in Hz, 0 if unknown
     */
    static AppSettings appSettings(Settings options, int desktopRefreshRate) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("ToB 2026");
        settings.setSamples(4);
        settings.setVSync(true);
        if (options.fullscreen()) {
            settings.setFullscreen(true);
            settings.setWindowSize(0, 0);
            settings.setFrequency(desktopRefreshRate > 0 ? desktopRefreshRate : -1);
        } else {
            settings.setResolution(WINDOW_WIDTH, WINDOW_HEIGHT);
            settings.setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            settings.setResizable(true);
        }
        return settings;
    }

    /** The primary display's refresh rate (AWT), 0 if unknown (no display, or AWT cannot tell). */
    static int desktopRefreshRate() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return 0;
            }
            int rate = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode()
                    .getRefreshRate();
            return rate == DisplayMode.REFRESH_RATE_UNKNOWN ? 0 : rate;
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the desktop's refresh rate: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void simpleInitApp() {
        if (inputManager.hasMapping(INPUT_MAPPING_EXIT)) {
            inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        }
        GuiGlobals.initialize(this);
        if (audioRenderer != null) {
            // Phase 5b §4: Master 200 % (or -Dtob.volume=4) drives a loud effect past full scale; OpenAL Soft's
            // limiter keeps the peaks from clipping. Logged, so the state can be checked without listening.
            limiterContext = enableLimiter("OpenAL output limiter");
        }
        // Phase 4b-2: the ground haze is drawn before every other transparent geometry (one comparator per viewport).
        HazeFirstComparator.install(viewPort);
        viewPort.setBackgroundColor(ColorRGBA.Black);
        store = new RecordingStore(config.userDir());
        recordingSwitch = recordingSwitch(System.getProperty(AppPaths.APP_PATH_PROPERTY), config.record());
        ProfileStore profiles = new ProfileStore(config.userDir());
        settings = LiveSettings.load(profiles.settings());
        flow = new GameFlow(texts, profiles, settings, KeyBindings.table(), config.volume().isPresent(), audioDevice);
        flow.designer(new TowerLibraries(config.towersDirectory(), config.userDir()));
        LOG.info("Recording {} (F12); recordings: {}", config.record() ? "on" : "off",
                config.userDir().resolve("recordings").toAbsolutePath());
        // M4 (final review): -Dtob.volume is a hidden override of Master; log it once so the author knows why the
        // Master row has no audible effect.
        config.volume().ifPresent(gain -> LOG.info("Master volume overridden by -D{}={} (replaces Master)",
                AppConfig.VOLUME_PROPERTY, gain));
        try {
            assets = new AssetLibrary(assetManager, config.assetsRoot());
            GuiGlobals.getInstance().getStyles().setDefault(assets.font());
        } catch (AssetLoadException e) {
            failed(e);
            return;
        }
        music = new MusicPlayer(assetManager, audioRenderer);
        frontEnd = new FrontEndState(flow, texts);
        if (config.startTower().isEmpty()) {
            stateManager.attach(frontEnd);
            return;
        }
        try {
            loadCatalog();
        } catch (IOException | RuntimeException e) {
            failed(e);
            return;
        }
        int index = catalog.indexOf(config.startTower().get());
        if (index >= 0 && catalog.entries().get(index).playable()) {
            TowerDefinition tower = catalog.entries().get(index).tower().orElseThrow();
            flow.developerSession(campaign, tower);
            startTower(tower);
            return;
        }
        flow.loaded(campaign);
        leaveGame("Tower " + config.startTower().get() + " not found or not playable");
    }

    /**
     * Phase 5b decision 3: the listener's gain is the Master option every frame (live while the Options window
     * previews it), or the hidden developer override {@code -Dtob.volume}. Headless there is no listener.
     */
    @Override
    public void simpleUpdate(float tpf) {
        if (listener != null && settings != null) {
            listener.setVolume(settings.current().listenerGain(config.volume()));
        }
        if (audioRenderer != null && !limiterWatchFailed) {
            // M3 (final review): jME recreates the device/context (no attributes) when the output device
            // disappears (USB/Bluetooth headset unplugged or switched); the limiter is off afterwards although
            // the log still says "on". A changed context (cheap to compare) is switched on again, logged.
            // R1 (final review re-review): the context read is guarded exactly like enableLimiter, and a failure
            // there switches the watch off for good (see limiterWatchFailed) instead of risking it every frame.
            // Phase 5c-2 fix wave: before the music's update, so the music is restored before it is touched.
            long context = currentAlcContext();
            if (context != 0L && context != limiterContext) {
                limiterContext = enableLimiter("OpenAL output limiter after a device change");
                if (music != null) {
                    music.deviceChanged();
                }
            }
        }
        if (music != null) {
            // Phase 5c-2: the song of the flow's screen, the result and the Music switch (previewed at once); its
            // volume follows the screen fade when the song changes (the author's answer to question 3); after an
            // audio device change it plays again (MusicPlayer notices the lost context itself)
            boolean on = settings.current().music();
            music.update(MusicChoice.wanted(flow, on), MusicChoice.fade(flow, on));
        }
    }

    /**
     * The current OpenAL context (R1, final review re-review), guarded the same way as {@link #enableLimiter}: on
     * a machine where the OpenAL natives themselves failed to load, jME keeps {@code audioRenderer} non-null (it
     * runs without sound), but any {@code ALC10} call still throws. A failure here switches
     * {@link #limiterWatchFailed} on, so {@link #simpleUpdate} stops probing for good.
     *
     * @return the current context, or 0 if there is none or it could not be read
     */
    private long currentAlcContext() {
        try {
            return ALC10.alcGetCurrentContext();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Cannot read the current OpenAL context; switching the output limiter watch off: {}",
                    e.getMessage());
            limiterWatchFailed = true;
            return 0L;
        }
    }

    /**
     * Switches OpenAL Soft's output limiter on for the current context and logs {@code what} it did.
     *
     * @return the current OpenAL context afterwards, or 0 if it could not be switched on or read (R1: also
     *         switches {@link #limiterWatchFailed} on, so a failing machine is not probed again every frame)
     */
    private long enableLimiter(String what) {
        try {
            LOG.info("{}: {}", what, OutputLimiter.enable().map(on -> on ? "on" : "off").orElse("not available"));
            return ALC10.alcGetCurrentContext();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Cannot switch the OpenAL output limiter on: {}", e.getMessage());
            limiterWatchFailed = true;
            return 0L;
        }
    }

    private void failed(Exception e) {
        LOG.error("Cannot start: {}", e.getMessage(), e);
        errors.add(e);
        stop();
    }

    /**
     * The 144 towers (loaded leniently), the standard game's campaign, the tutorial (Phase 5c-1: handed to the flow)
     * and the demo library.
     */
    private void loadCatalog() throws IOException {
        catalog = PickerModel.load(config.towersDirectory(), config.towerLibrary());
        List<TowerDefinition> original = new ArrayList<>();
        List<TowerDefinition> tutorial = new ArrayList<>();
        List<TowerDefinition> demo = new ArrayList<>();
        for (TowerEntry entry : catalog.entries()) {
            entry.tower().ifPresent(tower -> {
                if (tower.id().startsWith(GameFlow.LIBRARY + "-")) {
                    original.add(tower);
                } else if (tower.id().startsWith("tutorial-")) {
                    tutorial.add(tower);
                } else if (tower.id().startsWith("demo-")) {
                    demo.add(tower);
                }
            });
        }
        campaign = Campaign.of(original);
        flow.tutorial(tutorial);
        demos = List.copyOf(demo);
        // Phase 5c-2 decision 6: the background plays the interlude the result window wants
        background = new MenuBackgroundState(assets, demos, config.fastForward(), flow::interlude);
    }

    /**
     * One loading step per frame for the loader's bar (Phase 5a decision 2): the tower libraries, the sounds, then the
     * main menu with the menu background.
     *
     * @return the progress, 0..1
     */
    float loadNext() {
        if (loadStep >= LOAD_STEPS || !errors.isEmpty()) {
            // final review M10: after a failed step the app is stopping; no further step (no flow.loaded(null))
            return 1f;
        }
        try {
            switch (loadStep) {
                case 0 -> loadCatalog();
                case 1 -> assets.soundBank();
                default -> {
                    flow.loaded(campaign);
                    showBackground();
                }
            }
        } catch (IOException | RuntimeException e) {
            failed(e);
        }
        loadStep++;
        return (float) loadStep / LOAD_STEPS;
    }

    /**
     * Starts (or restarts) {@code tower} at once (developer starts, restarts, tests): the start screens and the menu
     * background are left; the title bar shows its number.
     */
    public void startTower(TowerDefinition tower) {
        startAttempt(tower, Optional.empty());
    }

    /**
     * Starts {@code tower} from the Briefing, with the sky textures it prepared (Phase 5a decision 14), and with the
     * settings' Backwards Allowed, fixed for the whole attempt (Phase 5b decision 4).
     */
    public void startAttempt(TowerDefinition tower, Optional<SkyPreparation> sky) {
        detach(frontEnd);
        detach(background);
        detachGame();
        Optional<TowerDesigner.Test> test = flow.testPlay();
        SimOptions options = new SimOptions(settings.current().backwardsAllowed(),
                test.map(TowerDesigner.Test::freezeEnemies).orElse(false));
        stateManager.attach(new GameState(tower, test.map(TowerDesigner.Test::number).orElseGet(() -> number(tower)),
                assets, config.fastForward(), store, test.isPresent() ? new RecordingSwitch(false) : recordingSwitch,
                options, sky, test.isPresent()));
    }

    /**
     * The tower's number for the title bar: the campaign's (group × 9 + index + 1, as the Overview shows it) for the
     * standard game's towers, else its number in its library (Phase 6d: an own tower's position in its library;
     * tutorial and demo towers, developer starts).
     */
    private int number(TowerDefinition tower) {
        OptionalInt own = flow.customNumber(tower.id());
        if (own.isPresent()) {
            return own.getAsInt();
        }
        return campaign.locate(tower.id()).map(at -> campaign.number(at.group(), at.index()))
                .orElseGet(() -> catalog.number(tower.id()));
    }

    /** The pause menu's RESTART or F9 was confirmed: the original's counter +1, then the tower afresh. */
    public void restartAttempt(TowerDefinition tower) {
        flow.attemptRestarted();
        startTower(tower);
    }

    /** The attempt is over (after the end screen, or abandoned): the result window (decision 11). */
    public void attemptEnded(int exitCode, int survivors, int frames) {
        flow.attemptEnded(exitCode, survivors, frames, Instant.now());
        showStartScreens();
    }

    /**
     * The window was closed during the attempt and the player confirmed (final review I1): the attempt ends with
     * {@code exitCode} (abandoned unless it had already finished), the profile is saved as at every attempt end, and
     * the app quits without showing the result window.
     */
    void quitAfterAttempt(int exitCode, int survivors, int frames) {
        flow.attemptEnded(exitCode, survivors, frames, Instant.now());
        quit();
    }

    /** Stops the app on the player's request (Quit on the main menu, a confirmed window close). */
    public void quit() {
        quitting = true;
        stop();
    }

    /** Whether the player asked to quit (tests: the headless context never closes a window). */
    public boolean isQuitting() {
        return quitting;
    }

    /**
     * The window's close button or Alt+F4 (final review I1; jME's window loop calls this on the render thread while
     * GLFW's close flag is set): the flag is cleared so the loop does not ask again every frame, then a running game
     * asks ARE YOU SURE? about QUIT ({@link GameState#requestQuit}) and the start screens ask like Quit
     * ({@link GameFlow#requestQuit}); a second close while the question is open quits. Only the loader and a failed
     * start close at once. The GLFW call is guarded: a headless context has no window.
     */
    @Override
    public void requestClose(boolean esc) {
        if (context instanceof LwjglWindow window && window.getWindowHandle() != 0L) {
            GLFW.glfwSetWindowShouldClose(window.getWindowHandle(), false);
        }
        GameState game = stateManager.getState(GameState.class);
        if (game != null) {
            game.requestQuit();
            return;
        }
        if (flow != null && frontEnd != null && stateManager.hasState(frontEnd)
                && flow.screen() != GameFlow.Screen.LOADER) {
            flow.requestQuit();
            return;
        }
        quitting = true;
        super.requestClose(esc);
    }

    /** The game failed (a missing asset, a failed frame; spec §6): back to the start screens with {@code message}. */
    public void leaveGame(String message) {
        messages.add(message);
        flow.gameLeft(message);
        showStartScreens();
    }

    /**
     * Ends the running game, if any, and shows the start screens (the Overview with a profile, else the main menu);
     * a non-empty {@code message} shows as an error there.
     */
    public void showFrontEnd(String message) {
        if (!message.isEmpty()) {
            leaveGame(message);
            return;
        }
        flow.gameLeft("");
        showStartScreens();
    }

    private void showStartScreens() {
        detachGame();
        if (!stateManager.hasState(frontEnd)) {
            stateManager.attach(frontEnd);
        }
        showBackground();
    }

    private void showBackground() {
        if (background != null && !stateManager.hasState(background) && flow.screen() != GameFlow.Screen.EDITOR) {
            stateManager.attach(background);
        }
    }

    /** Phase 6b: the menu background pauses while the Tower Designer is shown (drawn on black) and returns after. */
    void showMenuBackground(boolean shown) {
        if (shown) {
            showBackground();
        } else {
            detach(background);
        }
    }

    private void detach(AppState state) {
        if (state != null && stateManager.hasState(state)) {
            stateManager.detach(state);
        }
    }

    private void detachGame() {
        GameState game = stateManager.getState(GameState.class);
        if (game != null) {
            stateManager.detach(game);
        }
    }

    @Override
    public void handleError(String message, Throwable t) {
        errors.add(t == null ? new IllegalStateException(message) : t);
        super.handleError(message, t);
    }

    /**
     * Phase 7c: F12 and the developer recording stay out of the packages - a start from a package ({@code appPath},
     * the launcher's {@value AppPaths#APP_PATH_PROPERTY}, set) gets {@link RecordingSwitch#locked()} whatever
     * {@code record} ({@code -Dtob.record}) says.
     */
    static RecordingSwitch recordingSwitch(String appPath, boolean record) {
        return AppPaths.packaged(appPath) ? RecordingSwitch.locked() : new RecordingSwitch(record);
    }

    public AppConfig config() {
        return config;
    }

    public AssetLibrary assets() {
        return assets;
    }

    /** Phase 7c (tests): starts the app as if no audio device was found ({@link AudioDevice}). */
    void noAudioDevice() {
        audioDevice = false;
    }

    /** The global options (Phase 5b): saved in {@code settings.json}, previewed by the Options window. */
    public LiveSettings settings() {
        return settings;
    }

    /** The game flow (Phase 5a; tests drive it). */
    public GameFlow flow() {
        return flow;
    }

    /** The menu music (Phase 5c-2), null before the assets are loaded. */
    public MusicPlayer music() {
        return music;
    }

    /** The demo library in its order (the menu background). */
    public List<TowerDefinition> demoTowers() {
        return demos;
    }

    /**
     * The application's one shadow renderer (final review I1): every tower start reuses it and points it at its own
     * sun, since jME 3.9 frees a detached shadow renderer's depth maps only when the renderer is garbage collected.
     */
    public ShadowSetup shadows() {
        if (shadows == null) {
            shadows = new ShadowSetup(assetManager);
        }
        return shadows;
    }

    /** The recordings in the user data folder (Phase 3c). */
    public RecordingStore store() {
        return store;
    }

    /** Whether the developer recording is on (Phase 3c rework; F12 toggles it). */
    public RecordingSwitch recordingSwitch() {
        return recordingSwitch;
    }

    /** The error messages shown so far (failures that returned to the start screens). */
    public List<String> messages() {
        return List.copyOf(messages);
    }

    /** Uncaught errors of the render loop and start-up failures. */
    public List<Throwable> errors() {
        return List.copyOf(errors);
    }
}
