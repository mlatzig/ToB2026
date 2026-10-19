package de.latzig.game.tob.client;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;

import de.latzig.game.tob.client.input.FrontEndInput;
import de.latzig.game.tob.client.input.MouseInput;
import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner;
import de.latzig.game.tob.client.logic.frontend.DoubleClick;
import de.latzig.game.tob.client.logic.frontend.GameFlow;
import de.latzig.game.tob.client.logic.frontend.StartScreens;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.ui.StartLayout;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.client.view.CanvasView;
import de.latzig.game.tob.client.view.SkyPreparation;

/**
 * The start screens (Phase 5a spec decision 2): every frame advances the {@link GameFlow} and draws its screen
 * ({@link StartScreens}) with the {@link CanvasView} in the GUI node over the menu background; keys, typed
 * characters and the mouse ({@link FrontEndInput}) go to the flow, a second click on the same widget within
 * {@link DoubleClick#INTERVAL} as a double click. It executes the flow's effects: the loader's steps
 * ({@link TobApp#loadNext}), the Briefing's sky preparation ({@link SkyPreparation}, decision 14; cancelled when the
 * Briefing is left without playing, handed to the game otherwise), starting a tower and quitting. In fast-forward
 * (headless tests) every frame counts {@link SimDriver#FRAME_SECONDS}.
 * <p>
 * Input ownership: the state's one raw input listener is registered only while it is enabled (attached), and the app
 * never has it and a game attached at the same time, so exactly one of them takes the input.
 * <p>
 * Phase 6b: on the Tower Designer's screen the mouse goes to the designer as presses (left and right), moves (with
 * the left button held: painting) and releases, past the double click (a question's buttons are clicked as on every
 * start screen); the menu background is paused meanwhile
 * ({@link TobApp#showMenuBackground}): the designer is drawn on black, as ToB 2009's. Phase 6b-2: the mouse's
 * position goes to the designer first (it drags the 3D preview), and the {@link EditorPreview} is shown in the slot
 * the designer's screen leaves for it every frame the designer is shown, and hidden (its scene released) otherwise.
 */
public final class FrontEndState extends BaseAppState {

    private static final Logger LOG = LoggerFactory.getLogger(FrontEndState.class);

    private final GameFlow flow;
    private final Texts texts;
    private final DoubleClick clicks = new DoubleClick();
    private CanvasView canvas;
    private FrontEndInput input;
    private StartLayout layout;
    private StartScreens.Frame frame;
    private SkyPreparation preparation;
    /** The track of the scroll bar whose thumb the left button holds (Phase 5b), or null. */
    private PanelLayout.Rect dragging;
    /** Phase 6b: the left button is held on the designer's screen (painting). */
    private boolean leftHeld;
    /** Phase 6b: whether the designer's screen was shown last frame (the menu background is paused then). */
    private boolean editorShown;
    /** Phase 6b-2: the designer's 3D live preview. */
    private EditorPreview preview;

    public FrontEndState(GameFlow flow, Texts texts) {
        this.flow = flow;
        this.texts = texts;
    }

    @Override
    protected void initialize(Application app) {
        TobApp tob = (TobApp) app;
        canvas = new CanvasView(tob.assets(), tob.assets().font());
        // Task 8 review: the state is detached and attached again around every test play (initialize runs again),
        // but the preview's viewport and shadow renderer are one per application: it is made once
        if (preview == null) {
            preview = new EditorPreview(tob);
        }
        input = new FrontEndInput(new FrontEndInput.Listener() {
            @Override
            public void key(UiKey key) {
                flow.key(key);
            }

            @Override
            public void typed(char c) {
                flow.typed(c);
            }

            @Override
            public void moved(float x, float y) {
                // final review I2: hovering never moves the focus; StartScreens highlights the button under the
                // cursor from the position the input keeps. Phase 5b: a held scroll bar thumb follows the cursor.
                if (dragging != null) {
                    flow.scrollDrag(StartScreens.trackFraction(dragging, y));
                }
                if (flow.screen() == GameFlow.Screen.EDITOR) {
                    flow.editorMouse(x, y);
                    flow.editorPointer(hit(x, y), leftHeld);
                }
            }

            @Override
            public void clicked(float x, float y) {
                if (flow.screen() == GameFlow.Screen.EDITOR && flow.modal() == GameFlow.Modal.NONE) {
                    leftHeld = true;
                    flow.editorMouse(x, y);
                    flow.editorPress(hit(x, y), false);
                    return;
                }
                Optional<GameFlow.Hit> hit = hit(x, y);
                if (hit.isPresent() && hit.get().widget() == GameFlow.Widget.SCROLL_TRACK) {
                    // Phase 5b: the scroll bar pages or grabs its thumb at the cursor's place on the track
                    dragging = track();
                    if (dragging != null) {
                        flow.scrollPress(StartScreens.trackFraction(dragging, y));
                    }
                    return;
                }
                clicks.click(flow, hit, x, y, System.nanoTime() / 1e9);
            }

            @Override
            public boolean pressed(int keyCode, boolean leftCtrl) {
                // Phase 5b decision 5: while the Options window asks for a key, every key goes there first
                return flow.captureKey(keyCode, leftCtrl);
            }

            @Override
            public void wheel(int notches) {
                flow.wheel(notches);
            }

            @Override
            public void released(float x, float y) {
                dragging = null;
                flow.scrollRelease();
                leftHeld = false;
                flow.editorRelease();
            }

            @Override
            public void rightClicked(float x, float y) {
                if (flow.screen() == GameFlow.Screen.EDITOR && flow.modal() == GameFlow.Modal.NONE) {
                    flow.editorMouse(x, y);
                    flow.editorPress(hit(x, y), true);
                }
            }
        });
    }

