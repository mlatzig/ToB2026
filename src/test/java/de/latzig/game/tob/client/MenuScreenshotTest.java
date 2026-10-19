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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
import de.latzig.game.tob.client.logic.frontend.HallOfFame;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.progress.Outcome;
import de.latzig.game.tob.core.progress.Profile;

/**
 * Phase 5c-1 spec §4 (offscreen): the new main menu, the Play window with two profiles and with five (the greyed
 * "&lt; New Profile &gt;" and its hint), the Tutorial with one solved tower, and the Hall of Fame's two tabs, rendered
 * in jME's offscreen surface (an invisible GLFW window, no visible window) at 1280 × 720 over the menu background,
 * each saved as {@code target/screenshots/phase5c1/<name>.png} for the author's look, none blank. The flow is driven
 * through its own API, one step whenever no fade runs.
 */
@Offscreen
class MenuScreenshotTest {

    /** Where the screenshots go (the build folder; {@code mvn clean} removes them). */
    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase5c1");
    private static final List<String> NAMES = List.of("menu", "play", "play-full", "tutorial", "hall-of-fame-scores",
            "hall-of-fame-credits");
    /** Render frames between a step and the capture of its screen. */
    private static final int SETTLE_FRAMES = 3;

    /** One step of the script: on {@code screen}, either run {@code action} or capture the screen as {@code name}. */
    private record Step(Screen screen, Consumer<GameFlow> action, String name) {

        static Step act(Screen screen, Consumer<GameFlow> action) {
            return new Step(screen, action, null);
        }

        static Step shot(Screen screen, String name) {
            return new Step(screen, null, name);
        }
    }

    private static final class Shots extends BaseAppState {

        private final List<Step> steps;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private volatile String failure;
        private int step;
        private int frames = -1;

        Shots(List<Step> steps) {
            this.steps = steps;
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
            GameFlow flow = ((TobApp) getApplication()).flow();
            if (flow == null || done.getCount() == 0 || flow.fadeAlpha() > 0f || frames >= 0) {
                if (frames >= 0) {
                    frames++;
                }
                return;
            }
            Step next = steps.get(step);
            if (flow.screen() != next.screen()) {
                return;
            }
            if (next.action() != null) {
                next.action().accept(flow);
                step++;
            } else {
                frames = 0;
            }
        }

        @Override
        public void postRender() {
            if (frames < SETTLE_FRAMES || done.getCount() == 0) {
                return;
            }
            capture(steps.get(step).name());
            frames = -1;
            step++;
            if (step == steps.size()) {
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
        }
    }

    /** {@code profile} with the towers {@code ids} completed (3 survivors, no retry). */
    private static Profile solved(Profile profile, String... ids) {
        Profile result = profile;
        for (String id : ids) {
            result = result.started(id).ended(id, Outcome.COMPLETED, 3, 500, Instant.EPOCH).profile();
        }
        return result;
    }

    private static void save(ProfileStore store, Profile profile) {
        try {
            store.save(profile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theMenuAndItsNewWindowsAreRenderedOffscreen(@TempDir Path userDir) throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        ProfileStore store = new ProfileStore(userDir);
        save(store, solved(Profile.create("Anna", "original"), "original-000", "original-001", "original-002"));
        save(store, solved(Profile.create("Bob", "original"), "original-000").withGroup(0));
        store.settings().update(settings -> settings.withLastProfile("Anna").withSolvedTutorial("tutorial-007"));
        List<Step> steps = List.of(Step.shot(Screen.MENU, "menu"),
                Step.act(Screen.MENU, flow -> flow.click(Hit.of(Widget.MENU_PLAY))),
                Step.shot(Screen.PLAY, "play"),
                Step.act(Screen.PLAY, flow -> {
                    for (String name : List.of("Cid", "Dora", "MMMMMMMMMMMMMMMM")) {
                        save(store, Profile.create(name, "original"));
                    }
                    flow.key(UiKey.ESCAPE);
                }),
                Step.act(Screen.MENU, flow -> flow.click(Hit.of(Widget.MENU_PLAY))),
                Step.shot(Screen.PLAY, "play-full"),
                Step.act(Screen.PLAY, flow -> flow.key(UiKey.ESCAPE)),
                Step.act(Screen.MENU, flow -> flow.click(Hit.of(Widget.MENU_TUTORIAL))),
                Step.shot(Screen.TUTORIAL, "tutorial"),
                Step.act(Screen.TUTORIAL, flow -> flow.key(UiKey.ESCAPE)),
                Step.act(Screen.MENU, flow -> flow.click(Hit.of(Widget.MENU_HALL_OF_FAME))),
                Step.shot(Screen.HALL_OF_FAME, "hall-of-fame-scores"),
                Step.act(Screen.HALL_OF_FAME,
                        flow -> flow.click(new Hit(Widget.TAB, HallOfFame.Tab.CREDITS.ordinal()))),
                Step.shot(Screen.HALL_OF_FAME, "hall-of-fame-credits"));
        Shots shots = new Shots(steps);
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
        System.out.println("MenuScreenshotTest: " + (System.nanoTime() - start) / 1_000_000 + " ms; distinct colours "
                + shots.colours + "; " + shots.files);

        assertTrue(finished, NAMES.size() + " views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(NAMES.stream().map(name -> SCREENSHOTS.resolve(name + ".png")).toList(), shots.files);
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 100, NAMES.get(i) + ": not blank, " + shots.colours.get(i) + " colours");
        }
    }
}
