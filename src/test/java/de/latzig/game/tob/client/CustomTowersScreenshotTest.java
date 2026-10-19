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

import de.latzig.game.tob.client.logic.editor.EditorTowers;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Screen;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.frontend.ProfileStore;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.io.TowerLibraryReader;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.progress.Profile;

/**
 * Phase 6d spec §4 (offscreen): the Overview with an own library — its first group with two towers solved, its last
 * group with a tower without a task and empty cells, the original's hint after Play on that tower, and the original
 * towers after the Library button wrapped round — rendered in jME's offscreen surface (an invisible GLFW window, no
 * visible window) at 1280 × 720 over the menu background, each saved as {@code target/screenshots/phase6d/<name>.png}
 * for the author's look, none blank. The flow is driven through its own API, one step whenever no fade runs.
 */
@Offscreen
class CustomTowersScreenshotTest {

    /** Where the screenshots go (the build folder; {@code mvn clean} removes them). */
    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase6d");
    private static final List<String> NAMES = List.of("overview-own", "overview-own-last-group", "overview-no-task",
            "overview-original");
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

    /** The first ten original towers under new ids ({@code own-0} …), the tenth without a task. */
    private static List<TowerDefinition> ownTowers(Path assets) throws IOException {
        List<TowerDefinition> originals = new TowerLibraryReader().read(assets.resolve("towers/original.json"))
                .towers();
        List<TowerDefinition> own = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TowerDefinition tower = EditorTowers.withId(originals.get(i), "own-" + i);
            if (i == 9) {
                tower = new TowerDefinition(tower.id(), tower.group(), tower.index(), tower.title(), tower.author(),
                        new Conditions(tower.conditions().timeLimitSeconds(), false, 0, 0), tower.behaviour(),
                        tower.effects(), tower.moon(), tower.palette(), tower.sounds(), tower.lock(), tower.grid(),
                        tower.remake());
            }
            own.add(tower);
        }
        return own;
    }

    @Test
    void theOverviewOfAnOwnLibraryIsRenderedOffscreen(@TempDir Path userDir) throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        new TowerLibraries(assets.resolve("towers"), userDir).save("mine", ownTowers(assets));
        ProfileStore store = new ProfileStore(userDir);
        store.save(Profile.create("Anna", "original").withCustomLibrary(Optional.of("mine"))
                .withCustomSolved("mine", "own-0").withCustomSolved("mine", "own-1"));
        store.rememberLast("Anna");
        List<String> captions = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<Step> steps = List.of(Step.act(Screen.MENU, flow -> flow.click(Hit.of(Widget.MENU_PLAY))),
                Step.act(Screen.PLAY, flow -> flow.key(UiKey.ENTER)),
                Step.act(Screen.OVERVIEW, flow -> captions.add(flow.overview().caption())),
                Step.shot(Screen.OVERVIEW, "overview-own"),
                Step.act(Screen.OVERVIEW, flow -> flow.click(Hit.of(Widget.NEXT_GROUP))),
                Step.shot(Screen.OVERVIEW, "overview-own-last-group"),
                Step.act(Screen.OVERVIEW, flow -> {
                    flow.key(UiKey.ENTER);
                    messages.add(flow.message());
                }),
                Step.shot(Screen.OVERVIEW, "overview-no-task"),
                Step.act(Screen.OVERVIEW, flow -> {
                    flow.key(UiKey.ENTER);
                    flow.click(Hit.of(Widget.LIBRARY));
                    captions.add(flow.overview().caption());
                }),
                Step.shot(Screen.OVERVIEW, "overview-original"));
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
        System.out.println("CustomTowersScreenshotTest: " + (System.nanoTime() - start) / 1_000_000
                + " ms; distinct colours " + shots.colours + "; " + shots.files);

        assertTrue(finished, NAMES.size() + " views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(List.of("Anna (mine)", "Anna"), captions, "the profile's library, then wrapped round");
        assertEquals(List.of("No Task!"), messages, "author's acceptance amendment 2026-10-17: the designer's words");
        assertEquals(NAMES.stream().map(name -> SCREENSHOTS.resolve(name + ".png")).toList(), shots.files);
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 100, NAMES.get(i) + ": not blank, " + shots.colours.get(i) + " colours");
        }
    }
}
