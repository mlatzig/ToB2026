package de.latzig.game.tob.client;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

import de.latzig.game.tob.client.input.KeyBindings;
import de.latzig.game.tob.client.input.KeyboardInput;
import de.latzig.game.tob.client.input.MouseInput;
import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.audio.SoundCues;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.game.AttemptRecorder;
import de.latzig.game.tob.client.logic.game.CommandSource;
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.CursorAutoHide;
import de.latzig.game.tob.client.logic.game.FrameError;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.HudText;
import de.latzig.game.tob.client.logic.game.InputMapper;
import de.latzig.game.tob.client.logic.game.PanelButton;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PanelSize;
import de.latzig.game.tob.client.logic.game.PanelState;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.game.RecordingStore;
import de.latzig.game.tob.client.logic.game.RecordingSwitch;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.game.SpiderPick;
import de.latzig.game.tob.client.view.AssetLibrary;
import de.latzig.game.tob.client.view.AssetLoadException;
import de.latzig.game.tob.client.view.CameraController;
import de.latzig.game.tob.client.view.FlashOverlay;
import de.latzig.game.tob.client.view.HudOverlay;
import de.latzig.game.tob.client.view.NoiseOverlay;
import de.latzig.game.tob.client.view.PanelView;
import de.latzig.game.tob.client.view.SceneLook;
import de.latzig.game.tob.client.view.ShadowSetup;
import de.latzig.game.tob.client.view.SkyPreparation;
import de.latzig.game.tob.client.view.SkyView;
import de.latzig.game.tob.client.view.SoundView;
import de.latzig.game.tob.client.view.WorldView;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ObjectStatus;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;

/**
 * One attempt at one tower (spec §3, data flow): key, panel click or spider pick → {@link InputMapper} → command
 * queue → {@link SimDriver} → {@code Simulation.step} → events and state → views. The views only read.
 * <p>
 * Phase 3b-2: the ToB 2009 panel ({@link PanelView}, Tab or a click on the caption hides it), the software cursor, the
 * hold-to-repeat release of keys and panel buttons, spider picking in the 3D view, and the pause menu: P or the Pause
 * button pause, Esc pauses and opens the menu; Abandon or Restart (after YES) send their command, and once the
 * simulation has run the accepting frame the game shows the result window or starts the tower afresh. Like the
 * original ({@code S:8333-8339}), there is no pause while an exit is pending. Phase 5a decision 1: F9 restarts and F10
 * abandons the tower after the same ARE YOU SURE? (NO preselected) as the menu's RESTART and ABANDON; F10's texture
 * toggle is gone. Phase 5a decision 11: after the end the COMPLETED/FAILED/TIME UP screen stays {@value #END_SECONDS} s
 * (any key or click except F12 skips it), then the app shows the result window ({@link TobApp#attemptEnded}); an
 * abandoned attempt goes there at once, a restart counts in the profile ({@link TobApp#restartAttempt}). Final review
 * I1: the window's close button (or Alt+F4) asks ARE YOU SURE? about QUIT with NO preselected ({@link #requestQuit});
 * YES ends the attempt as abandoned (saved, the original's +2) and quits, so closing the window is no way around the
 * abandon penalty. With the
 * Briefing's {@link SkyPreparation} the moon shows at the first frame. The simulation runs with the options the app
 * passes (Phase 5b: the settings' Backwards Allowed). A missing asset or an exception inside a frame returns to the
 * start screens with a message (spec §6, {@link TobApp#leaveGame}).
 * <p>
 * Phase 3c (reworked after the author's decision of 2026-10-09: recordings are a development tool for regression
 * tests): an {@link AttemptRecorder} records the attempt only if the developer recording ({@link RecordingSwitch},
 * {@code -Dtob.record=true}) is on when it starts, and saves it only if it is completed, or when a frame throws
 * ({@code crash}); failed, timed-out, abandoned and restarted attempts are dropped. F12 toggles the recording: off
 * drops this attempt's recording at once, on takes effect at the next attempt start. The caption strip shows
 * {@code REC} while the attempt is recorded and {@code REC NEXT} while only the next one will be.
 * <p>
 * Phase 4c: the sounds. {@link SoundCues} turns every step's events, the lightning's strikes and the meteors'
 * impacts into one-shots and the world state into the loops of each frame, a panel button clicks; {@link SoundView}
 * plays them with the camera as the listener, within its voice budget; its WAVs load at the start. The loops pause
 * with the game (pause menu, end screen) while the one-shots play out; leaving the attempt (F9, the start screens, a
 * new tower) stops everything.
 * <p>
 * Author's extra (2026-10-14): while the "Hide Mouse Cursor" option is on, the software cursor ({@link PanelView})
 * hides after {@link CursorAutoHide#IDLE_SECONDS} s of active play without a mouse movement or a click, and shows
 * again at once on either; paused with the menu open (or its ARE YOU SURE?/QUIT? dialogs) or on the end screen it
 * always stays visible ({@link #cursorAutoHide}, fed every frame of {@link #update}). Picking and the panel keep
 * working while it is hidden: they read the mouse position, not this sprite.
 */
public final class GameState extends BaseAppState {

