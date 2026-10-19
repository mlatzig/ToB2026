package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.LiveSettings;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Phase 5e (decision 5, spec §4): the Look applies at the next scene build, not in the middle of one — except the
 * menu background (author's acceptance tweak, 2026-10-16), which rebuilds at once while the menus are shown, because
 * the author wants Apply in Options to show the new Look right away (there are no Options during a running tower, so
 * {@link GameState} and {@link EditorPreview}'s full rebuild are unaffected). Headless: a developer start of original
 * tower 001 (which has clouds in the Remake look) with the Look saved as Classic builds the classic scene (the flat
 * ground, no terrain, no clouds, no fog); switching the option meanwhile changes nothing until the tower starts
 * again. The menu background: rebuilt at once, in place, keeping the current demo tower (the Demo Sequence does not
 * advance); a later Back/Esc that restores the old value is caught the same way, because the check compares the
 * background's built look against the live (previewed) option every frame, not just once after a change. The
 * designer's preview: built in the Look saved, kept through an edit.
 */
class ClassicLookSmokeTest {

    private static final int UNTHROTTLED = Integer.MAX_VALUE;

    /** What a probe saw at one moment: the look, the ground, the clouds and the shared fog. */
    record Seen(boolean classic, boolean classicGround, boolean terrain, boolean clouds, boolean fog) {
    }

    private static Seen game(TobApp app, GameState game) {
        return new Seen(game.look().classic(), app.getRootNode().getChild("classic-ground") != null,
                app.getRootNode().getChild("terrain") != null, game.sky().hasClouds(), app.assets().fog());
    }

    private static Path assets() {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        return assets;
    }

    private static TobApp start(TobApp app) {
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start(JmeContext.Type.Headless);
        return app;
    }

    /** Waits for the classic tower, switches the Look back to Remake, waits, then starts the tower again. */
    private static final class GameProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Seen> seen = new ArrayList<>();
        private GameState shown;
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
            TobApp app = (TobApp) getApplication();
            GameState game = getState(GameState.class);
            if (game == null || game.driver() == null || game.driver().ticks() < 1 || done.getCount() == 0) {
                return;
            }
            if (game != shown) {
                shown = game;
                frames = 0;
                seen.add(game(app, game));
                if (seen.size() == 1) {
                    app.settings().preview(app.settings().current().withClassicLook(false));
                } else {
                    done.countDown();
                }
                return;
            }
            if (++frames == 5) {
                seen.add(game(app, game));
                app.startTower(game.tower());
            }
        }
    }

    @Test
    void aTowerStartedWithTheClassicLookKeepsItUntilItsNextStart(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = assets();
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        live.preview(live.current().withClassicLook(true));
        live.save();
        GameProbe probe = new GameProbe();
        TobApp app = start(new TobApp(new AppConfig(assets, Optional.of("original-001"), true, userDir), probe));

        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "two starts within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(new Seen(true, true, false, false, false), new Seen(true, true, false, false, false),
                new Seen(false, false, true, true, true)), probe.seen,
                "classic, still classic after the switch, the Remake look at the next start");
    }

    /**
     * Shows a demo, switches the Look to Classic: the background rebuilds at once, same demo tower, no advance of
     * the Demo Sequence; a few unchanged frames follow (no spurious rebuild); then the switch is undone (as Back/Esc
     * reverts the preview), which the background catches too and rebuilds back to Remake, still the same demo.
     */
    private static final class MenuProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<TowerDefinition> demos;
        private final List<Boolean> classic = new ArrayList<>();
        private final List<Integer> demoNumbers = new ArrayList<>();
        private final List<Integer> played = new ArrayList<>();
        private MenuBackgroundState background;
        private int phase;
        private int frames;
        private volatile boolean classicGround;
        private volatile boolean fog = true;

        MenuProbe(List<TowerDefinition> demos) {
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

        private void record() {
            classic.add(background.look().classic());
            demoNumbers.add(background.demo());
            played.add(background.played());
        }

        @Override
        public void update(float tpf) {
            TobApp app = (TobApp) getApplication();
            switch (phase) {
                case 0 -> {
                    FrontEndState frontEnd = getState(FrontEndState.class);
                    if (frontEnd != null) {
                        getStateManager().detach(frontEnd);
                    }
                    background = new MenuBackgroundState(app.assets(), demos, true);
                    getStateManager().attach(background);
                    phase = 1;
                }
                case 1 -> {
                    if (background.player() != null && background.player().step() > 0) {
                        record();
                        app.settings().preview(app.settings().current().withClassicLook(true));
                        frames = 0;
                        phase = 2;
                    }
                }
                case 2 -> {
                    if (++frames == 5) {
                        record();
                        classicGround = app.getRootNode().getChild("classic-ground") != null;
                        fog = app.assets().fog();
                        app.settings().preview(app.settings().current().withClassicLook(false));
                        frames = 0;
                        phase = 3;
                    }
                }
                case 3 -> {
                    if (++frames == 5) {
                        record();
                        done.countDown();
                    }
                }
                default -> {
                }
            }
        }
    }

    @Test
    void theMenuBackgroundRebuildsAtOnceWhenTheLookOptionChanges(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = assets();
        List<TowerDefinition> demos = new TowerLibraryLoader().load(assets.resolve("towers/demo.json"))
                .playableTowers();
        MenuProbe probe = new MenuProbe(demos);
        TobApp app = start(new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe));

        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(false, true, false), probe.classic,
                "Remake, Classic right after the switch (rebuilt at once, no game start), Remake again after the "
                        + "switch is undone (as Back/Esc reverting the preview would)");
        assertEquals(List.of(probe.demoNumbers.get(0), probe.demoNumbers.get(0)), probe.demoNumbers.subList(1, 3),
                "both rebuilds keep the same demo tower, the Demo Sequence does not advance");
        assertEquals(1, probe.played.get(1) - probe.played.get(0), "exactly one rebuild for the one switch");
        assertEquals(1, probe.played.get(2) - probe.played.get(1), "one more rebuild for undoing it, nothing else");
        assertTrue(probe.classicGround, "the flat ground");
        assertTrue(!probe.fog, "no fog");
    }

    /** Opens the designer with the Look saved as Classic, then places a floor (a partial rebuild). */
    private static final class PreviewProbe extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Seen> seen = new ArrayList<>();
        private int step;

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
            GameFlow flow = app.flow();
            if (flow == null || done.getCount() == 0) {
                return;
            }
            boolean still = flow.fadeAlpha() == 0f;
            EditorPreview preview = getState(FrontEndState.class).preview();
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        flow.click(Hit.of(Widget.MENU_DESIGNER));
                        step++;
                    }
                }
                case 1 -> {
                    if (flow.screen() == GameFlow.Screen.EDITOR && still && preview.visible()) {
                        seen.add(seen(app, preview));
                        flow.click(new Hit(Widget.EDITOR_CELL, 7 * 16 + 7));
                        step++;
                    }
                }
                case 2 -> {
                    seen.add(seen(app, preview));
                    step++;
                    done.countDown();
                }
                default -> {
                }
            }
        }

        private static Seen seen(TobApp app, EditorPreview preview) {
            return new Seen(preview.classic(), false, false, preview.sky().hasClouds(), app.assets().fog());
        }
    }

    @Test
    void theDesignersPreviewIsBuiltInTheSavedLook(@TempDir Path userDir) throws InterruptedException, IOException {
        Path assets = assets();
        LiveSettings live = LiveSettings.load(new ProfileStore(userDir).settings());
        live.preview(live.current().withClassicLook(true));
        live.save();
        PreviewProbe probe = new PreviewProbe();
        TobApp app = start(new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), probe));

        boolean finished = probe.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "the designer within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(new Seen(true, false, false, false, false), new Seen(true, false, false, false, false)),
                probe.seen, "classic, no clouds, no fog, also after an edit");
    }
}
