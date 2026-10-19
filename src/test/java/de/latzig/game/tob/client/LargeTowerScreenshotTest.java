package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Statistics;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.core.sim.LargeTestTowers;

/**
 * Phase 6a spec §4 (headless, offscreen): the full and the narrow large test tower, and {@code original-000} for
 * comparison, rendered in jME's offscreen surface (an invisible GLFW window, no visible window) through cameras
 * F5–F8; each view is saved as {@code target/screenshots/phase6a/<tower>-<key>.png} for the author's look and must
 * not be blank. The rendering cost (spec §5: objects and triangles per frame with the shadow passes, the mean frame
 * time) is printed for the results note.
 */
@Offscreen
class LargeTowerScreenshotTest {

    /** Where the screenshots go (the build folder; {@code mvn clean} removes them). */
    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase6a");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final List<GameAction> CAMERAS = List.of(GameAction.CAMERA_NORTH, GameAction.CAMERA_EAST,
            GameAction.CAMERA_SOUTH, GameAction.CAMERA_WEST);
    private static final List<String> KEYS = List.of("F5", "F6", "F7", "F8");
    /** Render frames after a camera key before the capture (the camera switch snaps). */
    private static final int SETTLE_FRAMES = 6;
    /** Render frames whose time and statistics are averaged per camera. */
    private static final int MEASURED_FRAMES = 30;

    /** Presses F5–F8 in turn, measures and captures each view. */
    private static final class CameraShots extends BaseAppState {

        private final String towerId;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private final int[] data = new int[13];
        private volatile String failure;
        private Statistics statistics;
        private int camera = -1;
        private int frame;
        private long lastNanos;
        private long measuredNanos;
        private long objects;
        private long triangles;
        private int measured;

        CameraShots(String towerId) {
            this.towerId = towerId;
        }

        @Override
        protected void initialize(Application app) {
            statistics = app.getRenderer().getStatistics();
            statistics.setEnabled(true);
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
            if (camera < 0 || frame == SETTLE_FRAMES + MEASURED_FRAMES + 1) {
                camera++;
                frame = 0;
                if (camera == CAMERAS.size()) {
                    done.countDown();
                    return;
                }
                game.onAction(CAMERAS.get(camera));
            }
            frame++;
        }

        @Override
        public void render(RenderManager rm) {
            statistics.clearFrame();
        }

        @Override
        public void postRender() {
            if (camera < 0 || camera >= CAMERAS.size() || done.getCount() == 0) {
                return;
            }
            long now = System.nanoTime();
            if (frame > SETTLE_FRAMES && frame <= SETTLE_FRAMES + MEASURED_FRAMES) {
                statistics.getData(data);
                objects += data[3];
                triangles += data[1];
                measuredNanos += now - lastNanos;
                measured++;
            }
            if (frame == SETTLE_FRAMES) {
                capture();
            }
            lastNanos = System.nanoTime();
        }

        private void capture() {
            int width = getApplication().getCamera().getWidth();
            int height = getApplication().getCamera().getHeight();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            getApplication().getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.RGBA8);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Set<Integer> seen = new HashSet<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    int rgb = (buffer.get(i) & 0xFF) << 16 | (buffer.get(i + 1) & 0xFF) << 8 | buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, rgb);
                    seen.add(rgb);
                }
            }
            Path file = SCREENSHOTS.resolve(towerId + "-" + KEYS.get(camera) + ".png");
            try {
                Files.createDirectories(SCREENSHOTS);
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException e) {
                failure = e.toString();
                throw new UncheckedIOException(e);
            }
            files.add(file);
            colours.add(seen.size());
        }

        String cost() {
            return String.format(Locale.ROOT, "%.1f objects, %.0f triangles per frame, %.2f ms per frame",
                    (double) objects / measured, (double) triangles / measured, measuredNanos / 1e6 / measured);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {LargeTestTowers.FULL, LargeTestTowers.NARROW, "original-000"})
    void camerasF5ToF8ShowTheTowerOffscreen(String id, @TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        CameraShots shots = new CameraShots(id);
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(id), true, userDir, false, SkyOptions.DEFAULT,
                Optional.empty(), Optional.of(LargeTestTowers.FILE)), shots);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(WIDTH, HEIGHT);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setVSync(false);
        settings.setSamples(4);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.OffscreenSurface);
        boolean finished = shots.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("LargeTowerScreenshotTest: " + id + " in " + (System.nanoTime() - start) / 1_000_000
                + " ms; " + shots.cost() + "; distinct colours " + shots.colours + "; " + shots.files);

        assertTrue(finished, "four camera views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages(), "no missing asset, no error");
        assertEquals(4, shots.files.size());
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 500, KEYS.get(i) + ": not blank, " + shots.colours.get(i) + " colours");
        }
    }
}