    private static final Logger LOG = LoggerFactory.getLogger(GameState.class);
    /** Phase 5a decision 11: how long the end screen stays before the result window (ToB 2009: 3 s). */
    public static final double END_SECONDS = 3.0;
    /**
     * The options of a start without a profile (developer starts, tests): Backwards Allowed on, the original's default
     * ({@code W0017C8} = 0, {@code S:1027}; author decision 2026-10-12).
     */
    public static final SimOptions ORIGINAL_OPTIONS = new SimOptions(true);

    private final TowerDefinition tower;
    private final String title;
    private final AssetLibrary assets;
    private final boolean fastForward;
    private final RecordingStore store;
    private final RecordingSwitch recordingSwitch;
    /**
     * Final review M5: whether this attempt is the designer's test play, which never records; F12 ({@code
     * TOGGLE_RECORDING}) is ignored then, so the caption strip cannot show a misleading "REC NEXT" that nothing ever
     * takes effect for (a fresh, throwaway {@link RecordingSwitch} is built for every test play and attempt restart).
     */
    private final boolean testPlay;
    /** The simulation's options (Phase 5b: the settings' Backwards Allowed). */
    private final SimOptions options;
    /** Phase 5a: the Briefing's preparation of this tower's sky textures, if any. */
    private final Optional<SkyPreparation> preparedSky;
    /** Seconds the end screen has been shown (Phase 5a decision 11). */
    private double endShown;
    private final Node scene = new Node("game");
    private final InputMapper mapper = new InputMapper();
    private final PanelState panelState = new PanelState();
    private final PauseMenu menu = new PauseMenu();
    private KeyBindings bindings;
    private KeyboardInput keyboard;
    private MouseInput mouse;
    private Simulation simulation;
    private SimDriver driver;
    /** The developer recording of this attempt (Phase 3c); null if the tower could not be started. */
    private AttemptRecorder recorder;
    /** The caption strip of the last frame, or null before the first frame (tests). */
    private String caption;
    private WorldView world;
    private CameraController camera;
    private SkyView sky;
    /** The ground: the terrain and socket, or the Classic Look's flat plane (Phase 5e). */
    private Node terrain;
    /** Phase 5e decision 5: this attempt's look, taken from the Look option at the tower's start. */
    private SceneLook look;
    private ShadowSetup shadows;
    /** This tower's sun; the application's one shadow renderer is pointed at it while this state is shown. */
    private DirectionalLight sun;
    /** The sun's own colour; a lightning flash adds white to it for a moment (Phase 4b-2). */
    private ColorRGBA sunColour;
    /** The sun's colour of this frame, rewritten in place (fix wave M1; {@code Light.setColor} copies it). */
    private final ColorRGBA sunShown = new ColorRGBA();
    /**
     * The scene's ambient light. Acceptance fix "flash on unlit faces": the flash adds white to it as well, so faces
     * turned away from the sun and faces in shadow flash white too, as in the original (the unlit sky stays dark).
     */
    private AmbientLight ambient;
    /** The ambient light's own colour. */
    private static final ColorRGBA AMBIENT_COLOUR = new ColorRGBA(0.4f, 0.4f, 0.45f, 1f);
    /** The ambient light's colour of this frame, rewritten in place ({@code Light.setColor} copies it). */
    private final ColorRGBA ambientShown = new ColorRGBA();
    private FlashOverlay flash;
    /** The flash curve shown in the last frame: the effects' curve, 0 once the attempt has finished (tests). */
    private float flashCurve;
    /** The effects' own flash curve in the last frame, before the end-screen rule (tests). */
    private float effectsFlashCurve;
    private HudOverlay hud;
    private NoiseOverlay noise;
    /** Phase 4c: what sounds when, and the sounds themselves. */
    private SoundCues cues;
    private SoundView sound;
    private PanelView panel;
    private PanelLayout layout;
    /** The Ambient effects option at the attempt's start (Phase 5b: from the next tower on). */
    private boolean ambientEffects = true;
    /** The Panel size option the layout was built for (Phase 5b: it switches live). */
    private PanelSize layoutSize;
    /** The Flash strength option's factor of the last frame (Phase 5b). */
    private float flashStrength = 1f;
    private boolean collapsed;
    private boolean started;
    private int width;
    private int height;
    private float alpha;
    /** Wall-clock seconds since the attempt started (the pulse of the centre message). */
    private double elapsed;
    /** The panel button the left mouse button holds down, or null. */
    private PanelButton mouseHeld;
    /** Abandon or Restart confirmed in the pause menu, waiting for the simulation to end the attempt. */
    private PauseMenu.Choice requestedExit;
    /** The window was closed before this state was initialized: ask at the first update (final review I1). */
    private boolean quitOnStart;
    /** The viewpoint shown until the first step applies the queued {@link InputMapper#initialSelection} (M1). */
    private SpiderId initialViewpoint;
    /** Commands actually sent to the simulation so far (tests: M2 leave-check order). */
    private int commandsSent;
    /** The cursor position of the previous frame: the pause menu's highlight follows only a moving cursor (item 7). */
    private float lastCursorX = Float.NaN;
    private float lastCursorY = Float.NaN;
    /** Author's extra (2026-10-14): the software cursor auto-hide, engine-free and tested on its own. */
    private final CursorAutoHide cursorAutoHide = new CursorAutoHide();
    /** A left click since the last frame, fed to {@link #cursorAutoHide} (any click shows the cursor at once). */
    private boolean cursorClicked;

