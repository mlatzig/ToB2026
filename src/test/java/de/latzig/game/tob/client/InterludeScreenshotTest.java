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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Profile;
import de.latzig.game.tob.core.sim.Rules;

/**
 * Phase 5c-2 spec §4 (offscreen): a failure and a success result window with the interlude behind them, rendered in
 * jME's offscreen surface (an invisible GLFW window, no visible window) at 1280 × 720 and saved as
 * {@code target/screenshots/phase5c2/<name>.png} for the author's look; none blank, and the band between the logo and
 * the window shows the scene. A profile plays the first tower: it ends as "All Spiders Destroyed" (interlude 7),
 * Retry, then "Tower Completed" (interlude 2).
 */
@Offscreen
class InterludeScreenshotTest {

    /** Where the screenshots go (the build folder; {@code mvn clean} removes them). */
    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase5c2");
    private static final List<String> NAMES = List.of("result-failure", "result-success");
    /** Render frames after a result window's fade before its capture (fast-forward: one demo frame each). */
    private static final int SETTLE_FRAMES = 30;

    private static final class Shots extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private final List<Integer> bandColours = new ArrayList<>();
        private final List<Integer> interludes = new ArrayList<>();
        private volatile String failure;
        private int step;
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
            GameFlow flow = app.flow();
            if (flow == null || done.getCount() == 0 || flow.screen() != Screen.GAME && flow.fadeAlpha() > 0f) {
                // the start screens' fade stays black while a game runs
                return;
            }
            GameState game = getState(GameState.class);
            boolean running = game != null && game.driver() != null && game.driver().ticks() >= 1;
            switch (step) {
                case 0 -> act(flow.screen() == Screen.MENU, () -> flow.click(Hit.of(Widget.MENU_PLAY)));
                case 1 -> act(flow.screen() == Screen.PLAY, () -> flow.key(UiKey.ENTER));
                case 2 -> act(flow.screen() == Screen.OVERVIEW, () -> flow.key(UiKey.ENTER));
                case 3 -> act(flow.screen() == Screen.BRIEFING && flow.isEnabled(Widget.PLAY),
                        () -> flow.key(UiKey.ENTER));
                case 4 -> act(running, () -> app.attemptEnded(Rules.EXIT_SPIDERS_DESTROYED, 0, 100));
                case 5, 8 -> {
                    MenuBackgroundState background = getState(MenuBackgroundState.class);
                    if (flow.screen() == Screen.RESULT && background != null && background.player() != null
                            && ++frames > SETTLE_FRAMES) {
                        interludes.add(background.interlude());
                        frames = 0;
                        step++;
                    }
                }
                case 7 -> act(running, () -> app.attemptEnded(Rules.EXIT_COMPLETED, 3, 500));
                default -> {
                }
            }
        }

        private void act(boolean ready, Runnable action) {
            if (ready) {
                action.run();
                step++;
            }
        }

        @Override
        public void postRender() {
            if (step != 6 && step != 9 || done.getCount() == 0) {
                return;
            }
            capture(NAMES.get(step == 6 ? 0 : 1));
            if (step == 6) {
                ((TobApp) getApplication()).flow().click(Hit.of(Widget.RETRY));
                step = 7;
            } else {
                done.countDown();
            }
        }

        private void capture(String name) {
            int width = getApplication().getCamera().getWidth();
            int height = getApplication().getCamera().getHeight();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            getApplication().getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.RGBA8);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            StartLayout layout = new StartLayout(width, height);
            int bandTop = Math.round(StartScreens.TITLE_BOTTOM * layout.scale());
            int bandBottom = Math.round(StartScreens.RESULT_TOP * layout.scale());
            Set<Integer> seen = new HashSet<>();
            Set<Integer> band = new HashSet<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    int rgb = (buffer.get(i) & 0xFF) << 16 | (buffer.get(i + 1) & 0xFF) << 8 | buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, rgb);
                    seen.add(rgb);
                    if (y > bandTop && y < bandBottom) {
                        band.add(rgb);
                    }
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
            bandColours.add(band.size());
        }
    }

    @Test
    void aFailureAndASuccessResultWindowShowTheirInterludes(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original"));
        store.rememberLast("Anna");
        Shots shots = new Shots();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), shots);
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
        System.out.println("InterludeScreenshotTest: " + (System.nanoTime() - start) / 1_000_000 + " ms; colours "
                + shots.colours + ", in the band " + shots.bandColours + "; interludes " + shots.interludes + "; "
                + shots.files);

        assertTrue(finished, "both result windows within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(List.of(7, 2), shots.interludes, "the first failure, then the second result, a success");
        assertEquals(NAMES.stream().map(name -> SCREENSHOTS.resolve(name + ".png")).toList(), shots.files);
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 100, NAMES.get(i) + ": not blank, " + shots.colours.get(i) + " colours");
            assertTrue(shots.bandColours.get(i) > 100, NAMES.get(i) + ": the interlude shows in the band, "
                    + shots.bandColours.get(i) + " colours");
        }
    }
}
