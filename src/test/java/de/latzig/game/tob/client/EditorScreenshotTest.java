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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.editor.EditorElement;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Phase 6b spec §4 (offscreen): the Tower Designer rendered in jME's offscreen surface (an invisible GLFW window, no
 * visible window) at 1280 × 720 — {@code original-000} opened read-only, the Edit menu open, a new tower with an
 * object without floor (marked) and a lift, the Scroll dialog, the Save As dialog — each saved as
 * {@code target/screenshots/phase6b1/<name>.png} for the author's look, none blank. Acceptance change 1 (the
 * editor's layout follows the window's aspect ratio): {@code original-000} and the Save As dialog again at 4:3,
 * 21:9 and 32:9 (clamped to 21:9 between bars) as {@code editor-<aspect>.png} and {@code editor-<aspect>-dialog.png};
 * the 16:9 view is {@code editor-original-000.png}.
 */
@Offscreen
class EditorScreenshotTest {

    /** Where the screenshots go (the build folder; {@code mvn clean} removes them). */
    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase6b1");
    private static final List<String> NAMES = List.of("editor-original-000", "editor-menu", "editor-marks",
            "editor-toolbox", "editor-save-as");
    /** The views {@link Shots#setUp} knows, by their index there. */
    private static final int ORIGINAL = 0;
    private static final int SAVE_AS = 4;
    /** Render frames between setting a view up and its capture. */
    private static final int SETTLE_FRAMES = 3;

    private static final class Shots extends BaseAppState {

        private final List<String> names;
        private final List<Integer> views;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private volatile String failure;
        private int shot = -1;
        private int frame;

        /** Captures {@code views} (indices of {@link #setUp}'s views) as {@code names}. */
        Shots(List<String> names, List<Integer> views) {
            this.names = names;
            this.views = views;
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
            if (flow == null || done.getCount() == 0 || flow.fadeAlpha() > 0f) {
                return;
            }
            if (shot < 0) {
                if (flow.screen() == GameFlow.Screen.MENU) {
                    flow.click(Hit.of(Widget.MENU_DESIGNER));
                } else if (flow.screen() == GameFlow.Screen.EDITOR) {
                    shot = 0;
                    frame = 0;
                    setUp(flow, flow.designer());
                }
                return;
            }
            frame++;
        }

        /** The view of shot {@link #shot}. */
        private void setUp(GameFlow flow, TowerDesigner designer) {
            switch (views.get(shot)) {
                case 0 -> {
                    designer.run(Command.OPEN);
                    flow.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
                    designer.model().level(1);
                    designer.model().cursor(6, 6);
                }
                case 1 -> {
                    flow.click(new Hit(Widget.EDITOR_MENU, TowerDesigner.Menu.EDIT.ordinal()));
                    flow.editorPointer(Optional.of(new Hit(Widget.EDITOR_ITEM, Command.COPY.ordinal())), false);
                }
                case 2 -> {
                    flow.click(new Hit(Widget.EDITOR_MENU, TowerDesigner.Menu.EDIT.ordinal()));
                    designer.run(Command.NEW);
                    for (int x = 3; x <= 12; x++) {
                        flow.click(cell(x, 8));
                    }
                    designer.model().tool(EditorElement.LIFT_UP);
                    flow.click(cell(3, 8));
                    designer.model().tool(EditorElement.of(ObjectType.ZAP));
                    flow.click(cell(5, 8));
                    designer.model().tool(EditorElement.of(ObjectType.WAT));
                    flow.click(cell(9, 4));
                    designer.model().tool(EditorElement.of(ObjectType.KLD));
                    flow.click(cell(12, 8));
                    designer.model().cursor(9, 4);
                }
                case 3 -> designer.run(Command.SCROLL);
                default -> {
                    designer.key(de.latzig.game.tob.client.logic.ui.UiKey.ESCAPE);
                    designer.run(Command.SAVE_AS);
                    for (char c : "my_towers".toCharArray()) {
                        flow.typed(c);
                    }
                }
            }
        }

        private static Hit cell(int x, int y) {
            return new Hit(Widget.EDITOR_CELL, y * TowerGrid.WIDTH + x);
        }

        @Override
        public void postRender() {
            if (shot < 0 || done.getCount() == 0 || frame < SETTLE_FRAMES) {
                return;
            }
            capture(names.get(shot));
            shot++;
            frame = 0;
            if (shot == names.size()) {
                done.countDown();
            } else {
                GameFlow flow = ((TobApp) getApplication()).flow();
                setUp(flow, flow.designer());
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

    @Test
    void theDesignerIsRenderedOffscreen(@TempDir Path userDir) throws InterruptedException, IOException {
        render(NAMES, List.of(0, 1, 2, 3, SAVE_AS), 1280, 720, userDir);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"4x3,1024,768", "21x9,1680,720", "32x9,1600,450"})
    void theDesignersLayoutFollowsTheAspectRatio(String aspect, int width, int height, @TempDir Path userDir)
            throws InterruptedException, IOException {
        render(List.of("editor-" + aspect, "editor-" + aspect + "-dialog"), List.of(ORIGINAL, SAVE_AS), width,
                height, userDir);
    }

    private static void render(List<String> names, List<Integer> views, int width, int height, Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Shots shots = new Shots(names, views);
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), shots);
        AppSettings settings = new AppSettings(true);
        settings.setResolution(width, height);
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
        System.out.println("EditorScreenshotTest " + width + " x " + height + ": " + (System.nanoTime() - start)
                / 1_000_000 + " ms; distinct colours " + shots.colours + "; " + shots.files);

        assertTrue(finished, names.size() + " views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(names.size(), shots.files.size());
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 100, names.get(i) + ": not blank, " + shots.colours.get(i) + " colours");
        }
    }
}