    /**
     * @param number the 1-based number of the tower in its library, for the title bar
     * @param store           where the attempt's recording is saved (Phase 3c)
     * @param recordingSwitch whether the developer recording is on (Phase 3c rework; F12 toggles it)
     */
    public GameState(TowerDefinition tower, int number, AssetLibrary assets, boolean fastForward,
            RecordingStore store, RecordingSwitch recordingSwitch) {
        this(tower, number, assets, fastForward, store, recordingSwitch, ORIGINAL_OPTIONS, Optional.empty(), false);
    }

    /**
     * @param options     the simulation's options (Phase 5b: the settings' Backwards Allowed)
     * @param preparedSky the Briefing's preparation of this tower's moon and cloud textures (Phase 5a)
     * @param testPlay    final review M5: the designer's test play (never recorded; F12 is ignored)
     */
    public GameState(TowerDefinition tower, int number, AssetLibrary assets, boolean fastForward,
            RecordingStore store, RecordingSwitch recordingSwitch, SimOptions options,
            Optional<SkyPreparation> preparedSky, boolean testPlay) {
        this.tower = tower;
        this.title = HudText.title(number, tower);
        this.assets = assets;
        this.fastForward = fastForward;
        this.store = store;
        this.recordingSwitch = recordingSwitch;
        this.options = options;
        this.preparedSky = preparedSky;
        this.testPlay = testPlay;
    }

    @Override
    protected void initialize(Application app) {
        TobApp tob = (TobApp) app;
        try {
            simulation = Simulation.start(tower, options);
            // Phase 5b: the moon detail and the ambient effects apply from the next tower on
            // Task 6 minor (final review): named "chosen", not "options", so it does not shadow the SimOptions field
            Settings chosen = tob.settings().current();
            // Phase 5e decision 5: the Look applies from the next tower on; it sets the shared fog (Phase 4b-4: ToB
            // 2005's fog colour is the horizon colour, every world material shares it; the Classic Look has none)
            look = SceneLook.of(assets, tower, chosen.classicLook());
            look.applyFog(assets);
            TowerColours colours = look.colours();
            ambientEffects = chosen.ambientEffects();
            world = look.world(assets, simulation.world(), ambientEffects);
            sky = look.sky(assets, tower, tob.config().sky().withHalfMoonDetail(chosen.halfMoonDetail()), preparedSky);
            terrain = look.ground(assets, tower);
            hud = new HudOverlay(assets.font());
            panel = new PanelView(assets, assets.font());
            noise = new NoiseOverlay(app.getAssetManager());
            // Phase 5b: the sound detail applies from the next tower on
            cues = new SoundCues(simulation.world(), assets.soundBank(), tower.sounds(), chosen.soundDetail(),
                    tower.id().hashCode());
            sound = new SoundView(app.getAssetManager(), app.getAudioRenderer(), app.getListener(),
                    assets.soundBank());
            // Phase 4c fix wave: every WAV at the start (no hitch at its first use; a missing one is a missing asset)
            sound.preload();
            scene.attachChild(terrain);
            scene.attachChild(world.node());
            sunColour = color(look.sun());
            sun = new DirectionalLight(SkyView.SUN_DIRECTION.clone(), sunColour.clone());
            flash = new FlashOverlay(app.getAssetManager());
            scene.addLight(sun);
            shadows = tob.shadows();
            ambient = new AmbientLight(AMBIENT_COLOUR.clone());
            scene.addLight(ambient);
            app.getViewPort().setBackgroundColor(color(colours.skyHorizon()));
        } catch (AssetLoadException e) {
            LOG.error("Tower {}: {}", tower.id(), e.getMessage(), e);
            // final review M10: a Briefing's sky job not yet taken over by a SkyView must not run on
            preparedSky.ifPresent(SkyPreparation::cancel);
            tob.leaveGame("Missing or unreadable asset: " + e.file());
            return;
        } catch (RuntimeException e) {
            LOG.error("Tower {} cannot be loaded: {}", tower.id(), e.getMessage(), e);
            preparedSky.ifPresent(SkyPreparation::cancel);
            tob.leaveGame("Tower " + tower.id() + " cannot be loaded: " + e.getMessage());
            return;
        }
        recorder = new AttemptRecorder(recordingSwitch, store, tower, options, Clock.systemUTC());
        // Task 16: the mapper's side-effect-free peek feeds the walk look-ahead of the drawing
        driver = new SimDriver(simulation, recorder.recording(CommandSource.peeking(this::nextCommand, mapper)),
                this::onStep);
        camera = new CameraController(app.getCamera(), CameraPlacement.of(tower));
        // Phase 5b decision 5: the remapped keys apply from the next tower on
        bindings = KeyBindings.of(tob.settings().current().keys());
        keyboard = new KeyboardInput(bindings, this::onAction, this::onRelease);
        mouse = new MouseInput(new MouseInput.Listener() {
            @Override
            public void leftPressed(float x, float y) {
                onLeftPressed(x, y);
            }

            @Override
            public void leftReleased(float x, float y) {
                onLeftReleased();
            }
        });
        initialViewpoint = InputMapper.initialSpider(simulation.world());
        InputMapper.initialSelection(simulation.world()).ifPresent(mapper::press);
        started = true;
        LOG.info("Tower {} started: {}", tower.id(), title);
    }