    /** The scroll bar's track of the last frame, or null. */
    private PanelLayout.Rect track() {
        return frame == null ? null : frame.areas().stream()
                .filter(area -> area.hit().widget() == GameFlow.Widget.SCROLL_TRACK).findFirst()
                .map(StartScreens.Area::rect).orElse(null);
    }

    @Override
    protected void cleanup(Application app) {
        preview.hide();
        if (preparation != null) {
            preparation.cancel();
            preparation = null;
        }
    }

    @Override
    protected void onEnable() {
        Application app = getApplication();
        ((TobApp) app).getGuiNode().attachChild(canvas.node());
        app.getInputManager().addRawInputListener(input);
        app.getInputManager().setMouseCursor(MouseInput.hiddenCursor());
        Vector2f cursor = app.getInputManager().getCursorPosition();
        input.seedPosition(cursor.x, cursor.y);
        // Phase 6b: Ctrl+Z and Ctrl+Y as the keyboard layout labels the keys (the name is asked at each press)
        InputManager inputs = app.getInputManager();
        input.undoKeys(inputs::getKeyName);
        LOG.info("Ctrl+Z/Ctrl+Y follow the keyboard layout: the keys at the US Z and Y positions are named {} and {}",
                inputs.getKeyName(KeyInput.KEY_Z), inputs.getKeyName(KeyInput.KEY_Y));
        clicks.reset();
        // M2 (final review): a Ctrl held across the state switch must not leak into the next key capture.
        input.reset();
    }

    @Override
    protected void onDisable() {
        canvas.node().removeFromParent();
        getApplication().getInputManager().removeRawInputListener(input);
        getApplication().getInputManager().setMouseCursor(null);
        clicks.reset();
        dragging = null;
        flow.scrollRelease();
        leftHeld = false;
        flow.editorRelease();
        // M2 (final review): reset the left-Ctrl state so it cannot leak into the next time the screens are shown.
        input.reset();
        // Phase 6b-2: a test play (or the app's end) leaves the screens: the preview's scene is released
        preview.hide();
    }

    @Override
    public void update(float tpf) {
        TobApp app = (TobApp) getApplication();
        double seconds = app.config().fastForward() ? SimDriver.FRAME_SECONDS : tpf;
        if (flow.screen() == GameFlow.Screen.LOADER) {
            flow.loadProgress(app.loadNext());
        }
        flow.update(seconds);
        if (preparation != null) {
            if (flow.screen() == GameFlow.Screen.BRIEFING) {
                flow.skyProgress(preparation.progress());
            } else if (flow.screen() != GameFlow.Screen.GAME) {
                preparation.cancel();
                preparation = null;
            }
        }
        for (Optional<GameFlow.Effect> effect = flow.poll(); effect.isPresent(); effect = flow.poll()) {
            switch (effect.get()) {
                case GameFlow.PrepareTower prepare -> {
                    if (preparation != null) {
                        preparation.cancel();
                    }
                    // Phase 5b: the Moon detail option, as the game's sky will use it; Phase 5e: no clouds in Classic
                    preparation = SkyPreparation.start(prepare.tower(),
                            app.config().sky().withHalfMoonDetail(app.settings().current().halfMoonDetail()),
                            app.settings().current().classicLook());
                    flow.skyProgress(preparation.progress());
                }
                case GameFlow.StartTower start -> {
                    Optional<SkyPreparation> sky = Optional.ofNullable(preparation);
                    preparation = null;
                    app.startAttempt(start.tower(), sky);
                    return;
                }
                case GameFlow.Quit quit -> {
                    app.quit();
                    return;
                }
            }
        }
        boolean editor = flow.screen() == GameFlow.Screen.EDITOR;
        if (editor != editorShown) {
            editorShown = editor;
            app.showMenuBackground(!editor);
        }
        Camera cam = app.getCamera();
        if (layout == null || cam.getWidth() > 0 && cam.getHeight() > 0
                && (cam.getWidth() != layout.width() || cam.getHeight() != layout.height())) {
            layout = new StartLayout(cam.getWidth(), cam.getHeight());
        }
        frame = StartScreens.draw(flow, texts, layout, input.x(), input.y());
        canvas.show(frame.drawing(), flow.fadeAlpha(), layout.width(), layout.height());
        if (editor && frame.drawing().viewports().size() == 1) {
            TowerDesigner designer = flow.designer();
            preview.show(designer.shown(), designer.preview(), frame.drawing().viewports().get(0), seconds);
        } else {
            preview.hide();
        }
    }

    private Optional<GameFlow.Hit> hit(float x, float y) {
        return frame == null ? Optional.empty() : frame.hit(x, y);
    }

    public GameFlow flow() {
        return flow;
    }

    /** The frame drawn last, or null before the first (tests: where to click). */
    public StartScreens.Frame frame() {
        return frame;
    }

    /** The input listener (tests: simulated clicks and keys). */
    FrontEndInput input() {
        return input;
    }

    /** The designer's 3D preview (tests). */
    EditorPreview preview() {
        return preview;
    }
}
