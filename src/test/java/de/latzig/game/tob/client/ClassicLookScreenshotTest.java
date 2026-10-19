package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Phase 5e spec §4 (offscreen): original towers 001 "The Sacrifice" (the reference screenshot's tower, with clouds and
 * a moon in the Remake look), 058 "It Takes Two to Tango" (the blue pulse) and 000 "Softly Softly!" (a blue sky
 * gradient), each in the Remake look and the Classic Look, rendered in jME's offscreen surface (an invisible GLFW
 * window) at 1280 × 720 from the first spider's view once the moon's textures are ready, saved as
 * {@code target/screenshots/phase5e/<tower>-<look>.png}; none blank. One application run: the Look is switched
 * through the options and applies at the next start of the tower (decision 5). Fix wave: 001 Classic again from the
 * tower's east camera ({@code original-001-classic-east}, the reference screenshot's camera, which shows spiders and
 * robots), and the Classic ground's colour is checked (a gamma regression would show there).
 */
@Offscreen
class ClassicLookScreenshotTest {

    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase5e");
    private static final List<String> TOWERS = List.of("original-001", "original-058", "original-000");
    private static final int SETTLE_FRAMES = 6;

    /** A pixel in the ground band below the horizon of the 001 start view. */
    private static final int GROUND_X = 640;
    private static final int GROUND_Y = 380;

    /** One screenshot: the tower, the look and whether it is taken from the tower's east camera. */
    record Shot(String tower, boolean classic, boolean east) {

        Shot(String tower, boolean classic) {
            this(tower, classic, false);
        }

        String name() {
            return tower + (classic ? "-classic" : "-remake") + (east ? "-east" : "");
        }
    }

    static final List<Shot> SHOTS = TOWERS.stream()
            .flatMap(tower -> tower.equals("original-001")
                    ? Stream.of(new Shot(tower, false), new Shot(tower, true), new Shot(tower, true, true))
                    : Stream.of(new Shot(tower, false), new Shot(tower, true)))
            .toList();

    private static final class Shots extends BaseAppState {

        private final Map<String, TowerDefinition> towers;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private final List<Integer> grounds = new ArrayList<>();
        private volatile String failure;
        private int shot;
        private int frames;
        private boolean switching;

        Shots(Map<String, TowerDefinition> towers) {
            this.towers = towers;
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
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null) {
                return;
            }
            Shot next = SHOTS.get(shot);
            if (!game.tower().id().equals(next.tower()) || game.look().classic() != next.classic()) {
                if (!switching) {
                    // decision 5: the switched Look applies when the tower starts again
                    app.settings().preview(app.settings().current().withClassicLook(next.classic()));
                    app.startTower(towers.get(next.tower()));
                    switching = true;
                }
                return;
            }
            switching = false;
            if (game.sky().moonSize() != null && !game.moonReady()) {
                return;
            }
            frames++;
        }

        /** After the game's camera update: the east shot looks from the tower's east camera. */
        @Override
        public void render(RenderManager renderManager) {
            GameState game = getState(GameState.class);
            if (done.getCount() == 0 || game == null || game.driver() == null || !SHOTS.get(shot).east()) {
                return;
            }
            Pose eye = CameraPlacement.of(game.tower()).eye(game.driver().simulation().camera(CameraId.EAST));
            float heading = CameraPlacement.heading(CameraId.EAST);
            Camera cam = getApplication().getCamera();
            cam.setLocation(new Vector3f(eye.x(), eye.y(), eye.z()));
            cam.lookAtDirection(new Vector3f(FastMath.sin(heading), 0f, -FastMath.cos(heading)), Vector3f.UNIT_Y);
        }

        @Override
        public void postRender() {
            if (done.getCount() == 0 || frames != SETTLE_FRAMES) {
                return;
            }
            capture(SHOTS.get(shot).name());
            frames = 0;
            if (++shot == SHOTS.size()) {
                done.countDown();
            }
        }

        private void capture(String name) {
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
            Path file = SCREENSHOTS.resolve(name + ".png");
            try {
                Files.createDirectories(SCREENSHOTS);
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException e) {
                failure = e.toString();
                throw new UncheckedIOException(e);
            }
            files.add(file);
            colours.add(seen.size());
            grounds.add(image.getRGB(GROUND_X, GROUND_Y) & 0xFFFFFF);
        }
    }

    @Test
    void threeTowersAreShotInTheRemakeLookAndTheClassicLook(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json")) && TowerAssets.available(),
                "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Map<String, TowerDefinition> towers = TowerAssets.allTowers().stream()
                .filter(tower -> TOWERS.contains(tower.id()))
                .collect(Collectors.toMap(TowerDefinition::id, Function.identity()));
        Shots shots = new Shots(towers);
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(TOWERS.get(0)), true, userDir), shots);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
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
        System.out.println("ClassicLookScreenshotTest: " + (System.nanoTime() - start) / 1_000_000 + " ms; colours "
                + shots.colours + "; " + shots.files);

        assertTrue(finished, "seven shots within 60 s");
        assertNull(shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(SHOTS.stream().map(shot -> SCREENSHOTS.resolve(shot.name() + ".png")).toList(), shots.files);
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(shots.colours.get(i) > 50, SHOTS.get(i).name() + ": not blank, " + shots.colours.get(i));
        }
        int ground = shots.grounds.get(SHOTS.indexOf(new Shot("original-001", true)));
        int r = ground >> 16;
        int g = ground >> 8 & 0xFF;
        int b = ground & 0xFF;
        String hex = String.format("%06X", ground);
        assertTrue(r > g && g > b && b < 0x20, "001 Classic: the yellow ground (entry 16, CA0), " + hex);
        assertTrue(r >= 0xC0 && r <= 0xF0, "001 Classic: the sunlit ground's red, not a gamma error, " + hex);
    }
}