    /**
     * Detached (F9, the start screens, a new attempt, closing the game): an attempt left before its end is not saved;
     * a moon texture generation still running is cancelled (Phase 4b-3).
     */
    @Override
    protected void cleanup(Application app) {
        if (sky != null) {
            sky.dispose();
        }
        if (sound != null) {
            sound.stopAll();
        }
    }

    @Override
    protected void onEnable() {
        if (!started) {
            return;
        }
        TobApp app = (TobApp) getApplication();
        app.getRootNode().attachChild(scene);
        shadows.setSun(sun, ShadowRange.distance(tower));
        shadows.attach(app.getViewPort());
        app.getGuiNode().attachChild(noise.geometry());
        // Phase 4b-3: the sky hangs outside the scene, so the scene's sun and ambient light do not reach the moon,
        // which has its own lights
        app.getRootNode().attachChild(sky.node());
        app.getGuiNode().attachChild(flash.geometry());
        app.getGuiNode().attachChild(hud.node());
        app.getGuiNode().attachChild(panel.node());
        app.getInputManager().addRawInputListener(keyboard);
        app.getInputManager().addRawInputListener(mouse);
        app.getInputManager().setMouseCursor(MouseInput.hiddenCursor());
        // Final review M3: jME has tracked the OS cursor's position all along, even while the previous screen (the
        // start screens or another attempt) was shown; without this, MouseInput's x/y stay 0 (drawing the software
        // cursor off-window, spec §2) until the next real motion event, so the cursor is invisible right after every
        // tower start or restart.
        Vector2f cursor = app.getInputManager().getCursorPosition();
        mouse.seedPosition(cursor.x, cursor.y);
    }

    @Override
    protected void onDisable() {
        if (!started) {
            return;
        }
        scene.removeFromParent();
        shadows.detach(getApplication().getViewPort());
        noise.geometry().removeFromParent();
        flash.geometry().removeFromParent();
        sky.node().removeFromParent();
        hud.node().removeFromParent();
        panel.node().removeFromParent();
        getApplication().getInputManager().removeRawInputListener(keyboard);
        getApplication().getInputManager().removeRawInputListener(mouse);
        getApplication().getInputManager().setMouseCursor(null);
        // Task 9 review (addition B): a release missed while these listeners are detached must not leave a held
        // mouse button or a held panel button/key stuck, which would swallow the next real press or keep repeating.
        mouse.resetHeld();
        mapper.releaseHeld();
        mouseHeld = null;
        // Author's extra (2026-10-14): the game is left, the cursor shows again and a fresh attempt starts its idle
        // timer anew.
        cursorAutoHide.reset();
    }

