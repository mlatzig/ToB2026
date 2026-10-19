package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.game.ConfirmDialogLayout;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.PanelButton;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.PauseMenu;
import de.latzig.game.tob.client.logic.game.RecordingStore;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.game.SoftlySoftlySolutionTest;
import de.latzig.game.tob.client.logic.game.SolutionCheck;
import de.latzig.game.tob.core.io.RecordingReader;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.Replayer;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.Snapshots;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Spec §7 smoke test: jME in a headless context loads one tower (the first tutorial tower) and runs 200 simulation
 * frames of seeded random keyboard input in fast-forward (one frame per render frame). Since Phase 3b-1 the input
 * includes the camera, zoom and program keys; since Phase 3b-2 Tab, held keys (released at the next input) and
 * clicks on the panel buttons. No exception, no missing asset and no simulation error may occur; a finished attempt
 * is restarted. The runtime is logged.
 *
 * <p>Further cases: an Esc, Abandon, start screens, start-again cycle through the pause menu's keys must not leak
 * the raw input listeners each screen owns while shown
 * ({@link #abandonStartScreensStartAgainCycleLeavesNoInputListenerLeak(Path)});
 * Pause, Exit, Restart, YES by mouse starts the same tower afresh
 * ({@link #pauseMenuRestartByMouseStartsAfresh(Path)});
 * and {@link GameState#update(float)}'s broad catch must turn an exception from a view update, not only a
 * {@code SimFailure} from the simulation step, into a message on the start screens instead of a crash
 * ({@link #injectedRuntimeExceptionInAViewUpdateReturnsToTheStartScreens(boolean, Path)}).
 */
class HeadlessSmokeTest {

    private static final int FRAMES = 200;
    private static final long SEED = 20261008L;
    /**
     * The headless frame cap: jME's {@code NullContext.sync} sleeps {@code Thread.sleep(1)} per frame below it, and on
     * Windows 11 a windowless process loses the 1 ms timer resolution after a few seconds (15.6 ms per sleep). Every
     * case runs fast-forward (one simulation step per render frame), so no cap is needed: 1e9 / MAX_VALUE ns = 0.
     */
    private static final int UNTHROTTLED = Integer.MAX_VALUE;
    private static final List<GameAction> ACTIONS = List.of(GameAction.FORWARD, GameAction.BACK,
            GameAction.TURN_LEFT, GameAction.TURN_RIGHT, GameAction.FIRE, GameAction.SELECT_ZAPPER,
            GameAction.SELECT_PUSHER, GameAction.SELECT_GRABBER, GameAction.PAUSE,
            GameAction.CAMERA_NORTH, GameAction.CAMERA_EAST, GameAction.CAMERA_SOUTH, GameAction.CAMERA_WEST,
            GameAction.ZOOM_IN, GameAction.ZOOM_OUT, GameAction.PROGRAM_WRITE, GameAction.PROGRAM_UP,
            GameAction.PROGRAM_FIRE, GameAction.PROGRAM_SLOT_3, GameAction.PROGRAM_DELETE, GameAction.PROGRAM_CLEAR,
            GameAction.PROGRAM_STOP, GameAction.PROGRAM_RUN, GameAction.PROGRAM_RUN_ALL, GameAction.PANEL_CHANGE);

    /**
     * Starts {@code game}'s tower afresh at once through the direct-start path, as F9 did before Phase 5a (now F9
     * asks ARE YOU SURE? first).
     */
    private static void restartAtOnce(GameState game) {
        ((TobApp) game.getApplication()).startTower(game.tower());
    }

    /**
     * Presses a random action or clicks a random panel button in about every third render frame and counts the
     * simulation frames run. A pressed key stays held until the next input releases it.
     */
    private static final class RandomKeys extends BaseAppState {

        private final Random random = new Random(SEED);
        private final CountDownLatch done = new CountDownLatch(1);
        private GameState game;
        private SimDriver driver;
        private GameAction held;
        private int frames;
        private int lastTicks;
        private int clicks;

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
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null) {
                return;
            }
            if (current != game || current.driver() != driver) {
                game = current;
                driver = current.driver();
                lastTicks = 0;
            }
            frames += driver.ticks() - lastTicks;
            lastTicks = driver.ticks();
            if (frames >= FRAMES) {
                done.countDown();
                return;
            }
            if (driver.isPaused()) {
                game.onAction(GameAction.PAUSE);
            } else if (driver.simulation().isFinished()) {
                restartAtOnce(game);
            } else if (random.nextInt(3) == 0) {
                if (held != null) {
                    game.onRelease(held);
                    held = null;
                }
                if (random.nextInt(4) == 0 && game.layout() != null) {
                    PanelButton button = PanelButton.values()[random.nextInt(PanelButton.values().length)];
                    PanelLayout.Rect rect = game.layout().button(button);
                    game.onLeftPressed(rect.x() + rect.width() / 2f, rect.y() + rect.height() / 2f);
                    game.onLeftReleased();
                    clicks++;
                } else {
                    held = ACTIONS.get(random.nextInt(ACTIONS.size()));
                    game.onAction(held);
                }
            }
        }
    }

    /**
     * Phase 4a: runs {@code original-000} (stars density 3, a moon in the north) for {@value #LOOK_FRAMES} simulation
     * frames and checks that the shadow renderer, the sky with stars and moon, the terrain and the socket are in the
     * scene while nothing fails.
     */
    private static final class WorldLookProbe extends BaseAppState {

        private static final int LOOK_FRAMES = 60;

        private final CountDownLatch done = new CountDownLatch(1);
        private volatile boolean shadows;
        private volatile boolean sky;
        private volatile float intensity = Float.NaN;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || game.driver().ticks() < LOOK_FRAMES || done.getCount() == 0) {
                return;
            }
            TobApp app = (TobApp) getApplication();
            shadows = app.getViewPort().getProcessors().stream()
                    .anyMatch(p -> p instanceof DirectionalLightShadowRenderer);
            intensity = app.getViewPort().getProcessors().stream()
                    .filter(p -> p instanceof DirectionalLightShadowRenderer)
                    .map(p -> ((DirectionalLightShadowRenderer) p).getShadowIntensity()).findFirst().orElse(Float.NaN);
            sky = Stream.of("sky-dome", "stars", "moon", "terrain", "socket")
                    .allMatch(name -> app.getRootNode().getChild(name) != null);
            done.countDown();
        }
    }

    @Test
    void original000RunsWithShadowsSkyStarsMoonTerrainAndSocket(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        WorldLookProbe probe = new WorldLookProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("HeadlessSmokeTest: original-000 world look in " + (System.nanoTime() - start) / 1_000_000
                + " ms");

        assertTrue(finished, "60 frames within 60 s");
        assertTrue(probe.shadows, "the directional shadow renderer is attached");
        assertEquals(0.7f, probe.intensity, 1e-6f, "Phase 4b-4 decision 6: stronger than 4a's 0.55");
        assertTrue(probe.sky, "sky dome, stars, moon, terrain and socket are in the scene");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no error");
    }

    /**
     * Final review I1: jME 3.9's {@code AbstractShadowRenderer.cleanup()} frees nothing, so a shadow renderer per tower
     * start would pile up its depth maps in video memory until a full GC. Starts {@code original-000} {@value #STARTS}
     * times, alternately by F9 ({@link GameAction#RESTART_TOWER}) and through the start screens, and records the
     * viewport's shadow processors in every attempt and while the start screens are shown. Phase 4b-3 (final review
     * Minor 3): also the root node's children named like the sky node and the live moon texture threads, per attempt
     * and at the start screens.
     */
    private static final class ShadowRestartProbe extends BaseAppState {

        private static final int STARTS = 4;

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<List<DirectionalLightShadowRenderer>> perAttempt = new ArrayList<>();
        private final List<Boolean> lightOfAttempt = new ArrayList<>();
        private final List<Integer> atStartScreens = new ArrayList<>();
        private final List<Integer> skiesOfAttempt = new ArrayList<>();
        private final List<Integer> skiesAtStartScreens = new ArrayList<>();
        private final List<Long> moonThreads = new ArrayList<>();
        private GameState last;
        private boolean toStartScreens;

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
            TobApp app = (TobApp) getApplication();
            if (toStartScreens) {
                MenuBackgroundState background = getState(MenuBackgroundState.class);
                if (getState(GameState.class) == null && getState(FrontEndState.class) != null && background != null
                        && background.player() != null) {
                    atStartScreens.add(renderers(app).size());
                    skiesAtStartScreens.add(skies(app));
                    toStartScreens = false;
                    app.startTower(last.tower());
                }
                return;
            }
            GameState game = getState(GameState.class);
            if (game == null || game == last || game.driver() == null || game.driver().ticks() < 3
                    || done.getCount() == 0) {
                return;
            }
            last = game;
            List<DirectionalLightShadowRenderer> shown = renderers(app);
            perAttempt.add(shown);
            lightOfAttempt.add(shown.size() == 1 && sceneHolds(app, shown.get(0).getLight()));
            skiesOfAttempt.add(skies(app));
            moonThreads.add(Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().equals("tob-moon-textures")).count());
            if (perAttempt.size() == STARTS) {
                done.countDown();
            } else if (perAttempt.size() % 2 == 1) {
                restartAtOnce(game);
            } else {
                app.showFrontEnd("");
                toStartScreens = true;
            }
        }

        /** The root node's children named like the sky node. */
        private static int skies(TobApp app) {
            return (int) app.getRootNode().getChildren().stream().filter(c -> "sky".equals(c.getName())).count();
        }

        private static List<DirectionalLightShadowRenderer> renderers(TobApp app) {
            return app.getViewPort().getProcessors().stream()
                    .filter(p -> p instanceof DirectionalLightShadowRenderer)
                    .map(p -> (DirectionalLightShadowRenderer) p)
                    .toList();
        }

        /** Whether a node directly under the root node (the game's scene) holds {@code sun} as a local light. */
        private static boolean sceneHolds(TobApp app, Light sun) {
            for (Spatial child : app.getRootNode().getChildren()) {
                for (Light light : child.getLocalLightList()) {
                    if (light == sun) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Test
    void restartsAndStartScreenCyclesShareOneShadowRenderer(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        ShadowRestartProbe probe = new ShadowRestartProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, ShadowRestartProbe.STARTS + " tower starts within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "restarts are not failures");
        assertEquals(ShadowRestartProbe.STARTS, probe.perAttempt.size());
        DirectionalLightShadowRenderer first = probe.perAttempt.get(0).get(0);
        for (int i = 0; i < probe.perAttempt.size(); i++) {
            List<DirectionalLightShadowRenderer> shown = probe.perAttempt.get(i);
            assertEquals(1, shown.size(), "attempt " + i + ": exactly one shadow processor on the main viewport");
            assertTrue(shown.get(0) == first, "attempt " + i + ": the same shadow renderer instance as the first");
            assertTrue(probe.lightOfAttempt.get(i), "attempt " + i + ": the renderer follows this attempt's sun");
        }
        assertEquals(List.of(1), probe.atStartScreens, "Phase 5a: at the start screens only the menu background's one "
                + "(shared) shadow processor");
        assertEquals(List.of(1, 1, 1, 1), probe.skiesOfAttempt, "one sky under the root node per attempt");
        assertEquals(List.of(1), probe.skiesAtStartScreens, "only the menu background's sky at the start screens");
        assertTrue(probe.moonThreads.stream().allMatch(n -> n <= 1), "at most one moon texture thread: "
                + probe.moonThreads);
    }

    /**
     * Phase 4b-3 (spec decisions 9 and 13): the 3D moon turns on simulation time, so it stands still while the game is
     * paused and turns on once it runs; its textures come in the background and it shows once they are ready. The sky
     * runs with the tower's own options ({@code original-000}: a normal moon at full detail).
     */
    private static final class MoonPauseProbe extends BaseAppState {

        private static final int PAUSED_FRAMES = 20;

        private final CountDownLatch done = new CountDownLatch(1);
        private int phase;
        private int frames;
        private int ticksAtResume;
        private boolean paused;
        private boolean ready;
        private float atPause = Float.NaN;
        private float afterPause = Float.NaN;
        private float afterResume = Float.NaN;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || done.getCount() == 0) {
                return;
            }
            switch (phase) {
                case 0 -> {
                    if (game.driver().ticks() >= 5) {
                        game.onAction(GameAction.PAUSE);
                        paused = game.driver().isPaused();
                        phase = 1;
                    }
                }
                case 1 -> {
                    if (frames == 0) {
                        atPause = game.moonSpin();
                    }
                    if (++frames >= PAUSED_FRAMES) {
                        afterPause = game.moonSpin();
                        game.onAction(GameAction.PAUSE);
                        ticksAtResume = game.driver().ticks();
                        phase = 2;
                    }
                }
                case 2 -> {
                    if (game.driver().ticks() >= ticksAtResume + 3) {
                        afterResume = game.moonSpin();
                        phase = 3;
                    }
                }
                default -> {
                    if (game.moonReady()) {
                        ready = true;
                        done.countDown();
                    }
                }
            }
        }
    }

    @Test
    void theMoonTurnsOnSimulationTimeAndStandsStillWhilePaused(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        MoonPauseProbe probe = new MoonPauseProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "pause, resume and the moon's textures within 60 s");
        assertEquals(List.of(), app.errors());
        assertTrue(probe.paused, "the Pause action paused the game");
        assertTrue(Float.isFinite(probe.atPause));
        assertEquals(probe.atPause, probe.afterPause, 0f,
                "the moon stands still for " + MoonPauseProbe.PAUSED_FRAMES + " paused render frames");
        assertTrue(probe.afterResume != probe.afterPause, "the moon turns on once the game runs");
        assertTrue(probe.ready, "the moon shows once its textures are ready");
    }

    /**
     * Phase 4b-3: runs a tower until its moon's textures are ready (the game does not wait for them) and records the
     * sky: the moon's size class, the clouds, and the moon standing still over {@value #PAUSED_FRAMES} paused render
     * frames. Also the first simulation tick and the wall time at which the moon was seen ready (final review Minor
     * 6: the ticks are unthrottled headless frames, not game time), that the sky hangs under the root node rather
     * than the scene, and that the moon's spheres are lit by the moon's own two lights only (final review Minor 2).
     */
    private static final class SkyProbe extends BaseAppState {

        private static final int PAUSED_FRAMES = 10;

        private final CountDownLatch done = new CountDownLatch(1);
        private int phase;
        private int frames;
        private volatile int readyTick = -1;
        private volatile long readyNanos;
        private volatile boolean skyUnderRoot;
        private volatile int spheres;
        private volatile boolean ownLightsOnly;
        private volatile Object size;
        private volatile boolean clouds;
        private float atPause = Float.NaN;
        private volatile boolean stoodStill;
        /** Phase 4c: the loops of the last paused frame and whether they were paused. */
        private volatile Set<String> loops = Set.of();
        private volatile boolean soundsPaused;
        /** Phase 4c fix wave: the first attempt, and its loops once F9 has started the next one. */
        private GameState first;
        private volatile Set<String> loopsAfterF9;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || done.getCount() == 0) {
                return;
            }
            if (first == null) {
                first = game;
            }
            if (game != first) {
                loopsAfterF9 = Set.copyOf(first.soundLoops());
                done.countDown();
                return;
            }
            if (phase == 0) {
                if (game.moonReady() && readyTick < 0) {
                    readyTick = game.driver().ticks();
                    readyNanos = System.nanoTime();
                }
                if (game.moonReady() && game.driver().ticks() >= 5) {
                    skyUnderRoot = game.sky().node().getParent() == ((TobApp) getApplication()).getRootNode();
                    recordMoonLights((Node) game.sky().node().getChild("moon"));
                    size = game.sky().moonSize();
                    clouds = game.sky().hasClouds();
                    game.onAction(GameAction.PAUSE);
                    phase = 1;
                }
                return;
            }
            if (frames == 0) {
                atPause = game.moonSpin();
            }
            if (++frames >= PAUSED_FRAMES) {
                stoodStill = game.driver().isPaused() && game.moonSpin() == atPause;
                loops = Set.copyOf(game.soundLoops());
                soundsPaused = game.soundsPaused();
                if (frames == PAUSED_FRAMES) {
                    restartAtOnce(game);
                }
            }
        }

        /** Whether every moon sphere's world light list holds exactly the moon anchor's own two lights. */
        private void recordMoonLights(Node moon) {
            List<Light> own = new ArrayList<>();
            moon.getLocalLightList().forEach(own::add);
            List<Geometry> found = new ArrayList<>();
            moon.depthFirstTraversal(spatial -> {
                if (spatial instanceof Geometry geometry && geometry.getName().startsWith("moon-sphere")) {
                    found.add(geometry);
                }
            });
            boolean only = own.size() == 2;
            for (Geometry sphere : found) {
                List<Light> lights = new ArrayList<>();
                sphere.getWorldLightList().forEach(lights::add);
                only &= lights.size() == own.size()
                        && lights.stream().allMatch(light -> own.stream().anyMatch(o -> o == light));
            }
            spheres = found.size();
            ownLightsOnly = only;
        }
    }

    /**
     * Phase 4b-3: {@code original-006} (light clouds, a large ringed gas planet), {@code original-018} (heavy clouds, a
     * normal wide crescent) and {@code original-000} with the developer override {@code giant} (no original tower draws
     * a giant moon); at half texture detail, as the spec allows for tests.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"original-006 LARGE clouds", "original-018 NORMAL clouds", "original-000 GIANT none"})
    void theSkyShowsTheTowersCloudsAndMoonOfEachSizeClass(String spec, @TempDir Path userDir)
            throws InterruptedException {
        String[] parts = spec.split(" ");
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        SkyProbe probe = new SkyProbe();
        Optional<MoonDesign.SizeClass> forced = parts[0].equals("original-000")
                ? Optional.of(MoonDesign.SizeClass.GIANT) : Optional.empty();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(parts[0]), true, userDir, false,
                new SkyOptions(true, Optional.empty(), forced)), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("HeadlessSmokeTest: " + spec + " sky in " + (System.nanoTime() - start) / 1_000_000
                + " ms; moon first seen ready at unthrottled tick " + probe.readyTick + ", "
                + (probe.readyNanos - start) / 1_000_000 + " ms after the app start");

        assertTrue(finished, "the moon's textures, a pause and F9 within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no error");
        assertEquals(MoonDesign.SizeClass.valueOf(parts[1]), probe.size);
        assertEquals(parts[2].equals("clouds"), probe.clouds);
        assertTrue(probe.stoodStill, "paused: the moon stands still");
        assertTrue(probe.skyUnderRoot, "the sky hangs under the root node, outside the scene's lights");
        assertTrue(probe.spheres > 0, "the moon has spheres");
        assertTrue(probe.ownLightsOnly, "the moon's spheres are lit by the moon's own two lights only");
        assertEquals(parts[0].equals("original-018"), probe.loops.contains("background"),
                "Phase 4c: original-018's background drone A (sounds.background 2), none on the others");
        assertTrue(probe.soundsPaused, "Phase 4c: paused, the loops pause");
        assertEquals(Set.of(), probe.loopsAfterF9, "Phase 4c fix wave: F9 stops every loop of the left attempt");
    }

    /**
     * Phase 4b-2: runs a tower with the ambient effect flags in fast-forward until {@value #RUN_TICKS} simulation
     * frames have run, keeping the brightest lightning flash seen, then pauses for {@value #PAUSED_FRAMES} render
     * frames: the strikes, impacts and the flash must stand still.
     */
    private static final class AmbientProbe extends BaseAppState {

        private static final int RUN_TICKS = 80;
        private static final int PAUSED_FRAMES = 20;

        private final CountDownLatch done = new CountDownLatch(1);
        private int pausedFrames;
        private volatile float brightest;
        private volatile int strikes;
        private volatile int impacts;
        private volatile boolean haze;
        private volatile boolean stoodStill = true;
        /** Phase 4b-4: geometries whose fog is not as decision 2 says (world fogged, sky unfogged but the clouds). */
        private final List<String> fogMismatches = new ArrayList<>();
        private volatile boolean fogIsHorizon;
        private int strikesAtPause;
        private int impactsAtPause;
        private float flashAtPause;
        /** Phase 4c: the one-shots played until the pause, and whether the loops paused. */
        private volatile Map<String, Integer> sounds = Map.of();
        private volatile boolean soundsPaused;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || done.getCount() == 0) {
                return;
            }
            brightest = Math.max(brightest, game.lastFlash().scene());
            if (!game.driver().isPaused()) {
                if (game.driver().ticks() >= RUN_TICKS) {
                    game.onAction(GameAction.PAUSE);
                    strikesAtPause = game.lightningStrikes();
                    impactsAtPause = game.meteorImpacts();
                    flashAtPause = game.lastFlash().scene();
                }
                return;
            }
            stoodStill &= game.lightningStrikes() == strikesAtPause && game.meteorImpacts() == impactsAtPause
                    && (pausedFrames == 0 || game.lastFlash().scene() == flashAtPause);
            flashAtPause = game.lastFlash().scene();
            if (++pausedFrames >= PAUSED_FRAMES) {
                strikes = game.lightningStrikes();
                impacts = game.meteorImpacts();
                haze = game.hasHaze();
                sounds = Map.copyOf(game.soundsPlayed());
                soundsPaused = game.soundsPaused();
                checkFog(game);
                done.countDown();
            }
        }

        private void checkFog(GameState game) {
            TobApp app = (TobApp) getApplication();
            ColorRGBA fog = app.assets().fogColour();
            int horizon = TowerColours.of(game.tower()).skyHorizon();
            fogIsHorizon = fog.r == ((horizon >> 16) & 0xFF) / 255f && fog.g == ((horizon >> 8) & 0xFF) / 255f
                    && fog.b == (horizon & 0xFF) / 255f;
            for (Spatial child : app.getRootNode().getChildren()) {
                boolean sky = "sky".equals(child.getName());
                child.depthFirstTraversal(spatial -> {
                    if (spatial instanceof Geometry geometry) {
                        Material material = geometry.getMaterial();
                        boolean fogged = Boolean.TRUE.equals(material.getParamValue("UseFog"))
                                && material.getParamValue("FogColor") == fog;
                        if (fogged != (!sky || geometry.getName().equals("clouds"))) {
                            fogMismatches.add(geometry.getName());
                        }
                    }
                });
            }
        }
    }

    private static AmbientProbe runAmbient(String towerId, Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        AmbientProbe probe = new AmbientProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(towerId), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("HeadlessSmokeTest: " + towerId + " ambient effects in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertTrue(finished, AmbientProbe.RUN_TICKS + " frames and a pause within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no error");
        return probe;
    }

    @Test
    void aLightningAndMeteorTowerStrikesFlashesAndImpactsOnSimulationTime(@TempDir Path userDir)
            throws InterruptedException {
        AmbientProbe probe = runAmbient("original-034", userDir);

        assertTrue(probe.strikes >= 1, "the first strikes come within 16-64 frames: " + probe.strikes);
        assertTrue(probe.impacts >= 1, "the first flights end within 32-64 frames: " + probe.impacts);
        assertTrue(probe.brightest > 0f, "a strike flashes the scene");
        assertTrue(probe.stoodStill, "paused: no strike, no impact, the flash stands still");
        assertFalse(probe.haze, "original-034 has no blue pulse flag");
        assertEquals(probe.strikes, probe.sounds.getOrDefault(SoundBank.EXPLOSION_SHORT, 0),
                "Phase 4c: every strike cracks (0901)");
        assertTrue(probe.sounds.getOrDefault(SoundBank.THUNDER, 0) >= 1, "and thunders 5-8 frames later");
        assertTrue(probe.sounds.getOrDefault(SoundBank.THUNDER, 0) <= probe.strikes, "merged: " + probe.sounds);
        assertEquals(probe.impacts, probe.sounds.getOrDefault(SoundBank.EXPLOSION_DEEP, 0), "every impact: 0903");
        assertTrue(probe.soundsPaused);
        assertEquals(List.of(), probe.fogMismatches, "Phase 4b-4: fog on the world and the clouds, not on the sky");
        assertTrue(probe.fogIsHorizon, "the fog colour is the horizon colour");
    }

    @Test
    void theBluePulseTowerShowsTheGroundHazeAndNoOtherAmbientEffect(@TempDir Path userDir)
            throws InterruptedException {
        AmbientProbe probe = runAmbient("original-058", userDir);

        assertTrue(probe.haze, "the ground haze");
        assertEquals(List.of(), probe.fogMismatches, "Phase 4b-4: the haze and the prism glass are fogged too");
        assertTrue(probe.fogIsHorizon, "the fog colour is the horizon colour");
        assertEquals(-1, probe.strikes, "no lightning flag");
        assertEquals(-1, probe.impacts, "no meteor flag");
        assertEquals(0f, probe.brightest, 0f, "no flash");
    }

    /**
     * Phase 4b-2 fix wave: runs the lightning tower {@code original-043} (a 30-second time limit) in fast-forward
     * until the attempt times out (Run all at frame {@value #THAW_TICK} ends READY, so the time limit runs), keeping
     * the strikes and impacts per simulation frame, checks {@value #END_FRAMES} render frames of the end screen for
     * the flash, then restarts with F9 (the same Run all) and keeps the same counts of the new attempt for
     * {@value #COMPARE_TICKS} simulation frames.
     */
    private static final class EndAndRestartProbe extends BaseAppState {

        private static final int END_FRAMES = 10;
        private static final int COMPARE_TICKS = 120;
        /**
         * Run all ends READY here; the time-out then comes at frame 454, 0.375 into a flash of the tower's lightning
         * (ending READY at frame 2 would time out at frame 237, outside any flash), so the end-screen check bites.
         */
        private static final int THAW_TICK = 219;

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<String> first = new ArrayList<>();
        private final List<String> second = new ArrayList<>();
        private GameState attempt;
        private GameState thawed;
        private int endFrames;
        private volatile boolean noFlashAtTheEnd = true;
        private volatile float effectsFlashAtTheEnd;
        private volatile float brightest;
        /** Whether the ambient light was brightened by a flash (acceptance fix "flash on unlit faces"). */
        private volatile boolean ambientFlashed;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || done.getCount() == 0) {
                return;
            }
            if (attempt == null) {
                attempt = game;
            }
            int ticks = game.driver().ticks();
            if (game != thawed && ticks >= THAW_TICK) {
                thawed = game;
                game.onAction(GameAction.PROGRAM_RUN_ALL);
                game.onRelease(GameAction.PROGRAM_RUN_ALL);
            }
            String counts = game.lightningStrikes() + "/" + game.meteorImpacts();
            if (game != attempt) {
                record(second, ticks, counts);
                if (ticks >= COMPARE_TICKS) {
                    done.countDown();
                }
                return;
            }
            if (!game.driver().simulation().isFinished()) {
                brightest = Math.max(brightest, game.lastFlash().scene());
                ambientFlashed |= game.lastFlash().scene() > 0f && game.ambientColor().r > 0.4f + 0.5f
                        * game.lastFlash().scene();
                record(first, ticks, counts);
                return;
            }
            effectsFlashAtTheEnd = Math.max(effectsFlashAtTheEnd, game.effectsFlashCurve());
            noFlashAtTheEnd &= game.lastFlash().equals(AmbientTuning.Flash.NONE)
                    && game.sunColor().equals(game.ownSunColor()) && game.flashOverlayAlpha() == 0f
                    && game.ambientColor().r == 0.4f;
            if (++endFrames >= END_FRAMES) {
                restartAtOnce(game);
            }
        }

        /** Keeps {@code counts} as the entry of simulation frame {@code ticks} (the first render frame of it). */
        private static void record(List<String> list, int ticks, String counts) {
            while (list.size() <= ticks) {
                list.add(counts);
            }
        }
    }

    @Test
    void noFlashOnTheEndScreenAndF9RepeatsTheSameLightning(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        EndAndRestartProbe probe = new EndAndRestartProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-043"), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("HeadlessSmokeTest: original-043 to the end and F9 in "
                + (System.nanoTime() - start) / 1_000_000 + " ms; effects' flash at the end "
                + probe.effectsFlashAtTheEnd);

        assertTrue(finished, "time-out, end screen and " + EndAndRestartProbe.COMPARE_TICKS + " frames after F9");
        assertEquals(List.of(), app.errors());
        assertTrue(probe.brightest > 0f, "the strikes flash while the attempt runs");
        assertTrue(probe.effectsFlashAtTheEnd > 0f, "the attempt ends during a flash of the effects");
        assertTrue(probe.ambientFlashed, "a flash brightens the ambient light too (faces away from the sun)");
        assertTrue(probe.noFlashAtTheEnd,
                "end screen: no flash, the sun's and the ambient light's own colours, no overlay");
        assertTrue(probe.first.size() > EndAndRestartProbe.COMPARE_TICKS, "the attempt ran past the compared frames");
        assertFalse(probe.first.get(EndAndRestartProbe.COMPARE_TICKS).startsWith("0/"), "strikes to compare");
        assertEquals(probe.first.subList(0, EndAndRestartProbe.COMPARE_TICKS + 1),
                probe.second.subList(0, EndAndRestartProbe.COMPARE_TICKS + 1),
                "F9: the same strikes and impacts at the same simulation frames");
    }

    @Test
    void playsTwoHundredFramesOfRandomInputWithoutAnErrorOrAMissingAsset(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        RandomKeys keys = new RandomKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("HeadlessSmokeTest: " + keys.frames + " frames, " + keys.clicks + " panel clicks in "
                + millis + " ms");

        assertTrue(finished, "200 frames within 60 s; frames run: " + keys.frames);
        assertTrue(keys.clicks > 0, "the panel was clicked");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no simulation error");
    }

    /**
     * Drives an Abandon, start screens, start-again cycle (review finding, Task 11 minor "no test of GameState catch
     * itself" plus the start screens/game lifecycle): after a few simulation frames it opens the pause menu with Esc
     * ({@link GameAction#MENU}), highlights ABANDON with Down (item 7: nothing is highlighted on open), chooses it with
     * Enter, moves to YES with Left and confirms with Enter, waits
     * for the start screens, restarts the same tower and runs a few more frames. A {@link GameState} owns two raw input
     * listeners while shown (keyboard and mouse), the start screens ({@link FrontEndState}) one, and each removes its
     * own when hidden
     * ({@code jME}'s {@link InputManager} has no public accessor for the count, hence the reflection); a leak-free
     * cycle keeps the counts the same throughout.
     */
    private static final class EscCycleKeys extends BaseAppState {

        private static final int FRAMES_PER_PHASE = 10;

        private final CountDownLatch done = new CountDownLatch(1);
        private int phase;
        private int framesInPhase;
        private GameState firstGame;
        private GameState secondGame;
        private int listenersDuringFirstGame = -1;
        private int listenersAtStartScreens = -1;
        private int listenersDuringSecondGame = -1;

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
            TobApp app = (TobApp) getApplication();
            switch (phase) {
                case 0 -> runFirstGame(app);
                case 1 -> waitForStartScreens(app);
                case 2 -> runSecondGame(app);
                default -> { }
            }
        }

        private void runFirstGame(TobApp app) {
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null) {
                return;
            }
            firstGame = current;
            framesInPhase++;
            if (framesInPhase == 1) {
                listenersDuringFirstGame = rawListenerCount(app);
            }
            if (framesInPhase >= FRAMES_PER_PHASE) {
                current.onAction(GameAction.MENU);
                current.onAction(GameAction.BACK);
                current.onAction(GameAction.MENU_CONFIRM);
                current.onAction(GameAction.TURN_LEFT);
                current.onAction(GameAction.MENU_CONFIRM);
                phase = 1;
                framesInPhase = 0;
            }
        }

        private void waitForStartScreens(TobApp app) {
            if (getState(GameState.class) != null || getState(FrontEndState.class) == null) {
                return;
            }
            listenersAtStartScreens = rawListenerCount(app);
            app.startTower(firstGame.tower());
            phase = 2;
        }

        private void runSecondGame(TobApp app) {
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null) {
                return;
            }
            secondGame = current;
            framesInPhase++;
            if (framesInPhase == 1) {
                listenersDuringSecondGame = rawListenerCount(app);
            }
            if (framesInPhase >= FRAMES_PER_PHASE) {
                done.countDown();
            }
        }

        private static int rawListenerCount(TobApp app) {
            try {
                Field field = InputManager.class.getDeclaredField("rawListeners");
                field.setAccessible(true);
                List<?> listeners = (List<?>) field.get(app.getInputManager());
                return listeners.size();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("cannot inspect InputManager.rawListeners", e);
            }
        }
    }

    @Test
    void abandonStartScreensStartAgainCycleLeavesNoInputListenerLeak(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        EscCycleKeys keys = new EscCycleKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir, true), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "Abandon, start screens and restart within 30 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "Abandon and a restart of the same tower are not failures");
        assertTrue(keys.secondGame != keys.firstGame, "restarting attaches a fresh GameState");
        assertEquals(keys.listenersDuringFirstGame - 1, keys.listenersAtStartScreens,
                "the start screens' one listener replaces the game's two: no leak");
        assertEquals(keys.listenersDuringFirstGame, keys.listenersDuringSecondGame,
                "the restarted game's listeners replace the start screens': same count, no leak");
        assertEquals(List.of(), outcomes(userDir, "tutorial-000"), "Phase 3c rework: with recording on, neither the"
                + " abandoned first attempt nor the second one, left when the game was closed, is saved");
    }

    /** The outcomes in the names of the saved recordings of {@code towerId}, oldest first (Phase 3c). */
    private static List<String> outcomes(Path userDir, String towerId) {
        try {
            return new RecordingStore(userDir).files(towerId).stream()
                    .map(file -> file.getFileName().toString().replaceAll("^\\d{8}-\\d{6}-|(-\\d+)?\\.json$", ""))
                    .toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Phase 5a decision 1: F10 pauses and asks ARE YOU SURE? about ABANDON with NO highlighted; Enter (NO) resumes
     * the game (acceptance 2026-10-13), Esc opens the menu and P resumes. Then F9 asks about RESTART; Left and Enter
     * (YES) restart the same tower.
     */
    private static final class ExitKeys extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private GameState firstGame;
        private GameState secondGame;
        private int frames;
        private volatile PauseMenu.Level levelAfterF10;
        private volatile PauseMenu.Choice choiceAfterF10;
        private volatile boolean yesAfterF10 = true;
        private volatile boolean pausedAfterF10;
        private volatile PauseMenu.Level levelAfterNo;
        private volatile boolean runningAgain;
        private volatile boolean runningAfterNo;
        private volatile PauseMenu.Choice choiceAfterF9;

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
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null) {
                return;
            }
            if (firstGame == null && ++frames >= 10) {
                firstGame = current;
                current.onAction(GameAction.ABANDON_TOWER);
                levelAfterF10 = current.menu().level();
                choiceAfterF10 = current.menu().choice();
                yesAfterF10 = current.menu().yesHighlighted();
                pausedAfterF10 = current.driver().isPaused();
                current.onAction(GameAction.MENU_CONFIRM);
                levelAfterNo = current.menu().level();
                runningAfterNo = !current.driver().isPaused();
                current.onAction(GameAction.MENU);
                current.onAction(GameAction.PAUSE);
                runningAgain = !current.driver().isPaused();
                current.onAction(GameAction.RESTART_TOWER);
                choiceAfterF9 = current.menu().choice();
                current.onAction(GameAction.TURN_LEFT);
                current.onAction(GameAction.MENU_CONFIRM);
            } else if (firstGame != null && current != firstGame) {
                secondGame = current;
                done.countDown();
            }
        }
    }

    @Test
    void f10AndF9AskBeforeAbandoningOrRestarting(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        ExitKeys keys = new ExitKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "F10, NO, F9, YES within 30 s");
        assertEquals(List.of(), app.errors());
        assertEquals(PauseMenu.Level.CONFIRM, keys.levelAfterF10, "F10 asks at once");
        assertEquals(PauseMenu.Choice.ABANDON, keys.choiceAfterF10);
        assertFalse(keys.yesAfterF10, "NO preselected");
        assertTrue(keys.pausedAfterF10, "the game pauses while asking");
        assertEquals(PauseMenu.Level.CLOSED, keys.levelAfterNo, "NO after F10 leaves the menu");
        assertTrue(keys.runningAfterNo, "NO after F10 resumes the game (acceptance 2026-10-13)");
        assertTrue(keys.runningAgain, "Esc and P resume");
        assertEquals(PauseMenu.Choice.RESTART, keys.choiceAfterF9);
        assertEquals(keys.firstGame.tower().id(), keys.secondGame.tower().id(), "YES restarted the same tower");
    }

    /**
     * Pauses with the Pause action after a few frames, then clicks the menu title EXIT, the RESTART row and the YES
     * row (Phase 3b-2 spec §7 smoke test), and waits for the same tower to start afresh.
     */
    private static final class RestartByMouse extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private GameState firstGame;
        private GameState secondGame;
        private int frames;

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
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null || current.layout() == null) {
                return;
            }
            if (firstGame == null && ++frames >= 10) {
                firstGame = current;
                current.onAction(GameAction.PAUSE);
                click(current, current.layout().menuTitle());
                click(current, current.layout().menuRow(1));
                click(current, ConfirmDialogLayout.of(current.layout(), current.isCollapsed())
                        .button(ConfirmDialogLayout.Answer.YES));
            } else if (firstGame != null && current != firstGame) {
                secondGame = current;
                done.countDown();
            }
        }

        private static void click(GameState game, PanelLayout.Rect rect) {
            game.onLeftPressed(rect.x() + rect.width() / 2f, rect.y() + rect.height() / 2f);
            game.onLeftReleased();
        }
    }

    @Test
    void pauseMenuRestartByMouseStartsAfresh(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        RestartByMouse keys = new RestartByMouse();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir, true), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "Pause, Exit, Restart, YES within 30 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "a restart is not a failure");
        assertEquals(keys.firstGame.tower().id(), keys.secondGame.tower().id(), "the same tower");
        assertEquals(PauseMenu.Level.CLOSED, keys.firstGame.menu().level(), "YES closed the menu");
        assertNull(keys.firstGame.driver(), "the first attempt has been left");
        assertEquals(List.of(), outcomes(userDir, "tutorial-000"),
                "Phase 3c rework: with recording on, a restarted attempt is not saved");
    }

    /**
     * Acceptance fixes 2 and 6: a window without area (minimized: the camera reports 0 × 0) keeps the last valid
     * layout and frustum; and the X at the left end of the title bar acts like Esc – while playing it pauses and opens
     * the EXIT menu, with the menu open it goes back one level.
     */
    private static final class MinimizeAndExitButton extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private int frames;
        private PanelLayout before;
        private PanelLayout whileMinimized;
        private float frustumWhileMinimized;
        private boolean pausedByX;
        private PauseMenu.Level levelAfterFirstX;
        private int highlightAfterFirstX;
        private PauseMenu.Level levelAfterSecondX;
        private boolean stillPaused;
        private PauseMenu.Level levelAfterModalClicks;
        private boolean answeredNo;

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || game.layout() == null || done.getCount() == 0) {
                return;
            }
            frames++;
            com.jme3.renderer.Camera cam = getApplication().getCamera();
            if (frames == 10) {
                before = game.layout();
                cam.resize(0, 0, false);
            } else if (frames == 13) {
                whileMinimized = game.layout();
                frustumWhileMinimized = cam.getFrustumRight();
                cam.resize(before.width(), before.height(), false);
            } else if (frames == 15) {
                click(game, game.layout().exitButton());
                pausedByX = game.driver().isPaused();
                levelAfterFirstX = game.menu().level();
                highlightAfterFirstX = game.menu().highlightedRow();
                click(game, game.layout().exitButton());
                levelAfterSecondX = game.menu().level();
                stillPaused = game.driver().isPaused();
                // Round 2, item 2: the confirmation dialog is modal; only YES and NO answer it.
                game.onAction(GameAction.MENU);
                game.onAction(GameAction.BACK);
                game.onAction(GameAction.MENU_CONFIRM);
                ConfirmDialogLayout dialog = ConfirmDialogLayout.of(game.layout(), game.isCollapsed());
                click(game, game.layout().exitButton());
                click(game, game.layout().menuTitle());
                click(game, game.layout().caption(game.isCollapsed()));
                click(game, dialog.messageGroup());
                levelAfterModalClicks = game.menu().level();
                click(game, dialog.button(ConfirmDialogLayout.Answer.NO));
                answeredNo = game.menu().level() == PauseMenu.Level.EXIT && game.driver().isPaused();
                done.countDown();
            }
        }

        private static void click(GameState game, PanelLayout.Rect rect) {
            game.onLeftPressed(rect.x() + 1f, rect.y() + rect.height() / 2f);
            game.onLeftReleased();
        }
    }

    @Test
    void aMinimizedWindowKeepsTheLayoutAndTheTitleBarXActsLikeEsc(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        MinimizeAndExitButton keys = new MinimizeAndExitButton();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "minimize, restore, two X clicks within 30 s");
        assertEquals(List.of(), app.errors());
        assertTrue(keys.before == keys.whileMinimized, "0 x 0: the last valid layout stays");
        assertFalse(Float.isNaN(keys.frustumWhileMinimized), "no NaN aspect");
        assertTrue(keys.pausedByX, "X while playing pauses");
        assertEquals(PauseMenu.Level.EXIT, keys.levelAfterFirstX, "and opens the EXIT menu");
        assertEquals(-1, keys.highlightAfterFirstX, "item 7: with nothing highlighted");
        assertEquals(PauseMenu.Level.CLOSED, keys.levelAfterSecondX, "X with the menu open: back one level");
        assertTrue(keys.stillPaused, "like Esc: closing the menu does not resume");
        assertEquals(PauseMenu.Level.CONFIRM, keys.levelAfterModalClicks, "X, title, caption, question: ignored");
        assertTrue(keys.answeredNo, "NO in the dialog: back to the EXIT menu, still paused");
    }

    /**
     * Injects a {@link RuntimeException} into one frame's view update (review finding, Task 11: {@link GameState}'s
     * broad catch in {@code update(float)} had no behavioural test, only {@code SimFailure} from the simulation
     * step) by nulling the running {@link GameState}'s private {@code world} field by reflection (no production API
     * triggers this cheaply), then checks that the game returns to the start screens instead of crashing the
     * application.
     */
    private static final class FaultInjectionKeys extends BaseAppState {

        private static final int FRAMES_BEFORE_FAULT = 10;

        private final CountDownLatch done = new CountDownLatch(1);
        private int frames;
        private boolean injected;

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
            if (injected) {
                if (getState(GameState.class) == null && getState(FrontEndState.class) != null) {
                    done.countDown();
                }
                return;
            }
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null) {
                return;
            }
            frames++;
            if (frames >= FRAMES_BEFORE_FAULT) {
                injectFault(current);
                injected = true;
            }
        }

        private static void injectFault(GameState game) {
            try {
                Field field = GameState.class.getDeclaredField("world");
                field.setAccessible(true);
                field.set(game, null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("cannot null GameState.world for the fault injection", e);
            }
        }
    }

    @ParameterizedTest(name = "recording {0}")
    @ValueSource(booleans = {true, false})
    void injectedRuntimeExceptionInAViewUpdateReturnsToTheStartScreens(boolean record, @TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        FaultInjectionKeys keys = new FaultInjectionKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir, record), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "the injected failure returns to the start screens within 30 s");
        assertEquals(List.of(), app.errors(), "GameState.update catches the exception; it never reaches the app");
        assertFalse(app.messages().isEmpty(), "the start screens shows the failure message");
        assertEquals(record ? List.of("crash") : List.of(), outcomes(userDir, "tutorial-000"),
                "Phase 3c rework: a crash is saved only while recording is on");
    }

    /**
     * Final review M2: holds the panel's FIRE button with the mouse (repeats every frame while held, like every
     * queued panel action), then moves the cursor off the button without releasing it, and counts
     * {@link GameState#commandsSent()} just before and well after the move. A due simulation step inside
     * {@code driver.update} must not sneak in one more repeat before the "cursor left the held button" check runs
     * (spec §4.2); fixed, the count is unchanged across the move, since the hold is released before that frame's
     * step and no further repeat is ever sent once released.
     */
    private static final class HoldLeaveKeys extends BaseAppState {

        private static final int FRAMES_HELD_BEFORE_MOVE = 2;
        private static final int FRAMES_AFTER_MOVE = 5;

        private final CountDownLatch done = new CountDownLatch(1);
        private int phase;
        private int framesInPhase;
        private int before;
        private int after;

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
            GameState current = getState(GameState.class);
            if (current == null || current.driver() == null || current.layout() == null) {
                return;
            }
            switch (phase) {
                case 0 -> {
                    PanelLayout.Rect rect = current.layout().button(PanelButton.FIRE);
                    float x = rect.x() + rect.width() / 2f;
                    float y = rect.y() + rect.height() / 2f;
                    // onLeftPressed is a direct call (as the smoke test's own clicks do), bypassing the raw
                    // listener: it does not itself move mouse.x()/y(), so the leave-check (which reads those) must
                    // see the click's own position first, or it would fire on the very next frame regardless of M2.
                    current.mouse().onMouseMotionEvent(new MouseMotionEvent((int) x, (int) y, 0, 0, 0, 0));
                    current.onLeftPressed(x, y);
                    phase = 1;
                }
                case 1 -> {
                    if (++framesInPhase >= FRAMES_HELD_BEFORE_MOVE) {
                        before = current.commandsSent();
                        // far outside every panel button (bottom area): the title bar, top-right corner.
                        current.mouse().onMouseMotionEvent(new MouseMotionEvent(current.layout().width() - 1,
                                current.layout().height() - 1, 0, 0, 0, 0));
                        phase = 2;
                        framesInPhase = 0;
                    }
                }
                case 2 -> {
                    if (++framesInPhase >= FRAMES_AFTER_MOVE) {
                        after = current.commandsSent();
                        done.countDown();
                    }
                }
                default -> { }
            }
        }
    }

    @Test
    void movingOffAHeldPanelButtonBeforeTheDueStepSendsNoExtraRepeat(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/tutorial.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        HoldLeaveKeys keys = new HoldLeaveKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("tutorial-000"), true, userDir), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(30, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "hold, move off and settle within 30 s");
        assertEquals(List.of(), app.errors());
        assertEquals(keys.before, keys.after,
                "M2: moving the cursor off the held FIRE button must stop the repeat before the next due sim step, "
                        + "not after it");
    }

    /**
     * Phase 3c spec §5: plays {@code original-000} with the keys of {@link SoftlySoftlySolutionTest} through the
     * headless client (one key every 40 simulation frames, pressed and released before the frame that sends it) and
     * keeps the live simulation's final state and the caption strip of the first frame. With {@code thenF12}, it
     * then presses F12 on the finished attempt and F9, and keeps the caption of the restarted attempt (rework).
     */
    private static final class SoftlySoftlyByKeys extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final boolean thenF12;
        private GameState first;
        private int next;
        private String firstCaption;
        private String finalState;
        private int finalTicks;
        private String captionAfterF12;
        private String restartedCaption;
        /** The most explosions seen at once in the first attempt (Phase 4b-1). */
        private volatile int explosions;
        /** Phase 4c: the first attempt's one-shots and every loop it ran. */
        private volatile Map<String, Integer> sounds = Map.of();
        private volatile Set<String> loops = Set.of();

        SoftlySoftlyByKeys(boolean thenF12) {
            this.thenF12 = thenF12;
        }

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
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || done.getCount() == 0) {
                return;
            }
            if (first == null) {
                first = game;
            }
            if (game == first) {
                explosions = Math.max(explosions, game.activeExplosions());
            }
            if (game != first) {
                if (game.caption() != null) {
                    restartedCaption = game.caption();
                    done.countDown();
                }
                return;
            }
            if (firstCaption == null) {
                firstCaption = game.caption();
            }
            SimDriver driver = game.driver();
            if (finalState != null) {
                captionAfterF12 = game.caption();
                restartAtOnce(game);
                return;
            }
            if (driver.simulation().isFinished()) {
                sounds = Map.copyOf(game.soundsPlayed());
                loops = Set.copyOf(game.soundLoopsEver());
                finalState = Snapshots.of(driver.simulation());
                finalTicks = driver.ticks();
                if (thenF12) {
                    game.onAction(GameAction.TOGGLE_RECORDING);
                } else {
                    done.countDown();
                }
                return;
            }
            if (next < SoftlySoftlySolutionTest.KEYS.size()
                    && driver.ticks() >= next * SoftlySoftlySolutionTest.FRAMES_PER_KEY) {
                GameAction key = SoftlySoftlySolutionTest.KEYS.get(next++);
                game.onAction(key);
                game.onRelease(key);
            }
        }
    }

    /** Starts {@code original-000} headless with {@code keys}, waits for them and stops the game (Phase 3c). */
    private static TobApp playOriginal000(Path assets, Path userDir, boolean record, SoftlySoftlyByKeys keys)
            throws InterruptedException {
        TobApp app = new TobApp(new AppConfig(assets, Optional.of("original-000"), true, userDir, record), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("HeadlessSmokeTest: original-000 played in " + (System.nanoTime() - start) / 1_000_000
                + " ms");
        assertTrue(finished, "original-000 completed within 60 s");
        assertEquals(List.of(), app.errors());
        return app;
    }

    @Test
    void aSolutionPlayedInTheClientIsSavedAndReplaysHeadlessToTheSameWorld(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        SoftlySoftlyByKeys keys = new SoftlySoftlyByKeys(false);

        playOriginal000(assets, userDir, true, keys);

        assertTrue(keys.explosions >= 1, "Phase 4b-1: the zapped fixed zapper breaks apart");
        System.out.println("HeadlessSmokeTest: original-000 sounds " + keys.sounds + ", loops " + keys.loops);
        assertEquals(1, keys.sounds.getOrDefault(SoundBank.ZAP, 0), "Phase 4c: the zapper's one shot");
        assertEquals(1, keys.sounds.getOrDefault(SoundBank.EXPLOSION, 0), "the zapped fixed zapper explodes (0900)");
        assertEquals(3, keys.sounds.getOrDefault(SoundBank.KLONDIKE, 0), "three klondikes");
        assertTrue(keys.sounds.getOrDefault(SoundBank.ENEMY_ZAPPER, 0) >= 1, "the fixed zapper's cadence");
        assertTrue(keys.sounds.getOrDefault(SoundBank.SPIDER_STEP, 0) >= 8, "the spiders walk");
        assertTrue(keys.sounds.getOrDefault(SoundBank.LIFT_STOP, 0) >= 1, "lift rides stop");
        assertTrue(keys.loops.stream().anyMatch(key -> key.startsWith("ride-")), "a lift ride's loop");
        assertFalse(keys.sounds.containsKey(SoundBank.EXPLOSION_BIG), "no spider was destroyed");
        assertEquals("REC  KLONDIKES 0/3", keys.firstCaption, "tob.record=true: REC in the caption strip");
        assertEquals(List.of("completed"), outcomes(userDir, "original-000"));
        Recording recorded = new RecordingReader().read(new RecordingStore(userDir).files("original-000").get(0));
        assertEquals(keys.finalTicks, recorded.length(), "every step of the attempt is recorded");
        assertEquals(SoftlySoftlySolutionTest.KEYS.size(), recorded.entries().size());
        TowerDefinition tower = TowerAssets.allTowers().get(0);
        assertEquals("original-000", tower.id());
        Replayer replayer = new Replayer(recorded, tower);
        replayer.run(new ArrayList<>());
        assertEquals(Rules.EXIT_COMPLETED, replayer.simulation().exitCode());
        assertEquals(recorded.length(), replayer.position(), "completed at the same step");
        assertEquals(keys.finalState, Snapshots.of(replayer.simulation()), "the same final world as the live run");
        assertEquals(Optional.empty(), SolutionCheck.problem(recorded, tower));
        Recording committed = new RecordingReader().read(Path.of("src/test/resources/solutions/original-000.json"));
        assertEquals(committed.entries(), recorded.entries(), "the client records exactly what the script sends");
        assertEquals(committed.length(), recorded.length());
    }

    /**
     * Phase 3c rework (author's decision of 2026-10-09): without {@code tob.record} nothing is recorded, not even a
     * completed attempt; F12 turns recording on for the next attempt ("REC NEXT" until then), and the attempt
     * restarted with F9 is recorded from its start ("REC"); left when the game is closed, it is not saved.
     */
    @Test
    void recordingIsOffByDefaultAndF12TurnsItOnFromTheNextAttempt(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        SoftlySoftlyByKeys keys = new SoftlySoftlyByKeys(true);

        TobApp app = playOriginal000(assets, userDir, false, keys);

        assertEquals("KLONDIKES 0/3", keys.firstCaption, "off by default: no REC");
        assertEquals("REC NEXT  KLONDIKES 3/3", keys.captionAfterF12, "F12 on a running attempt: the next one");
        assertEquals("REC  KLONDIKES 0/3", keys.restartedCaption, "the restarted attempt is recorded");
        assertTrue(app.recordingSwitch().isOn());
        assertFalse(Files.exists(userDir.resolve("recordings")),
                "the completed attempt was not recorded, the restarted one was left unfinished");
    }
}
