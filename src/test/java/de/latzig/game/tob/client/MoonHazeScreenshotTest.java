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
import java.util.List;
import java.util.Optional;
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
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.game.GameAction;

/**
 * Acceptance change "moon haze" (Phase 6b-2): a tower with a forced large or giant moon rendered offscreen (an
 * invisible GLFW surface, no visible window) through cameras F5–F8 once the moon's textures are ready; each view is
 * saved as {@code target/screenshots/moon-haze/<tower>-<size>-<key>.png} for the author's look and must not be blank.
 */
@Offscreen
class MoonHazeScreenshotTest {

    static final Path SCREENSHOTS = Path.of("target", "screenshots", "moon-haze");
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final List<GameAction> CAMERAS = List.of(GameAction.CAMERA_NORTH, GameAction.CAMERA_EAST,
            GameAction.CAMERA_SOUTH, GameAction.CAMERA_WEST);
    private static final List<String> KEYS = List.of("F5", "F6", "F7", "F8");
    private static final int SETTLE_FRAMES = 6;

    /** Waits for the moon, then presses F5–F8 in turn and captures each view. */
    private static final class MoonShots extends BaseAppState {

        private final String name;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private volatile String failure;
        private int camera = -1;
        private int frame;

        MoonShots(String name) {
            this.name = name;
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
            if (game == null || game.driver() == null || !game.moonReady() || done.getCount() == 0) {
                return;
            }
            if (camera < 0 || frame == SETTLE_FRAMES + 1) {
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
        public void postRender() {
            if (camera >= 0 && camera < CAMERAS.size() && frame == SETTLE_FRAMES && done.getCount() > 0) {
                capture();
            }
        }

        private void capture() {
            int width = getApplication().getCamera().getWidth();
            int height = getApplication().getCamera().getHeight();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            getApplication().getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.RGBA8);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    image.setRGB(x, y, (buffer.get(i) & 0xFF) << 16 | (buffer.get(i + 1) & 0xFF) << 8
                            | buffer.get(i + 2) & 0xFF);
                }
            }
            Path file = SCREENSHOTS.resolve(name + "-" + KEYS.get(camera) + ".png");
            try {
                Files.createDirectories(SCREENSHOTS);
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException e) {
                failure = e.toString();
                throw new UncheckedIOException(e);
            }
            files.add(file);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"original-000 GIANT", "original-006 LARGE"})
    void aLargeMoonIsShotOffscreenFromEveryCamera(String spec, @TempDir Path userDir)
            throws InterruptedException, IOException {
        String[] parts = spec.split(" ");
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        MoonShots shots = new MoonShots(parts[0] + "-" + parts[1].toLowerCase(java.util.Locale.ROOT));
        TobApp app = new TobApp(new AppConfig(assets, Optional.of(parts[0]), true, userDir, false,
                new SkyOptions(true, Optional.empty(), Optional.of(MoonDesign.SizeClass.valueOf(parts[1])))), shots);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(WIDTH, HEIGHT);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setVSync(false);
        settings.setSamples(4);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.OffscreenSurface);
        boolean finished = shots.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("MoonHazeScreenshotTest: " + spec + " " + shots.files);

        assertTrue(finished, "the moon and four camera views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(4, shots.files.size());
        for (Path file : shots.files) {
            assertTrue(Files.size(file) > 0, file.toString());
        }
    }
}