    @Override
    public void update(float tpf) {
        if (driver == null) {
            return;
        }
        if (quitOnStart) {
            quitOnStart = false;
            requestQuit();
            return;
        }
        try {
            // Final review M2: the layout refresh and the "cursor left the held button" check run before
            // driver.update, matching jME's own order (inputManager.update before stateManager.update): otherwise a
            // due step inside driver.update could still send one more repeat of a button the cursor has just left,
            // in the render frame in which the motion event moved it off.
            // Acceptance fix 2: a window without area (minimized: 0 x 0) keeps the last valid layout and frustum.
            Camera cam = getApplication().getCamera();
            Settings options = ((TobApp) getApplication()).settings().current();
            if (layout == null || PanelLayout.hasArea(cam.getWidth(), cam.getHeight())
                    && (cam.getWidth() != width || cam.getHeight() != height || options.panelSize() != layoutSize)) {
                layout = PanelLayout.resized(layout, cam.getWidth(), cam.getHeight(), options.panelSize());
                layoutSize = options.panelSize();
                width = layout.width();
                height = layout.height();
                camera.resize(cam.getWidth(), cam.getHeight());
            }
            if (mouseHeld != null && layout.buttonAt(mouse.x(), mouse.y(), collapsed).orElse(null) != mouseHeld) {
                onLeftReleased();
            }
            boolean cursorMoved = mouse.x() != lastCursorX || mouse.y() != lastCursorY;
            if (cursorMoved) {
                lastCursorX = mouse.x();
                lastCursorY = mouse.y();
                if (driver.isPaused()) {
                    menu.hover(lastCursorX, lastCursorY, layout, dialog());
                }
            }
            double seconds = fastForward ? SimDriver.FRAME_SECONDS : tpf;
            // Author's extra (2026-10-14), per the author's clarification: the cursor hides only during active play
            // (not paused with the menu open or its ARE YOU SURE?/QUIT? dialogs, not on the end screen); elsewhere
            // reset() keeps it visible and starts a fresh idle timer once play resumes.
            if (!driver.isPaused() && !simulation.isFinished()) {
                cursorAutoHide.update(seconds, cursorMoved || cursorClicked, options.hideMouseCursor());
            } else {
                cursorAutoHide.reset();
            }
            cursorClicked = false;
            if (!driver.isPaused()) {
                mapper.advance(seconds);
            }
            driver.update(seconds);
            if (endRequestedAttempt()) {
                return;
            }
            if (simulation.isFinished() && requestedExit == null) {
                endShown += seconds;
                if (endShown >= END_SECONDS) {
                    reportEnd();
                    return;
                }
            }
            alpha = driver.alpha();
            // Until the first step has applied the queued initial selection, simulation.selected() is still the
            // absent zapper (M1): show the first present spider instead, so neither the HUD nor the camera's noise
            // overlay flashes at every start and every F9.
            Viewpoint shown = shown();
            // Acceptance fix "reversal interpolation": everything is drawn with the driver's carry, so a move
            // reversed before its advance does not snap
            world.update(alpha, shown, driver.carry());
            camera.update(simulation, shown, alpha, driver.carry(), tpf);
            // Phase 4b-1: the explosions run on simulation time (steps run + alpha), so they stand still while
            // paused or in a menu and keep pace with fast-forward. Phase 4b-2: so do the ambient effects; a
            // lightning flash adds white to the sun (the lit faces flash, the unlit sky stays dark) and shows the
            // faint white overlay (AmbientTuning). Fix wave M2: once the attempt has finished (end screen) there is
            // no flash: the sun keeps its own colour and the overlay is hidden (paused, the flash stands still).
            // Fix wave M1: no allocation per frame for the flash colours (the Phase 4c sound hand-off below allocates
            // its small cue lists each frame, as planned).
            float now = driver.ticks() + alpha;
            world.updateEffects(now, cam.getLeft(), cam.getUp());
            sound.play(cues.onAmbient(world.newStrikes(), world.newImpacts(), now));
            effectsFlashCurve = world.flash(now);
            flashCurve = simulation.isFinished() ? 0f : effectsFlashCurve;
            // Phase 5b decision 6: brightness scales the sun and the ambient light, the flash strength the flash
            flashStrength = options.flashFactor();
            float bright = options.brightnessFactor();
            float scene = AmbientTuning.sceneFlash(flashCurve, flashStrength);
            sun.setColor(sunShown.set(AmbientTuning.light(sunColour.r, bright, scene),
                    AmbientTuning.light(sunColour.g, bright, scene), AmbientTuning.light(sunColour.b, bright, scene),
                    sunColour.a));
            ambient.setColor(ambientShown.set(AmbientTuning.light(AMBIENT_COLOUR.r, bright, scene),
                    AmbientTuning.light(AMBIENT_COLOUR.g, bright, scene),
                    AmbientTuning.light(AMBIENT_COLOUR.b, bright, scene), AMBIENT_COLOUR.a));
            flash.update(AmbientTuning.overlayFlash(flashCurve, flashStrength), width, height);
            // Phase 4b-3: the clouds drift and the moon turns on simulation time (driver steps + alpha), so they
            // stand still while paused or in a menu and keep pace with fast-forward; the steps include the READY
            // frames, so the moon turns from the start.
            sky.update(cam.getLocation(), now, ambientEffects);
            elapsed += tpf;
            panelState.advance(tpf);
            boolean paused = driver.isPaused();
            hud.update(simulation.world(), paused, elapsed, layout, collapsed,
                    paused && menu.level() == PauseMenu.Level.CONFIRM);
            caption = recorder.caption(HudText.caption(simulation.world()));
            panel.update(layout, collapsed, panelState, simulation, shown, paused, menu, title, caption, mouse.x(),
                    mouse.y());
            if (!cursorAutoHide.visible()) {
                // the panel and picking keep working either way: they read the mouse position directly, not this
                // sprite; a click shows it again next frame and still acts (cursorClicked, set above)
                panel.hideCursor();
            }
            noise.update(camera.showsNoise(), width, height);
            // Phase 4c: the loops stand still with the game (paused, in a menu, on the end screen); Phase 5b: the
            // background loops follow the Background option live
            sound.backgroundVolume(options.backgroundScale());
            sound.update(cues.loops(alpha, camera.showsNoise()), paused || simulation.isFinished(), cam);
        } catch (RuntimeException e) {
            // SimFailure (Simulation.step) and every other runtime exception of this frame's view, camera, sky,
            // HUD, panel or noise update (spec §6: no crash); FrameError keeps SimFailure's own tower id and frame.
            FrameError error = FrameError.of(tower.id(), simulation.frame(), e);
            Optional<Path> file = recorder.crashed(simulation.frame());
            LOG.error(error.logMessage() + file.map(f -> "; recording saved to " + f).orElse(""), e);
            ((TobApp) getApplication()).leaveGame(error.message());
            driver = null;
        }
    }

    /** The command of this frame; the panel flashes its button. */
    private Optional<Command> nextCommand(Simulation sim) {
        Optional<Command> command = mapper.next(sim);
        command.ifPresent(c -> {
            panelState.onCommand(c);
            commandsSent++;
        });
        return command;
    }

