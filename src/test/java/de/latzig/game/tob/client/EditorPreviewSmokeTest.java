package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import de.latzig.game.tob.client.input.FrontEndInput;
import de.latzig.game.tob.client.logic.editor.EditorElement;
import de.latzig.game.tob.client.logic.editor.EditorForm;
import de.latzig.game.tob.client.logic.editor.EditorToolbox;
import de.latzig.game.tob.client.logic.editor.EffectsDialog;
import de.latzig.game.tob.client.logic.editor.PreviewCamera;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerEdits;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.client.view.CanvasView;
import de.latzig.game.tob.core.model.ObjectType;

/**
 * Phase 6b spec decision 3 (headless): the 3D live preview is built when the designer opens, shown in its slot at the
 * screen's drawing order, rebuilds only the part an edit changed (the first floor: the world and the socket under it;
 * the Effects dialog's lightning: the world; its moon: the sky), follows the open dialog and returns to the tower on
 * Cancel; the mouse turns it through the real input path, a right click resets it, a double click fills the screen
 * and Esc returns; leaving the designer hides it, releases its scene and switches its viewport off, and the menu
 * background comes back undisturbed.
 */
class EditorPreviewSmokeTest {

    private static final class Driver extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<String> builds = new ArrayList<>();
        private int step;
        private volatile boolean shownOnEntry;
        private volatile boolean quadAtTheSlot;
        private volatile float yawAfterDrag = Float.NaN;
        private volatile float yawAfterReset = Float.NaN;
        private volatile boolean fullScreen;
        private volatile boolean smallAgain;
        private volatile boolean hiddenOnLeaving;
        private volatile boolean backgroundBack;
        private volatile String failure;

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
            try {
                act(app, flow);
            } catch (RuntimeException e) {
                failure = e.toString();
                done.countDown();
            }
        }

        private void act(TobApp app, GameFlow flow) {
            boolean still = flow.fadeAlpha() == 0f;
            FrontEndState frontEnd = getState(FrontEndState.class);
            EditorPreview preview = frontEnd.preview();
            TowerDesigner designer = flow.designer();
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        flow.click(Hit.of(Widget.MENU_DESIGNER));
                        step++;
                    }
                }
                case 1 -> {
                    if (flow.screen() == GameFlow.Screen.EDITOR && still && preview.visible()) {
                        shownOnEntry = preview.viewPort().isEnabled() && preview.sky() != null;
                        Drawing.Viewport slot = frontEnd.frame().drawing().viewports().get(0);
                        quadAtTheSlot = preview.quad().getParent() == app.getGuiNode()
                                && Math.abs(preview.quad().getLocalTranslation().z
                                        - (CanvasView.Z_BASE + slot.order() * CanvasView.Z_STEP)) < 1e-4f
                                && Math.abs(preview.quad().getLocalScale().x - slot.rect().width()) < 1e-3f;
                        record(preview, "entry");
                        flow.click(new Hit(Widget.EDITOR_CELL, 7 * 16 + 7));
                        step++;
                    }
                }
                case 2 -> {
                    record(preview, "floor");
                    choose(flow, Command.EFFECTS);
                    flow.click(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(EffectsDialog.LIGHTNING,
                            EditorForm.BODY)));
                    step++;
                }
                case 3 -> {
                    record(preview, "lightning");
                    flow.click(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(EffectsDialog.MOON, EditorForm.PLUS)));
                    step++;
                }
                case 4 -> {
                    record(preview, "moon");
                    flow.key(UiKey.ESCAPE);
                    step++;
                }
                case 5 -> {
                    record(preview, "cancel");
                    float[] at = centre(frontEnd, Hit.of(Widget.EDITOR_PREVIEW));
                    FrontEndInput input = frontEnd.input();
                    input.onMouseMotionEvent(new MouseMotionEvent((int) at[0], (int) at[1], 0, 0, 0, 0));
                    input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) at[0],
                            (int) at[1]));
                    input.onMouseMotionEvent(new MouseMotionEvent((int) at[0] + 40, (int) at[1], 40, 0, 0, 0));
                    input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) at[0] + 40,
                            (int) at[1]));
                    yawAfterDrag = designer.preview().yaw();
                    input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_RIGHT, true, (int) at[0] + 40,
                            (int) at[1]));
                    yawAfterReset = designer.preview().yaw();
                    for (int i = 0; i < 2; i++) {
                        input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) at[0] + 40,
                                (int) at[1]));
                        input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false,
                                (int) at[0] + 40, (int) at[1]));
                    }
                    step++;
                }
                case 6 -> {
                    PanelLayout.Rect slot = frontEnd.frame().drawing().viewports().get(0).rect();
                    fullScreen = designer.preview().maximized() && slot.width() == app.getCamera().getWidth()
                            && Math.abs(preview.quad().getLocalScale().y - app.getCamera().getHeight()) < 1e-3f;
                    flow.key(UiKey.ESCAPE);
                    step++;
                }
                case 7 -> {
                    smallAgain = !designer.preview().maximized()
                            && frontEnd.frame().drawing().viewports().get(0).rect().width() < app.getCamera()
                                    .getWidth() / 2f;
                    flow.key(UiKey.ESCAPE);
                    flow.click(Hit.of(Widget.YES));
                    step++;
                }
                case 8 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        hiddenOnLeaving = !preview.visible() && !preview.viewPort().isEnabled()
                                && preview.quad().getParent() == null && preview.sky() == null;
                        MenuBackgroundState background = getState(MenuBackgroundState.class);
                        backgroundBack = background != null && background.sky() != null;
                        done.countDown();
                    }
                }
                default -> {
                }
            }
        }

        private void record(EditorPreview preview, String after) {
            int[] counts = preview.builds();
            builds.add(after + " " + counts[0] + "/" + counts[1] + "/" + counts[2]);
        }

        private static void choose(GameFlow flow, Command command) {
            flow.click(new Hit(Widget.EDITOR_MENU, command.menu().ordinal()));
            flow.click(new Hit(Widget.EDITOR_ITEM, command.ordinal()));
        }

        private static float[] centre(FrontEndState frontEnd, Hit hit) {
            Optional<PanelLayout.Rect> rect = frontEnd.frame().areas().stream().filter(a -> a.hit().equals(hit))
                    .map(a -> a.rect()).findFirst();
            return rect.map(r -> new float[] {r.x() + r.width() / 2f, r.y() + r.height() / 2f}).orElseThrow();
        }
    }

    @Test
    void thePreviewFollowsTheEditsTurnsFillsTheScreenAndIsReleasedOnLeaving(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Driver driver = new Driver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = driver.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("EditorPreviewSmokeTest: " + (System.nanoTime() - start) / 1_000_000 + " ms, builds "
                + driver.builds);

        assertTrue(finished, "within 60 s; builds " + driver.builds);
        assertEquals(null, driver.failure);
        assertEquals(List.of(), app.errors());
        assertTrue(driver.shownOnEntry, "built and switched on when the designer opens");
        assertTrue(driver.quadAtTheSlot, "the quad lies in the GUI node at the slot's place in the drawing order");
        assertEquals(List.of("entry 1/1/1", "floor 1/2/2", "lightning 1/2/3", "moon 2/2/3", "cancel 3/2/4"),
                driver.builds, "sky/terrain/world: only the changed part is rebuilt (the first floor gives the empty "
                        + "tower its footprint: the terrain's socket); Cancel returns to the tower");
        assertEquals(40 * PreviewCamera.RADIANS_PER_PIXEL, driver.yawAfterDrag, 1e-5f, "dragged 40 pixels");
        assertEquals(0f, driver.yawAfterReset, 0f, "the right click resets the view");
        assertTrue(driver.fullScreen, "the double click fills the window");
        assertTrue(driver.smallAgain, "Esc returns to the small area");
        assertTrue(driver.hiddenOnLeaving, "leaving hides it, switches the viewport off and releases the sky");
        assertTrue(driver.backgroundBack, "the menu background shows again");
    }

    /**
     * Final review I1: Toolbox → Copy Level copies cells verbatim, objects included, so placing ZAP on level 0 and
     * copying it onto level 1 doubles the spider; before the fix {@code WorldState.load} threw "ZAP placed twice",
     * {@code EditorPreview.show} logged an error, hid the preview and kept it hidden for the rest of the visit, even
     * across an Undo. {@link de.latzig.game.tob.client.logic.editor.PreviewParts#viewable} now keeps only the first
     * copy, so the preview never has to fail for this; it stays visible throughout, and an Undo right after still
     * leaves it visible (it is not left permanently hidden by a build that happened to fail).
     */
    private static final class DuplicateSpiderDriver extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private int step;
        private volatile boolean visibleAfterDuplicate;
        private volatile boolean visibleAfterUndo;
        private volatile String failure;

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
            try {
                act(app, flow);
            } catch (RuntimeException e) {
                failure = e.toString();
                done.countDown();
            }
        }

        private void act(TobApp app, GameFlow flow) {
            boolean still = flow.fadeAlpha() == 0f;
            FrontEndState frontEnd = getState(FrontEndState.class);
            EditorPreview preview = frontEnd.preview();
            TowerDesigner designer = flow.designer();
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        flow.click(Hit.of(Widget.MENU_DESIGNER));
                        step++;
                    }
                }
                case 1 -> {
                    if (flow.screen() == GameFlow.Screen.EDITOR && still && preview.visible()) {
                        step++;
                    }
                }
                case 2 -> {
                    designer.model()
                            .apply(grid -> TowerEdits.place(grid, EditorElement.of(ObjectType.ZAP), 5, 4, 0).grid());
                    step++;
                }
                case 3 -> {
                    // the review's scenario: Toolbox -> Copy Level 0 -> 1, the spider copied along with the cells
                    designer.model().apply(grid -> EditorToolbox.copyLevel(grid, 0, 1));
                    step++;
                }
                case 4 -> {
                    visibleAfterDuplicate = preview.visible() && preview.viewPort().isEnabled();
                    designer.model().undo();
                    step++;
                }
                case 5 -> {
                    visibleAfterUndo = preview.visible() && preview.viewPort().isEnabled();
                    done.countDown();
                }
                default -> {
                }
            }
        }
    }

    @Test
    void aSpiderDoubledByCopyLevelDoesNotKillThePreviewAndUndoLeavesItVisible(@TempDir Path userDir)
            throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        DuplicateSpiderDriver driver = new DuplicateSpiderDriver();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), driver);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);

        app.start(JmeContext.Type.Headless);
        boolean finished = driver.done.await(60, TimeUnit.SECONDS);
        app.stop(true);

        assertTrue(finished, "within 60 s");
        assertEquals(null, driver.failure);
        assertEquals(List.of(), app.errors());
        assertTrue(driver.visibleAfterDuplicate,
                "the doubled spider does not kill the preview (PreviewParts.viewable keeps the first copy)");
        assertTrue(driver.visibleAfterUndo, "the preview is still visible right after the Undo");
    }
}
