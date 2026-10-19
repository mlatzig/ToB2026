package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.CameraLimits;
import de.latzig.game.tob.core.sim.LargeTestTowers;

/**
 * Phase 6a spec §4 (headless): every large test tower starts through the developer start ({@code -Dtob.towerLibrary}
 * with {@code -Dtob.tower}) and runs {@value #FRAMES} simulation frames of seeded random keys in fast-forward,
 * cameras and zoom included, without an error; a finished attempt is restarted.
 */
class LargeTowersSmokeTest {

    private static final int FRAMES = 300;
    private static final int UNTHROTTLED = Integer.MAX_VALUE;
    private static final List<GameAction> ACTIONS = List.of(GameAction.FORWARD, GameAction.BACK,
            GameAction.TURN_LEFT, GameAction.TURN_RIGHT, GameAction.FIRE, GameAction.SELECT_ZAPPER,
            GameAction.SELECT_PUSHER, GameAction.SELECT_GRABBER, GameAction.CAMERA_NORTH, GameAction.CAMERA_EAST,
            GameAction.CAMERA_SOUTH, GameAction.CAMERA_WEST, GameAction.ZOOM_IN, GameAction.ZOOM_OUT);

    /** Presses a random action in about every third render frame and counts the simulation frames run. */
    private static final class RandomKeys extends BaseAppState {

        private final Random random = new Random(20261014L);
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile String towerId;
        private volatile int startDistance;
        private GameState game;
        private GameAction held;
        private int frames;
        private int lastTicks;

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
            if (current == null || current.driver() == null || done.getCount() == 0) {
                return;
            }
            if (current != game) {
                if (game == null) {
                    towerId = current.tower().id();
                    startDistance = current.driver().simulation().camera(CameraId.WEST).distance();
                }
                game = current;
                lastTicks = 0;
            }
            frames += game.driver().ticks() - lastTicks;
            lastTicks = game.driver().ticks();
            if (frames >= FRAMES) {
                done.countDown();
            } else if (game.driver().isPaused()) {
                game.onAction(GameAction.PAUSE);
            } else if (game.driver().simulation().isFinished()) {
                ((TobApp) getApplication()).startTower(game.tower());
            } else if (random.nextInt(3) == 0) {
                if (held != null) {
                    game.onRelease(held);
                }
                held = ACTIONS.get(random.nextInt(ACTIONS.size()));
                game.onAction(held);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {LargeTestTowers.FULL, LargeTestTowers.NARROW, LargeTestTowers.CROWDED,
        LargeTestTowers.SOLVABLE})
    void everyLargeTestTowerStartsAndRunsWithoutErrors(String id, @TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        RandomKeys keys = new RandomKeys();
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(id), true, userDir, false, SkyOptions.DEFAULT,
                Optional.empty(), Optional.of(LargeTestTowers.FILE)), keys);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(UNTHROTTLED);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = keys.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("LargeTowersSmokeTest: " + id + " " + FRAMES + " frames in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertTrue(finished, FRAMES + " frames within 60 s");
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no error");
        assertEquals(id, keys.towerId, "the developer start found the tower in the extra library");
        assertEquals(CameraLimits.of(LargeTestTowers.tower(id).grid()).startDistance(CameraId.WEST),
                keys.startDistance, "the game's cameras start at the tower's limits");
    }
}