    private void onStep(List<SimEvent> events, int tick) {
        if (simulation.isFinished()) {
            recorder.finished(simulation.exitCode(), simulation.frame());
        }
        world.onStep(events, tick);
        sound.play(cues.onStep(events));
        panelState.onStep(simulation, shown());
    }

    private Viewpoint shown() {
        return driver.ticks() == 0 ? initialViewpoint : simulation.selected();
    }

    /**
     * After the frame that accepted a confirmed Abandon or Restart: the result window at once (the original's "Attempt
     * Abandoned", {@code S:4381-4383}) or the same tower afresh, counted as a restart (Phase 5a).
     *
     * @return true if this game state has ended
     */
    private boolean endRequestedAttempt() {
        if (requestedExit == null || !simulation.isFinished()) {
            return false;
        }
        TobApp app = (TobApp) getApplication();
        int exit = simulation.exitCode();
        requestedExit = null;
        if (exit == Rules.EXIT_ABANDONED) {
            driver = null;
            app.attemptEnded(exit, survivors(), simulation.frame());
            return true;
        }
        if (exit == Rules.EXIT_RESTART) {
            driver = null;
            app.restartAttempt(tower);
            return true;
        }
        return false;
    }

    /** The end screen is over: the app shows the result window (Phase 5a decision 11). */
    private void reportEnd() {
        int exit = simulation.exitCode();
        driver = null;
        ((TobApp) getApplication()).attemptEnded(exit, survivors(), simulation.frame());
    }

    /** The player spiders present and alive: the survivor bonus ({@code S:6456-6467}). */
    private int survivors() {
        int alive = 0;
        for (SpiderId id : SpiderId.values()) {
            if (simulation.world().spider(id).status() == ObjectStatus.ALIVE) {
                alive++;
            }
        }
        return alive;
    }

    /**
     * A key or a panel button: client keys at once, everything else to the command queue or the pause menu. On the
     * end screen every key but the developer recording's F12 skips to the result window (Phase 5a decision 11).
     */
    void onAction(GameAction action) {
        if (driver == null) {
            return;
        }
        if (simulation.isFinished() && requestedExit == null && action != GameAction.TOGGLE_RECORDING) {
            reportEnd();
            return;
        }
        switch (action) {
            case PAUSE -> {
                if (driver.isPaused()) {
                    setPaused(false);
                } else if (pauseAllowed()) {
                    setPaused(true);
                }
            }
            case MENU -> {
                if (driver.isPaused()) {
                    menu.escape();
                    resumeIfAnswered();
                } else if (pauseAllowed()) {
                    setPaused(true);
                    menu.open();
                }
            }
            case PANEL_CHANGE -> collapsed = !collapsed;
            case RESTART_TOWER -> exitKey(PauseMenu.Choice.RESTART);
            case ABANDON_TOWER -> exitKey(PauseMenu.Choice.ABANDON);
            case TOGGLE_RECORDING -> {
                // Final review M5: a test play never records, so F12 is ignored - toggling the throwaway switch
                // would only show a misleading "REC NEXT" that no attempt (this one or its F9 restart) ever honours.
                if (!testPlay) {
                    recorder.toggle();
                    LOG.info("Recording {} (F12)", recordingSwitch.isOn() ? "on from the next attempt" : "off");
                }
            }
            default -> {
                if (driver.isPaused()) {
                    menu.key(action).ifPresent(this::choose);
                    resumeIfAnswered();
                } else {
                    mapper.press(action);
                }
            }
        }
    }

    /** The key of {@code action} was released: a held repeat stops. */
    void onRelease(GameAction action) {
        mapper.release(action);
    }

