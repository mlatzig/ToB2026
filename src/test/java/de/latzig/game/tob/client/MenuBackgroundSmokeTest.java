package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.logic.frontend.DemoPlayer;
import de.latzig.game.tob.client.logic.frontend.DemoScripts;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.client.view.SkyView;
import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Phase 5a spec §4 (headless): the menu background plays the original Demo Sequence: demo 17 first; leaving the
 * background removes its scene. Small extra (author decision A, 2026-10-14): one demo tower per menu visit — a
 * script end (plus the linger) restarts the same tower instead of advancing to the next one; only a return to the
 * menus (a new visit) advances the sequence.
 */
class MenuBackgroundSmokeTest {

    private static final int UNTHROTTLED = Integer.MAX_VALUE;

    /**
     * Phase 5a (Task 15): a normal start shows the start screens, whose loader attaches the app's own menu
     * background; these probes test their own instance, so they leave the start screens at their first frame,
     * before the loader gets that far (it attaches the background at its third step).
     */
    private static void leaveStartScreens(TobApp app) {
        FrontEndState frontEnd = app.getStateManager().getState(FrontEndState.class);
        if (frontEnd != null) {
            app.getStateManager().detach(frontEnd);
        }
    }

    private static final class Probe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private MenuBackgroundState background;
        private volatile int initialDemo = -1;
        private volatile DemoPlayer initialPlayer;
        private volatile SkyView initialSky;
        private volatile int firstScriptSteps;
        private volatile boolean firstScriptFinished;
        private volatile boolean sceneWhileShown;
        private boolean scriptEndSeen;
        // captured at the moment of the restart (not read after app.stop(), which itself leaves the menus and so
        // advances the sequence through onDisable, same as every other menu exit):
        private volatile int demoAfterRestart = -1;
        private volatile int playedAfterRestart = -1;
        private volatile int restartedCount = -1;
        private volatile DemoPlayer playerAfterRestart;
        private volatile SkyView skyAfterRestart;
        private volatile boolean sceneStillAttachedAfterRestart;

