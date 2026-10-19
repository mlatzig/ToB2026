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
import java.util.UUID;
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

import de.latzig.game.tob.client.logic.editor.EditorForm;
import de.latzig.game.tob.client.logic.editor.EffectsDialog;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerIds;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;

/**
 * Phase 6b spec §4 (offscreen): the Tower Designer with its 3D live preview, rendered in jME's offscreen surface (an
 * invisible GLFW window, no visible window) at 1280 × 720 — {@code original-000} in the small preview, the preview on
 * the full screen, the Settings dialog, the Effects dialog (lightning and clouds on, the moon in the east) beside the
 * preview showing it, and the colour dialog over Effects — each saved as {@code target/screenshots/phase6b2/<name>.png}
 * for the author's look. The preview's own pixels are checked: its slot is not blank and shows the tower's objects
 * (the klondikes' gold), also after the render target was replaced for the full screen.
 */
@Offscreen
class EditorPreviewScreenshotTest {

    static final Path SCREENSHOTS = Path.of("target", "screenshots", "phase6b2");
    private static final List<String> NAMES = List.of("preview-original-000", "preview-maximized", "settings",
            "effects", "effects-colour");
    /** Render frames between setting a view up and its capture (the preview rebuilds in the first). */
    private static final int SETTLE_FRAMES = 4;

    private static final class Shots extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<Path> files = new ArrayList<>();
        private final List<Integer> colours = new ArrayList<>();
        private final List<Integer> previewColours = new ArrayList<>();
        private final List<Integer> gold = new ArrayList<>();
        private volatile String failure;
        private int shot = -1;
        private int frame;

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
                    // Phase 6d: the pasted copy is a new tower with a new id, its lightning seed-drawn from it; a
                    // fixed id keeps the "effects" shot (lightning on) the same in every run (measured: with this
                    // id no flash hides the klondikes at the capture; ...0001 flashes there)
                    flow.towerIds(new TowerIds(() -> new UUID(0L, 2L)));
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

        private void setUp(GameFlow flow, TowerDesigner designer) {
            switch (shot) {
                case 0 -> {
                    designer.run(Command.OPEN);
                    flow.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
                }
                case 1 -> designer.preview().toggle();
                case 2 -> {
                    designer.key(UiKey.ESCAPE);
                    designer.run(Command.COPY);
                    designer.run(Command.NEW);
                    designer.run(Command.PASTE);
                    designer.run(Command.SETTINGS);
                }
                case 3 -> {
                    designer.key(UiKey.ESCAPE);
                    designer.run(Command.EFFECTS);
                    field(flow, EffectsDialog.LIGHTNING, EditorForm.BODY);
                    field(flow, EffectsDialog.CLOUDS, EditorForm.PLUS);
                    field(flow, EffectsDialog.MOON, EditorForm.PLUS);
                }
                default -> field(flow, EffectsDialog.SKY_1, EditorForm.BODY);
            }
        }

        private static void field(GameFlow flow, int field, int part) {
            flow.click(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(field, part)));
        }

        @Override
        public void postRender() {
            if (shot < 0 || done.getCount() == 0 || frame < SETTLE_FRAMES) {
                return;
            }
            capture(NAMES.get(shot));
            shot++;
            frame = 0;
            if (shot == NAMES.size()) {
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
            FrontEndState frontEnd = getState(FrontEndState.class);
            PanelLayout.Rect slot = frontEnd.frame().drawing().viewports().get(0).rect();
            Set<Integer> inSlot = new HashSet<>();
            int klondikes = 0;
            for (int y = (int) slot.y() + 2; y < (int) (slot.y() + slot.height()) - 2; y++) {
                for (int x = (int) slot.x() + 2; x < (int) (slot.x() + slot.width()) - 2; x++) {
                    int rgb = image.getRGB(x, height - 1 - y) & 0xFFFFFF;
                    inSlot.add(rgb);
                    int r = rgb >> 16;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (r > 190 && g > 170 && b < 130 && r - b > 80) {
                        klondikes++;
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
            previewColours.add(inSlot.size());
            gold.add(klondikes);
        }
    }

    @Test
    void theDesignerWithItsPreviewAndTheDialogsIsRenderedOffscreen(@TempDir Path userDir)
            throws InterruptedException, IOException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
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
        System.out.println("EditorPreviewScreenshotTest: " + (System.nanoTime() - start) / 1_000_000
                + " ms; distinct colours " + shots.colours + ", in the preview " + shots.previewColours
                + ", klondike pixels " + shots.gold + "; " + shots.files);

        assertTrue(finished, NAMES.size() + " views within 60 s");
        assertEquals(null, shots.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertEquals(NAMES.size(), shots.files.size());
        for (int i = 0; i < shots.files.size(); i++) {
            assertTrue(Files.size(shots.files.get(i)) > 0, shots.files.get(i).toString());
            assertTrue(shots.colours.get(i) > 100, NAMES.get(i) + ": not blank, " + shots.colours.get(i));
        }
        for (int i : List.of(0, 1, 2, 3)) {
            assertTrue(shots.previewColours.get(i) > 100, NAMES.get(i) + ": the preview is rendered, "
                    + shots.previewColours.get(i) + " colours in its slot");
            assertTrue(shots.gold.get(i) >= 10, NAMES.get(i) + ": original-000's klondikes show in the preview, "
                    + shots.gold.get(i) + " gold pixels");
        }
    }
}