    /**
     * A left click (spec §4.1): while the pause menu's ARE YOU SURE? dialog is open, only its YES and NO react (modal,
     * acceptance round 2); else the X at the left end of the title bar acts like Esc and goes first (acceptance item
     * 6; ToB 2009 drew the X but never implemented it); the open pause menu takes every other click; otherwise the
     * caption strip toggles the panel,
     * a button acts like its key (and, except Pause, repeats in every frame while held, as in the original,
     * {@link #onLeftReleased}), the rest of the panel and the title bar swallow the click, and a click in the 3D view
     * picks a spider.
     */
    void onLeftPressed(float x, float y) {
        // Author's extra (2026-10-14): any click shows the cursor at once, even one swallowed by the checks below.
        cursorClicked = true;
        if (driver == null || layout == null) {
            return;
        }
        if (simulation.isFinished() && requestedExit == null) {
            reportEnd();
            return;
        }
        if (driver.isPaused() && menu.level() == PauseMenu.Level.CONFIRM) {
            // Round 2, item 2: the ARE YOU SURE? dialog is modal; only its YES and NO react.
            menu.click(x, y, layout, dialog()).ifPresent(this::choose);
            resumeIfAnswered();
            return;
        }
        if (layout.onExitButton(x, y)) {
            onAction(GameAction.MENU);
            return;
        }
        if (driver.isPaused() && (menu.level() != PauseMenu.Level.CLOSED || layout.menuTitle().contains(x, y))) {
            menu.click(x, y, layout, dialog()).ifPresent(this::choose);
            resumeIfAnswered();
            return;
        }
        if (layout.onCaption(x, y, collapsed)) {
            onAction(GameAction.PANEL_CHANGE);
            return;
        }
        Optional<PanelButton> button = layout.buttonAt(x, y, collapsed);
        if (button.isPresent()) {
            cues.click().ifPresent(click -> sound.play(List.of(click)));
            GameAction action = button.get().action();
            if (driver.isPaused() || !action.isQueued()) {
                onAction(action);
            } else {
                mouseHeld = button.get();
                mapper.pressPanel(action);
            }
            return;
        }
        if (layout.covers(x, y, collapsed) || driver.isPaused()) {
            return;
        }
        Camera cam = getApplication().getCamera();
        Vector3f near = cam.getWorldCoordinates(new Vector2f(x, y), 0f);
        Vector3f direction = cam.getWorldCoordinates(new Vector2f(x, y), 1f).subtractLocal(near);
        SpiderPick.pick(simulation.world(), alpha, driver.carry(), shown(), new SpiderPick.Ray(near.x, near.y, near.z,
                direction.x, direction.y, direction.z)).ifPresent(spider -> mapper.press(switch (spider) {
                    case ZAPPER -> GameAction.SELECT_ZAPPER;
                    case PUSHER -> GameAction.SELECT_PUSHER;
                    case GRABBER -> GameAction.SELECT_GRABBER;
                }));
    }

    /** The pause menu's ARE YOU SURE? dialog for the current layout. */
    private ConfirmDialogLayout dialog() {
        return ConfirmDialogLayout.of(layout, collapsed);
    }

    /** The left button was released, or the cursor left the held button: a held repeat stops. */
    void onLeftReleased() {
        if (mouseHeld != null) {
            mapper.release(mouseHeld.action());
            mouseHeld = null;
        }
    }

    /** Like the original's pause button ({@code S:8333-8339}): refused while an exit is pending or after the end. */
    private boolean pauseAllowed() {
        return !simulation.isFinished() && !simulation.world().isExitPending();
    }

    private void setPaused(boolean paused) {
        driver.setPaused(paused);
        mapper.setPaused(paused);
        onLeftReleased();
        if (!paused) {
            menu.close();
            panelState.onPauseEnd();
        }
    }

    /**
     * F9 (restart) and F10 (abandon), Phase 5a decision 1: pause and ask ARE YOU SURE? with NO highlighted, exactly as
     * the pause menu's EXIT → RESTART / ABANDON; refused like the pause button while an exit is pending or after the
     * end ({@code S:8333-8339}). Asked from the running game, NO and Esc resume it (acceptance 2026-10-13); asked
     * while paused, they return to the EXIT level.
     */
    private void exitKey(PauseMenu.Choice choice) {
        if (driver.isPaused()) {
            menu.confirm(choice);
            return;
        }
        if (!pauseAllowed()) {
            return;
        }
        setPaused(true);
        menu.ask(choice);
    }

    /** NO or Esc answered a question asked straight from the running game (F9, F10, the close button): resume. */
    private void resumeIfAnswered() {
        if (menu.takeResume()) {
            setPaused(false);
        }
    }

    /**
     * The window's close button or Alt+F4 during the game (final review I1, Phase 5a decisions 6 and 9): pauses and
     * asks ARE YOU SURE? about QUIT with NO preselected, like F10 (NO resumes a running game). Nothing is asked
     * when there is nothing to lose: if the attempt has finished, an exit is pending or an Abandon/Restart is on its
     * way, or if the question is already open (a second close), the attempt ends at once with its own result, or as
     * abandoned, is saved and the app quits ({@link #quitNow}). Practice and developer sessions go the same way;
     * their profiles stay untouched ({@code GameFlow}). Before the first frame the question waits for the attempt to
     * run.
     */
    void requestQuit() {
        if (driver == null) {
            if (!isInitialized()) {
                quitOnStart = true;
            } else {
                ((TobApp) getApplication()).quit();
            }
            return;
        }
        boolean asking = driver.isPaused() && menu.level() == PauseMenu.Level.CONFIRM
                && menu.choice() == PauseMenu.Choice.QUIT;
        if (asking || requestedExit != null || !pauseAllowed()) {
            quitNow();
            return;
        }
        if (driver.isPaused()) {
            menu.confirm(PauseMenu.Choice.QUIT);
        } else {
            setPaused(true);
            menu.ask(PauseMenu.Choice.QUIT);
        }
    }

    /**
     * Ends the attempt for a quit and stops the app: a finished attempt or a pending exit with its own result (the
     * end screen is skipped), anything else as abandoned (the original's +2, {@code S:6771-6773}).
     */
    private void quitNow() {
        int exit = Rules.EXIT_ABANDONED;
        if (simulation.isFinished()) {
            exit = simulation.exitCode();
        } else if (simulation.world().isExitPending()) {
            exit = simulation.world().exitCode();
        }
        if (exit == Rules.EXIT_RESTART) {
            exit = Rules.EXIT_ABANDONED;
        }
        driver = null;
        ((TobApp) getApplication()).quitAfterAttempt(exit, survivors(), simulation.frame());
    }

