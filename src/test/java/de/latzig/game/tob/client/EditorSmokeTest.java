package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerLibraries;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Phase 6b spec §4 (headless): main menu → Tower Designer; a small tower is built with the mouse through the real
 * input path (a floor stroke, the grabber and a klondike from the palette), test-played with the enemies frozen,
 * abandoned back to the editor (status, cursor and undo history kept, the menu background paused, no profile); Clear
 * Level asks and NO is clicked with the mouse; the tower is saved as {@code smoke}, reloaded from
 * {@code <userdir>/towers/smoke.json}; Exit leads to the main menu.
 */
class EditorSmokeTest {

    private static final class Builder extends BaseAppState {

        private final CountDownLatch done = new CountDownLatch(1);
        private final List<GameFlow.Screen> screens = new ArrayList<>();
        private int step;
        private TowerGrid built;
        private volatile boolean backgroundInEditor = true;
        private volatile String testedTower = "";
        private volatile boolean frozen;
        private volatile String statusAfterTest = "";
        private volatile boolean undoAfterTest;
        private volatile boolean reloaded;
        private volatile boolean noByMouse;
        private volatile String failure;
        /** Final review M5: F12 during the test play must be ignored (never "REC NEXT" in the caption strip). */
        private volatile String captionAfterF12 = "";
        /** Final review M9: F9 restarts the test play with the same options (freeze enemies, no recording). */
        private volatile boolean restartKeptTestPlayOptions;
        private GameState firstTestGame;
        /** Phase 6b-2 (Task 8 review): the preview before the test play; one instance and one viewport survive it. */
        private EditorPreview previewBefore;
        private volatile boolean previewKept;
        private volatile long previewViewports = -1;

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
            if (screens.isEmpty() || screens.get(screens.size() - 1) != flow.screen()) {
                screens.add(flow.screen());
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
            switch (step) {
                case 0 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        flow.click(Hit.of(Widget.MENU_DESIGNER));
                        step++;
                    }
                }
                case 1 -> {
                    if (flow.screen() == GameFlow.Screen.EDITOR && still && frontEnd.frame() != null
                            && cell(frontEnd, 4, 7) != null) {
                        backgroundInEditor = getState(MenuBackgroundState.class) != null;
                        previewBefore = frontEnd.preview();
                        FrontEndInput input = frontEnd.input();
                        press(input, cell(frontEnd, 4, 7), MouseInput.BUTTON_LEFT);
                        for (int x = 5; x <= 7; x++) {
                            float[] at = cell(frontEnd, x, 7);
                            input.onMouseMotionEvent(new MouseMotionEvent((int) at[0], (int) at[1], 1, 0, 0, 0));
                        }
                        release(input, cell(frontEnd, 7, 7));
                        step++;
                    }
                }
                case 2 -> {
                    click(frontEnd, new Hit(Widget.EDITOR_PALETTE, palette(ObjectType.GRB)));
                    press(frontEnd.input(), cell(frontEnd, 4, 7), MouseInput.BUTTON_LEFT);
                    release(frontEnd.input(), cell(frontEnd, 4, 7));
                    step++;
                }
                case 3 -> {
                    click(frontEnd, new Hit(Widget.EDITOR_PALETTE, palette(ObjectType.KLD)));
                    press(frontEnd.input(), cell(frontEnd, 6, 7), MouseInput.BUTTON_LEFT);
                    release(frontEnd.input(), cell(frontEnd, 6, 7));
                    press(frontEnd.input(), cell(frontEnd, 6, 7), MouseInput.BUTTON_RIGHT);
                    press(frontEnd.input(), cell(frontEnd, 7, 7), MouseInput.BUTTON_LEFT);
                    release(frontEnd.input(), cell(frontEnd, 7, 7));
                    built = flow.designer().model().tower().grid();
                    choose(flow, Command.FREEZE);
                    choose(flow, Command.PLAY);
                    step++;
                }
                case 4 -> {
                    GameState game = getState(GameState.class);
                    if (game == null || game.driver() == null) {
                        return;
                    }
                    if (firstTestGame == null) {
                        firstTestGame = game;
                    }
                    if (game == firstTestGame) {
                        if (game.driver().ticks() == 5) {
                            // Final review M5: F12 during a test play must not toggle or show the recording state.
                            game.onAction(GameAction.TOGGLE_RECORDING);
                        }
                        if (game.driver().ticks() >= 8) {
                            captionAfterF12 = game.caption();
                            // Final review M9: F9 restarts the test play with the same options kept through
                            // TobApp.restartAttempt -> startTower -> startAttempt.
                            game.onAction(GameAction.RESTART_TOWER);
                            game.onAction(GameAction.TURN_LEFT);
                            game.onAction(GameAction.MENU_CONFIRM);
                        }
                        return;
                    }
                    // the restarted attempt: a new GameState, the same test play
                    if (game.driver().ticks() >= 5) {
                        testedTower = game.tower().id();
                        frozen = game.options().enemiesFrozen();
                        restartKeptTestPlayOptions = frozen && testedTower.equals(firstTestGame.tower().id())
                                && !game.caption().contains("REC");
                        game.onAction(GameAction.ABANDON_TOWER);
                        game.onAction(GameAction.TURN_LEFT);
                        game.onAction(GameAction.MENU_CONFIRM);
                        step++;
                    }
                }
                case 5 -> {
                    if (flow.screen() == GameFlow.Screen.EDITOR && still) {
                        TowerDesigner designer = flow.designer();
                        statusAfterTest = designer.model().status();
                        undoAfterTest = designer.model().canUndo();
                        backgroundInEditor |= getState(MenuBackgroundState.class) != null;
                        previewKept = frontEnd.preview() == previewBefore && previewBefore.visible()
                                && previewBefore.viewPort().isEnabled();
                        previewViewports = app.getRenderManager().getPreViews().stream()
                                .filter(v -> v.getName().equals("editor-preview")).count();
                        choose(flow, Command.CLEAR_LEVEL);
                        step++;
                    }
                }
                case 6 -> {
                    if (frontEnd.frame().areas().stream().anyMatch(a -> a.hit().widget() == Widget.NO)) {
                        click(frontEnd, Hit.of(Widget.NO));
                        TowerDesigner designer = flow.designer();
                        noByMouse = flow.modal() == GameFlow.Modal.NONE
                                && designer.model().tower().grid().equals(built);
                        choose(flow, Command.SAVE_AS);
                        for (char c : "smoke".toCharArray()) {
                            flow.typed(c);
                        }
                        flow.key(UiKey.ENTER);
                        choose(flow, Command.NEW);
                        choose(flow, Command.OPEN);
                        int row = designer.libraryDialog().orElseThrow().entries().indexOf(
                                new TowerLibraries.Entry("smoke", false));
                        flow.click(new Hit(Widget.EDITOR_DIALOG_LIST, row));
                        flow.click(new Hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK));
                        reloaded = designer.model().library().equals(Optional.of("smoke"))
                                && designer.model().tower().grid().equals(built);
                        flow.key(UiKey.ESCAPE);
                        step++;
                    }
                }
                case 7 -> {
                    if (flow.screen() == GameFlow.Screen.MENU && still) {
                        done.countDown();
                    }
                }
                default -> {
                }
            }
        }

        private static void choose(GameFlow flow, Command command) {
            flow.click(new Hit(Widget.EDITOR_MENU, command.menu().ordinal()));
            flow.click(new Hit(Widget.EDITOR_ITEM, command.ordinal()));
        }

        private static int palette(ObjectType object) {
            return EditorElement.PALETTE.indexOf(EditorElement.of(object));
        }

        /** The centre of {@code hit}'s area in the last frame, or null. */
        private static float[] centre(FrontEndState frontEnd, Hit hit) {
            Optional<PanelLayout.Rect> rect = frontEnd.frame().areas().stream().filter(a -> a.hit().equals(hit))
                    .map(a -> a.rect()).findFirst();
            return rect.map(r -> new float[] {r.x() + r.width() / 2f, r.y() + r.height() / 2f}).orElse(null);
        }

        private static float[] cell(FrontEndState frontEnd, int x, int y) {
            return centre(frontEnd, new Hit(Widget.EDITOR_CELL, y * TowerGrid.WIDTH + x));
        }

        private static void click(FrontEndState frontEnd, Hit hit) {
            float[] at = centre(frontEnd, hit);
            press(frontEnd.input(), at, MouseInput.BUTTON_LEFT);
            release(frontEnd.input(), at);
        }

        private static void press(FrontEndInput input, float[] at, int button) {
            input.onMouseMotionEvent(new MouseMotionEvent((int) at[0], (int) at[1], 0, 0, 0, 0));
            input.onMouseButtonEvent(new MouseButtonEvent(button, true, (int) at[0], (int) at[1]));
        }

        private static void release(FrontEndInput input, float[] at) {
            input.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) at[0], (int) at[1]));
        }
    }

    @Test
    void aTowerIsBuiltTestPlayedSavedAndReloaded(@TempDir Path userDir) throws InterruptedException {
        Path assets = Path.of(System.getProperty(AppConfig.ASSETS_PROPERTY, AppConfig.DEFAULT_ASSETS));
        assumeTrue(Files.isRegularFile(assets.resolve("elements.json"))
                && Files.isRegularFile(assets.resolve("towers/original.json")), "generated assets not available");
        Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
        Builder builder = new Builder();
        TobApp app = new TobApp(new AppConfig(assets, Optional.empty(), true, userDir), builder);
        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(Integer.MAX_VALUE);
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        long start = System.nanoTime();

        app.start(JmeContext.Type.Headless);
        boolean finished = builder.done.await(60, TimeUnit.SECONDS);
        app.stop(true);
        System.out.println("EditorSmokeTest: " + (System.nanoTime() - start) / 1_000_000 + " ms, " + builder.screens);

        assertTrue(finished, "built, tested, saved and reloaded within 60 s; screens " + builder.screens);
        assertEquals(null, builder.failure);
        assertEquals(List.of(), app.errors());
        assertEquals(List.of(), app.messages());
        assertTrue(builder.screens.containsAll(List.of(GameFlow.Screen.EDITOR, GameFlow.Screen.GAME)));
        assertFalse(builder.screens.contains(GameFlow.Screen.RESULT), "a test play has no result window");
        assertTrue(builder.testedTower.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"),
                "Phase 6d decision 7: a new tower's id is a UUID: " + builder.testedTower);
        assertTrue(builder.frozen, "Freeze Enemies");
        assertFalse(builder.captionAfterF12.contains("REC"), "M5: F12 is ignored during a test play");
        assertTrue(builder.restartKeptTestPlayOptions, "M9: F9 keeps the test play's options (frozen, no recording)");
        assertEquals("Test play: Attempt Abandoned", builder.statusAfterTest);
        assertTrue(builder.undoAfterTest, "the undo history survives the test play");
        assertFalse(builder.backgroundInEditor, "the menu background pauses in the designer");
        assertTrue(builder.previewKept, "the same 3D preview is shown again after the test play");
        assertEquals(1, builder.previewViewports, "one preview viewport (and shadow renderer) per application");
        assertTrue(builder.noByMouse, "a question's NO is clicked through the input; the level stays");
        assertTrue(builder.reloaded, "the saved library opens again with the tower");
        assertTrue(Files.isRegularFile(userDir.resolve("towers").resolve("smoke.json")));
        assertFalse(Files.exists(userDir.resolve("savegames")), "no profile");
        assertFalse(Files.exists(userDir.resolve("recordings")), "no recording");
    }
}