        Probe(List<TowerDefinition> demos) {
            this.demos = demos;
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
            TobApp app = (TobApp) getApplication();
            if (background == null) {
                leaveStartScreens(app);
                background = new MenuBackgroundState(app.assets(), demos, true);
                getStateManager().attach(background);
                return;
            }
            if (background.player() == null) {
                return;
            }
            if (initialDemo < 0) {
                initialDemo = background.demo();
                initialPlayer = background.player();
                initialSky = background.sky();
                sceneWhileShown = app.getRootNode().getChild("menu-background") != null;
            }
            if (!scriptEndSeen && background.player().isFinished()) {
                scriptEndSeen = true;
                firstScriptSteps = background.player().step();
                firstScriptFinished = true;
            }
            if (scriptEndSeen && restartedCount < 0 && background.restarted() > 0) {
                demoAfterRestart = background.demo();
                playedAfterRestart = background.played();
                restartedCount = background.restarted();
                playerAfterRestart = background.player();
                skyAfterRestart = background.sky();
                sceneStillAttachedAfterRestart = app.getRootNode().getChild("menu-background") != null;
                done.countDown();
            }
        }
    }

    /**
     * Controller addition (Task 14): leaving the menu background for a game releases its scene, sky and shadow
     * renderer; returning to the menus (the same state attached again, as {@code TobApp} does) resumes with the next
     * demo tower of the sequence.
     */
    private static final class ResumeProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private MenuBackgroundState background;
        private int phase;
        private int wait;
        private int rootChildren = -1;
        private int processors = -1;
        private final List<Integer> shown = new ArrayList<>();
        private final List<Boolean> clean = new ArrayList<>();

        ResumeProbe(List<TowerDefinition> demos) {
            this.demos = demos;
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
            TobApp app = (TobApp) getApplication();
            switch (phase) {
                case 0 -> {
                    rootChildren = app.getRootNode().getQuantity();
                    processors = app.getViewPort().getProcessors().size();
                    leaveStartScreens(app);
                    background = new MenuBackgroundState(app.assets(), demos, true);
                    getStateManager().attach(background);
                    phase = 1;
                }
                case 1, 3, 5 -> {
                    if (background.player() != null && background.player().step() > 0) {
                        shown.add(background.demo());
                        getStateManager().detach(background);
                        wait = 0;
                        phase++;
                    }
                }
                case 2, 4, 6 -> {
                    if (++wait == 3) {
                        clean.add(app.getRootNode().getQuantity() == rootChildren
                                && app.getViewPort().getProcessors().size() == processors
                                && app.getRootNode().getChild("menu-background") == null
                                && app.getRootNode().getChild("sky") == null);
                        if (phase == 6) {
                            phase = 7;
                            done.countDown();
                        } else {
                            getStateManager().attach(background);
                            phase++;
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * Review fix (Important #1): a {@code RuntimeException} mid-frame must log, release the broken demo and show the
     * next one of the sequence, at the latest the following frame, instead of leaving the background blank forever.
     */
    private static final class FailFrameProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private MenuBackgroundState background;
        private boolean injected;
        private volatile int demoBeforeFailure = -1;
        private volatile int demoAfterRecovery = -1;
        private volatile boolean recoveredSameFrame;

        FailFrameProbe(List<TowerDefinition> demos) {
            this.demos = demos;
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
            TobApp app = (TobApp) getApplication();
            if (background == null) {
                leaveStartScreens(app);
                background = new MenuBackgroundState(app.assets(), demos, true);
                getStateManager().attach(background);
                return;
            }
            if (!injected) {
                if (background.player() != null && background.player().step() > 0) {
                    demoBeforeFailure = background.demo();
                    background.failNextFrame();
                    injected = true;
                }
                return;
            }
            demoAfterRecovery = background.demo();
            recoveredSameFrame = background.player() != null;
            done.countDown();
        }
    }

    /**
     * Review fix (Important #2): a demo that fails to start must not be retried forever; the sequence advances so
     * the next attempt uses the next demo, tried right away.
     */
    private static final class FailStartProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private final Set<Integer> failing;
        private MenuBackgroundState background;
        private volatile int firstDemoShown = -1;
        private volatile int firstDemoPlayed = -1;

        FailStartProbe(List<TowerDefinition> demos, Set<Integer> failing) {
            this.demos = demos;
            this.failing = failing;
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
            TobApp app = (TobApp) getApplication();
            if (background == null) {
                leaveStartScreens(app);
                background = new MenuBackgroundState(app.assets(), demos, true);
                background.failDemosToStart(failing);
                getStateManager().attach(background);
                return;
            }
            if (background.player() == null) {
                return;
            }
            firstDemoShown = background.demo();
            firstDemoPlayed = background.played();
            done.countDown();
        }
    }

    /**
     * Review fix (Important #2, tight-loop guard): if every demo of the sequence fails to start, {@code show()} must
     * give up after one full cycle instead of looping forever, and stay blank.
     */
    private static final class FailAllStartsProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private MenuBackgroundState background;
        private int frames;
        private volatile boolean stillBlank;

        FailAllStartsProbe(List<TowerDefinition> demos) {
            this.demos = demos;
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
            TobApp app = (TobApp) getApplication();
            if (background == null) {
                leaveStartScreens(app);
                background = new MenuBackgroundState(app.assets(), demos, true);
                background.failDemosToStart(Set.copyOf(DemoScripts.SEQUENCE));
                getStateManager().attach(background);
                return;
            }
            if (++frames == 3) {
                stillBlank = background.player() == null;
                done.countDown();
            }
        }
    }

    /**
     * Phase 5c-2 decisions 5, 7 and 8: while the flow wants an interlude, the background plays that demo with the
     * chase camera (north, level, its axis in the band above the result window); the script runs once, no restart,
     * also after the failure demo's simulation has ended; its sounds are muted while the Music switch is on. When the
     * interlude is no longer wanted, the Demo Sequence's tower follows with the usual frustum, and with the switch off
     * its sounds play.
     */
    private static final class InterludeProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private final AtomicReference<OptionalInt> wanted = new AtomicReference<>(OptionalInt.of(7));
        private MenuBackgroundState background;
        private int phase;
        private int frames;
        private DemoPlayer interludePlayer;
        private volatile int interludeDemo = -1;
        private volatile boolean lookingNorth;
        private volatile boolean axisAboveCentre;
        private volatile boolean eyeBehindAndAbove;
        private volatile boolean samePlayerAfterTheEnd;
        private volatile boolean sceneAfterTheEnd;
        private volatile int restartsInInterlude = -1;
        private volatile int playedInInterlude = -1;
        private volatile boolean silentWhileMusic;
        private volatile int menuDemo = -1;
        private volatile boolean symmetricAgain;
        private volatile boolean soundsWithoutMusic;

        InterludeProbe(List<TowerDefinition> demos) {
            this.demos = demos;
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
            TobApp app = (TobApp) getApplication();
            Camera cam = app.getCamera();
            switch (phase) {
                case 0 -> {
                    leaveStartScreens(app);
                    background = new MenuBackgroundState(app.assets(), demos, true, wanted::get);
                    getStateManager().attach(background);
                    phase = 1;
                }
                case 1 -> {
                    if (background.player() == null || background.player().step() == 0) {
                        return;
                    }
                    interludePlayer = background.player();
                    interludeDemo = background.demo();
                    lookingNorth = cam.getDirection().distance(new Vector3f(0f, 0f, -1f)) < 1e-4f;
                    axisAboveCentre = cam.getFrustumTop() < -cam.getFrustumBottom();
                    Vector3f eye = cam.getLocation();
                    eyeBehindAndAbove = eye.z > 9f && eye.y > 0.5f;
                    phase = 2;
                }
                case 2 -> {
                    if (background.player().isFinished() && background.simulation().isFinished() && ++frames > 60) {
                        samePlayerAfterTheEnd = background.player() == interludePlayer;
                        sceneAfterTheEnd = app.getRootNode().getChild("menu-background") != null;
                        restartsInInterlude = background.restarted();
                        playedInInterlude = background.played();
                        silentWhileMusic = background.sound().played().isEmpty()
                                && background.sound().everLooped().isEmpty();
                        wanted.set(OptionalInt.empty());
                        app.settings().preview(app.settings().current().withMusic(false));
                        frames = 0;
                        phase = 3;
                    }
                }
                case 3 -> {
                    if (background.player() == null || ++frames < 40) {
                        return;
                    }
                    menuDemo = background.demo();
                    symmetricAgain = Math.abs(cam.getFrustumTop() + cam.getFrustumBottom()) < 1e-6f;
                    soundsWithoutMusic = !background.sound().everLooped().isEmpty()
                            || !background.sound().played().isEmpty();
                    phase = 4;
                    done.countDown();
                }
                default -> {
                }
            }
        }
    }

    @Test
    void anInterludePlaysOnceWithTheChaseCameraAndMutedSoundsThenTheSequenceReturns(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        InterludeProbe probe = new InterludeProbe(demos);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("MenuBackgroundSmokeTest: interlude and return in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertTrue(finished, "the interlude and the return within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(7, probe.interludeDemo, "the wanted interlude, Pusher Dies 2");
        assertTrue(probe.lookingNorth, "decision 8: the fixed view north, level");
        assertTrue(probe.axisAboveCentre, "the axis in the band above the result window");
        assertTrue(probe.eyeBehindAndAbove, "behind (south of) and above the spider");
        assertTrue(probe.sceneAfterTheEnd, "the scene stays after the failure demo's simulation has ended");
        assertTrue(probe.samePlayerAfterTheEnd, "decision 7: the script runs once");
        assertEquals(0, probe.restartsInInterlude, "no restart in an interlude");
        assertEquals(1, probe.playedInInterlude, "one tower loaded");
        assertTrue(probe.silentWhileMusic, "decision 5: no demo sound while the music is on");
        assertEquals(DemoScripts.SEQUENCE.get(0).intValue(), probe.menuDemo, "the sequence's tower follows");
        assertTrue(probe.symmetricAgain, "the menu's frustum again");
        assertTrue(probe.soundsWithoutMusic, "with the music off the demo sounds play");
    }

    /**
     * Small extra (author decision A, 2026-10-14): a script end (plus the linger) restarts the same demo tower
     * instead of advancing to the next one of the sequence — the simulation and the demo player go back to the
     * start, but the scene, the views and the generated sky textures stay (no new tower load, no scene rebuild).
     */
    @Test
    void aScriptEndRestartsTheSameTowerInsteadOfAdvancingToTheNextOne(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        Probe probe = new Probe(demos);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("MenuBackgroundSmokeTest: script end and restart in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertTrue(finished, "the script end and the restart happen within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(17, probe.initialDemo, "the original's order starts with demo 17");
        assertTrue(probe.sceneWhileShown);
        assertTrue(probe.firstScriptFinished, "demo 17's script ran to its end");
        assertEquals(15, probe.firstScriptSteps, "all 15 bytes of Grabber Demo");
        assertEquals(17, probe.demoAfterRestart, "the restart keeps the same demo, it does not advance");
        assertEquals(1, probe.playedAfterRestart, "the restart is not a new tower load");
        assertEquals(1, probe.restartedCount, "exactly one restart happened");
        assertSame(probe.initialSky, probe.skyAfterRestart, "the sky and its generated textures are kept");
        assertTrue(probe.playerAfterRestart != probe.initialPlayer, "the demo player starts afresh");
        assertTrue(!probe.playerAfterRestart.isFinished(), "the restarted script is running again, not finished");
        assertTrue(probe.playerAfterRestart.step() < probe.firstScriptSteps,
                "the restarted script is back near its first byte, not where the old one ended");
        assertTrue(probe.sceneStillAttachedAfterRestart, "the scene stays attached while the menu is shown");
    }

    @Test
    void leavingReleasesTheSceneAndReturningResumesWithTheNextDemo(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        ResumeProbe probe = new ResumeProbe(demos);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "leave and return within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(17, 12, 13), probe.shown,
                "the order of towers over several visits: each return to the menus advances the sequence");
        assertEquals(List.of(true, true, true), probe.clean, "no scene, sky or shadow renderer left after leaving");
    }

    @Test
    void aRuntimeExceptionMidFrameReleasesTheBrokenDemoAndShowsTheNextOneTheSameFrame(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        FailFrameProbe probe = new FailFrameProbe(demos);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "recovery within 60 s");
        assertEquals(List.of(), app.errors(), "the exception is logged and swallowed, not an application error");
        assertEquals(17, probe.demoBeforeFailure, "demo 17 was running when the failure was injected");
        assertTrue(probe.recoveredSameFrame, "the next demo is already showing when update() catches the failure");
        assertEquals(12, probe.demoAfterRecovery, "the sequence moved on to the next demo, not a repeat of 17");
    }

    @Test
    void aDemoThatFailsToStartIsSkippedInFavourOfTheNextOneRightAway(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        FailStartProbe probe = new FailStartProbe(demos, Set.of(17));
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "the next demo shows within 60 s");
        assertEquals(List.of(), app.errors(), "the start failure is logged and swallowed, not an application error");
        assertEquals(12, probe.firstDemoShown, "demo 17 (first of the sequence) is skipped, not retried");
        assertEquals(1, probe.firstDemoPlayed, "only the successful start (demo 12) counts as played");
    }

    @Test
    void aSequenceWhereEveryDemoFailsToStartGivesUpAfterOneCycleAndStaysBlank(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        FailAllStartsProbe probe = new FailAllStartsProbe(demos);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "show() returns promptly instead of looping forever");
        assertEquals(List.of(), app.errors(), "giving up is logged, not an application error");
        assertTrue(probe.stillBlank, "no demo could start, so the background stays empty");
    }

    /**
     * Task 6 review fix (Important 1): Retry on the result window detaches the background in the frame the game starts;
     * jME still updates the detached state in that frame, which must neither build a menu tower nor advance the
     * sequence. Path: the app's own background, menu → Play → New Profile → Overview → Briefing → the first tower →
     * abandon → result window (interlude) → RETRY → the tower → abandon → result window (interlude) → Overview: two
     * interludes and one menu tower are loaded after the game start, and the Overview shows the sequence's next tower.
     */
    private static final class RetryProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private int step;
        private boolean abandoned;
        private volatile int menuDemoBefore = -1;
        private volatile int playedAtStart = -1;
        private volatile int playedAtEnd = -1;
        private volatile int menuDemoAfter = -1;
        private volatile int results;

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
            MenuBackgroundState background = getState(MenuBackgroundState.class);
            switch (flow.screen()) {
                case MENU -> {
                    if (step == 0 && background != null && background.player() != null) {
                        menuDemoBefore = background.demo();
                        step = 1;
                        flow.key(UiKey.ENTER);
                    }
                }
                case PLAY -> {
                    if (step == 1) {
                        step = 2;
                        flow.key(UiKey.ENTER);
                    }
                }
                case NEW_GAME -> {
                    if (step == 2) {
                        step = 3;
                        for (char c : "Retry".toCharArray()) {
                            flow.typed(c);
                        }
                        flow.key(UiKey.ENTER);
                        flow.key(UiKey.ENTER);
                    }
                }
                case OVERVIEW -> {
                    if (step == 3) {
                        step = 4;
                        flow.key(UiKey.ENTER);
                    } else if (step == 6 && background != null && background.player() != null) {
                        menuDemoAfter = background.demo();
                        playedAtEnd = background.played();
                        step = 7;
                        done.countDown();
                    }
                }
                case BRIEFING -> {
                    if (step == 4 && flow.isEnabled(GameFlow.Widget.PLAY) && background != null) {
                        playedAtStart = background.played();
                        step = 5;
                        flow.key(UiKey.ENTER);
                    }
                }
                case GAME -> {
                    GameState game = getState(GameState.class);
                    if (!abandoned && game != null && game.driver() != null && game.driver().ticks() >= 5) {
                        abandoned = true;
                        game.onAction(GameAction.ABANDON_TOWER);
                        game.onAction(GameAction.TURN_LEFT);
                        game.onAction(GameAction.MENU_CONFIRM);
                    }
                }
                case RESULT -> {
                    if (abandoned && background != null && background.interlude() >= 0
                            && background.player() != null) {
                        abandoned = false;
                        results++;
                        if (results == 1) {
                            // Retry (the focus starts on Overview)
                            flow.key(UiKey.LEFT);
                            flow.key(UiKey.ENTER);
                        } else {
                            step = 6;
                            flow.key(UiKey.ENTER);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    @Test
    void retryFromTheResultWindowBuildsNoMenuTowerAndKeepsTheSequence(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json"))
                && Files.isRegularFile(assets.resolve("towers/demo.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        RetryProbe probe = new RetryProbe();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("MenuBackgroundSmokeTest: retry path in " + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertTrue(finished, "menu, game, result, retry, game, result, overview within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(2, probe.results, "two result windows, each with its interlude");
        int index = DemoScripts.SEQUENCE.indexOf(probe.menuDemoBefore);
        assertTrue(index >= 0, "the first menu visit shows a tower of the sequence");
        assertEquals(DemoScripts.SEQUENCE.get((index + 1) % DemoScripts.SEQUENCE.size()).intValue(),
                probe.menuDemoAfter, "one game, one advance: the Retry consumes no tower of the sequence");
        assertEquals(3, probe.playedAtEnd - probe.playedAtStart,
                "after the Briefing only the two interludes and the Overview's tower are loaded");
    }
}