    /**
     * YES in the pause menu: resume and send the command; {@link #endRequestedAttempt} finishes the job. YES to QUIT
     * ends the attempt as abandoned at once and quits.
     */
    private void choose(PauseMenu.Choice choice) {
        if (choice == PauseMenu.Choice.QUIT) {
            quitNow();
            return;
        }
        requestedExit = choice;
        setPaused(false);
        mapper.submit(choice == PauseMenu.Choice.ABANDON ? new Command.Abandon() : new Command.Restart());
    }

    public TowerDefinition tower() {
        return tower;
    }

    /** The key bindings of this attempt (tests, Phase 5b: the settings' remapped keys). */
    KeyBindings bindings() {
        return bindings;
    }

    /** The sound cues of this attempt (tests, Phase 5b: their sound detail). */
    SoundCues cues() {
        return cues;
    }

    /** The simulation's options (tests, Phase 5a: Backwards Allowed). */
    SimOptions options() {
        return options;
    }

    /** This attempt's look (tests, Phase 5e). */
    SceneLook look() {
        return look;
    }

    /** The Briefing's sky preparation handed to this attempt, if any (tests, Phase 5a). */
    Optional<SkyPreparation> preparedSky() {
        return preparedSky;
    }

    /** The moon's turn of the last frame in radians, NaN without a moon (tests, Phase 4b-3). */
    float moonSpin() {
        return sky.moonSpin();
    }

    /** Whether the moon's textures are shown (tests, Phase 4b-3). */
    boolean moonReady() {
        return sky.moonReady();
    }

    /** The sky (tests, Phase 4b-3). */
    SkyView sky() {
        return sky;
    }

    /** The explosions running (tests, Phase 4b-1). */
    int activeExplosions() {
        return world.activeExplosions();
    }

    /** The lightning strikes so far, -1 without the lightning flag (tests, Phase 4b-2). */
    int lightningStrikes() {
        return world.lightningStrikes();
    }

    /** The meteor impacts so far, -1 without the meteor flag (tests, Phase 4b-2). */
    int meteorImpacts() {
        return world.meteorImpacts();
    }

    /** Whether the ground haze is shown (tests, Phase 4b-2). */
    boolean hasHaze() {
        return world.hasHaze();
    }

    /** The one-shots played so far per sound id, also without an audio renderer (tests, Phase 4c). */
    Map<String, Integer> soundsPlayed() {
        return sound.played();
    }

    /** The loop keys of the last frame (tests, Phase 4c). */
    Set<String> soundLoops() {
        return sound.loopKeys();
    }

    /** Every loop key of this attempt so far (tests, Phase 4c). */
    Set<String> soundLoopsEver() {
        return sound.everLooped();
    }

    /** Whether the last frame paused the loops (tests, Phase 4c). */
    boolean soundsPaused() {
        return sound.isPaused();
    }

    /** The lightning flash of the last frame at the Flash strength option (tests, Phase 4b-2, Phase 5b). */
    AmbientTuning.Flash lastFlash() {
        return AmbientTuning.Flash.at(flashCurve, AmbientTuning.SCENE_FLASH * flashStrength,
                AmbientTuning.OVERLAY_FLASH * flashStrength);
    }

    /** The Panel size option of the last frame's layout (tests, Phase 5b). */
    PanelSize panelSize() {
        return layoutSize;
    }

    /** The effects' own flash curve in the last frame, before the end-screen rule (tests, fix wave M2). */
    float effectsFlashCurve() {
        return effectsFlashCurve;
    }

    /** The sun's colour of the last frame (tests, fix wave M2). */
    ColorRGBA sunColor() {
        return sun.getColor();
    }

    /** The ambient light's colour of the last frame (tests, acceptance fix "flash on unlit faces"). */
    ColorRGBA ambientColor() {
        return ambient.getColor();
    }

    /** The sun's own colour, without a flash (tests, fix wave M2). */
    ColorRGBA ownSunColor() {
        return sunColour;
    }

    /** The flash overlay's alpha of the last frame (tests, fix wave M2). */
    float flashOverlayAlpha() {
        return flash.alpha();
    }

    /** The running driver, or null after a failure or once the attempt has been left (tests). */
    public SimDriver driver() {
        return driver;
    }

    /** The panel layout of the last frame, or null before the first frame (tests). */
    public PanelLayout layout() {
        return layout;
    }

    /** Whether the panel is collapsed to the caption strip (tests). */
    public boolean isCollapsed() {
        return collapsed;
    }

    public PauseMenu menu() {
        return menu;
    }

    /** The caption strip of the last frame, with {@code REC} while recording; null before the first frame (tests). */
    String caption() {
        return caption;
    }

    /** The mouse's raw input listener (tests: simulating a motion event without a real input device, M2). */
    MouseInput mouse() {
        return mouse;
    }

    /** Commands actually sent to the simulation so far (tests: M2 leave-check order). */
    int commandsSent() {
        return commandsSent;
    }

    private static ColorRGBA color(int rgb) {
        return new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }
}
