package de.latzig.game.tob.client;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.editor.PreviewCamera;
import de.latzig.game.tob.client.logic.editor.PreviewParts;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.frontend.Settings;
import de.latzig.game.tob.client.logic.game.PanelLayout;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.client.view.AssetLibrary;
import de.latzig.game.tob.client.view.CameraController;
import de.latzig.game.tob.client.view.CanvasView;
import de.latzig.game.tob.client.view.HazeFirstComparator;
import de.latzig.game.tob.client.view.SceneLook;
import de.latzig.game.tob.client.view.ShadowSetup;
import de.latzig.game.tob.client.view.SkyView;
import de.latzig.game.tob.client.view.WorldView;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The Tower Designer's 3D live preview (Phase 6b spec decision 3): the selected tower (as an open Settings or Effects
 * dialog would leave it) in its world — terrain, sky with moon, stars and clouds, sun, shadows and the ambient
 * effects — as the game builds it ({@code GameState}, {@link MenuBackgroundState}), seen through the
 * {@link PreviewCamera}, never simulated (the objects stand where the tower places them; the effects run on the
 * preview's clock). Its own small scene is rendered by a viewport of its own into a texture, which a quad in the GUI
 * node shows at the {@link Drawing.Viewport} the designer's screen leaves for it, in that screen's drawing order: the
 * menus, the dialogs, the questions and the cursor lie over it; on the full screen it covers the designer.
 * <p>
 * Every frame it compares the tower's {@link PreviewParts} with the ones it shows and rebuilds only the parts that
 * changed (at most once per frame, so a painting stroke rebuilds the world once per frame). Hiding it (the designer
 * left, a test play started) releases the scene — the sky's moon generation is cancelled — and switches the viewport
 * off; the viewport, its render target and its shadow renderer ({@value #SHADOW_MAP}² maps, {@value #SHADOW_SPLITS}
 * splits: one per application, as {@link TobApp#shadows()}, since jME frees nothing of a shadow renderer) stay for the
 * next time. The menu background is paused while the designer is shown, so the shared fog colour and the
 * application's own shadow renderer are not disturbed; the next demo tower sets its fog colour again.
 */
public final class EditorPreview {

    private static final Logger LOG = LoggerFactory.getLogger(EditorPreview.class);

    public static final int SHADOW_MAP = 1024;
    public static final int SHADOW_SPLITS = 2;
    private static final ColorRGBA AMBIENT_COLOUR = new ColorRGBA(0.4f, 0.4f, 0.45f, 1f);

    private final TobApp app;
    private final AssetLibrary assets;
    private final Camera camera = new Camera(1, 1);
    /** The preview's scene: the sky beside the lit part (the moon has its own lights). */
    private final Node root = new Node("editor-preview");
    private final Node lit = new Node("editor-preview-lit");
    private final DirectionalLight sun = new DirectionalLight(SkyView.SUN_DIRECTION.clone(), ColorRGBA.White.clone());
    private final AmbientLight ambient = new AmbientLight(AMBIENT_COLOUR.clone());
    private final ColorRGBA sunShown = new ColorRGBA();
    private final ColorRGBA ambientShown = new ColorRGBA();
    private final Geometry quad = new Geometry("editor-preview", new Quad(1f, 1f));
    private final Material material;
    private ViewPort viewPort;
    private ShadowSetup shadows;
    private Texture2D texture;
    /**
     * The render target shown before the current one (the small slot while the full screen is shown, and back), kept
     * so that toggling the full screen reuses it instead of allocating a new one each time; null before a size change.
     */
    private Texture2D spareTexture;
    private FrameBuffer spareBuffer;
    private FrameBuffer buffer;
    private PreviewParts parts;
    private SkyView sky;
    /** The ground: the terrain and socket, or the Classic Look's flat plane (Phase 5e). */
    private Node terrain;
    private WorldView world;
    /**
     * Phase 5e decision 5: the look of the preview's scene, taken from the Look option when it is built afresh (the
     * designer shown again after it was left), kept by the partial rebuilds of the edits.
     */
    private boolean classic;
    private boolean ambientEffects = true;
    private boolean visible;
    /**
     * Final review I1: the {@link PreviewParts} key of a build that just failed (a missing asset, or a tower state
     * the designer itself can produce, e.g. a spider placed twice by Copy Level before {@link PreviewParts#viewable}
     * covered it); null while nothing is failing. Skipped on {@link #show} as long as the shown tower's parts are
     * still the same key, so one failing edit does not keep failing the preview once the tower changes again (Undo,
     * another edit, another tower).
     */
    private PreviewParts failedParts;
    /** Failure keys already logged (final review I1): a cause that keeps recurring is reported only once. */
    private final Set<PreviewParts> loggedFailures = new HashSet<>();
    private float now;
    private int skyBuilds;
    private int terrainBuilds;
    private int worldBuilds;

    public EditorPreview(TobApp app) {
        this.app = app;
        this.assets = app.assets();
        material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        quad.setMaterial(material);
        lit.addLight(sun);
        lit.addLight(ambient);
        root.attachChild(lit);
    }

    /**
     * Shows {@code tower} through {@code view} in {@code slot} (GUI pixels) this frame, {@code seconds} after the last
     * one: rebuilds what changed, places the camera, runs the effects. A failure (a missing asset, or a tower state
     * the preview cannot load) is logged once per distinct cause and the preview stays hidden until the shown tower's
     * {@link PreviewParts} differ from the ones that failed (final review I1); the designer itself goes on either way.
     */
    public void show(TowerDefinition tower, PreviewCamera view, Drawing.Viewport slot, double seconds) {
        // Phase 5e: a preview to be built afresh will take the Look option (rebuild), so its key does as well
        PreviewParts next = PreviewParts.of(tower, parts == null ? app.settings().current().classicLook() : classic);
        if (next.equals(failedParts)) {
            return;
        }
        try {
            frame(tower, view, slot, seconds);
            failedParts = null;
        } catch (RuntimeException e) {
            if (loggedFailures.add(next)) {
                LOG.error("Editor preview of tower {} failed: {}", tower.id(), e.getMessage(), e);
            }
            hide();
            failedParts = next;
        }
    }

    private void frame(TowerDefinition tower, PreviewCamera view, Drawing.Viewport slot, double seconds) {
        if (viewPort == null) {
            create();
        }
        PanelLayout.Rect rect = slot.rect();
        int width = Math.max(1, Math.round(rect.width()));
        int height = Math.max(1, Math.round(rect.height()));
        if (texture == null || texture.getImage().getWidth() != width || texture.getImage().getHeight() != height) {
            target(width, height);
        }
        rebuild(tower);
        PreviewCamera.View eye = view.view(tower, (float) width / height);
        camera.setLocation(new Vector3f(eye.eye().x(), eye.eye().y(), eye.eye().z()));
        camera.lookAt(new Vector3f(eye.target().x(), eye.target().y(), eye.target().z()), Vector3f.UNIT_Y);
        now += (float) (seconds / SimDriver.FRAME_SECONDS);
        world.updateEffects(now, camera.getLeft(), camera.getUp());
        sky.update(camera.getLocation(), now, ambientEffects);
        Settings options = app.settings().current();
        float bright = options.brightnessFactor();
        float flash = AmbientTuning.sceneFlash(world.flash(now), options.flashFactor());
        int sunColour = SceneLook.sun(tower, classic);
        sun.setColor(sunShown.set(AmbientTuning.light(((sunColour >> 16) & 0xFF) / 255f, bright, flash),
                AmbientTuning.light(((sunColour >> 8) & 0xFF) / 255f, bright, flash),
                AmbientTuning.light((sunColour & 0xFF) / 255f, bright, flash), 1f));
        ambient.setColor(ambientShown.set(AmbientTuning.light(AMBIENT_COLOUR.r, bright, flash),
                AmbientTuning.light(AMBIENT_COLOUR.g, bright, flash),
                AmbientTuning.light(AMBIENT_COLOUR.b, bright, flash), 1f));
        quad.setLocalTranslation(rect.x(), rect.y(), CanvasView.Z_BASE + slot.order() * CanvasView.Z_STEP);
        quad.setLocalScale(rect.width(), rect.height(), 1f);
        if (!visible) {
            app.getGuiNode().attachChild(quad);
            viewPort.setEnabled(true);
            visible = true;
        }
        root.updateLogicalState((float) seconds);
        root.updateGeometricState();
    }

    /** Hides the preview and releases its scene (the designer is left or a test play starts); idempotent. */
    public void hide() {
        // Task 8 review: a first build that failed half-way leaves visible false and parts null, but may have built
        // the sky (its moon generation running) or attached parts to the scene: those are released as well
        if (!visible && parts == null && sky == null && lit.getQuantity() == 0) {
            return;
        }
        if (viewPort != null) {
            viewPort.setEnabled(false);
        }
        quad.removeFromParent();
        visible = false;
        if (sky != null) {
            sky.node().removeFromParent();
            sky.dispose();
        }
        lit.detachAllChildren();
        sky = null;
        terrain = null;
        world = null;
        parts = null;
    }

    /**
     * The viewport, its scene and its shadow renderer, once per application ({@link FrontEndState} keeps one preview
     * for the application's life). The shadow renderer is made first, so a failure there registers no viewport and
     * the next show tries again.
     */
    private void create() {
        ShadowSetup setup = new ShadowSetup(app.getAssetManager(), SHADOW_MAP, SHADOW_SPLITS);
        ViewPort created = app.getRenderManager().createPreView("editor-preview", camera);
        created.setClearFlags(true, true, true);
        created.attachScene(root);
        HazeFirstComparator.install(created);
        setup.attach(created);
        created.setEnabled(false);
        shadows = setup;
        viewPort = created;
    }

    /**
     * A render target of {@code width} × {@code height} pixels (the slot's size changed: the full screen, a resized
     * window). The last two targets are kept, so toggling the full screen switches between them without allocating;
     * a target of a third size replaces the older one, which is left to jME's native object manager, which frees it
     * once it is unreferenced: deleting it at once ({@code Renderer.deleteFrameBuffer}) left the next target rendering
     * wrongly in the offscreen test (objects missing).
     */
    private void target(int width, int height) {
        Texture2D previousTexture = texture;
        FrameBuffer previousBuffer = buffer;
        if (spareTexture != null && spareTexture.getImage().getWidth() == width
                && spareTexture.getImage().getHeight() == height) {
            texture = spareTexture;
            buffer = spareBuffer;
        } else {
            texture = new Texture2D(width, height, Image.Format.RGBA8);
            texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            buffer = new FrameBuffer(width, height, 1);
            buffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(texture));
            buffer.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
        }
        spareTexture = previousTexture;
        spareBuffer = previousBuffer;
        viewPort.setOutputFrameBuffer(buffer);
        camera.resize(width, height, true);
        camera.setFrustumPerspective(CameraController.FIELD_OF_VIEW_DEGREES, (float) width / height,
                CameraController.NEAR, CameraController.FAR);
        material.setTexture("ColorMap", texture);
    }

    /** Rebuilds the parts of {@code tower}'s scene that differ from the ones shown. */
    private void rebuild(TowerDefinition tower) {
        Settings options = app.settings().current();
        if (parts == null) {
            classic = options.classicLook();
        }
        PreviewParts next = PreviewParts.of(tower, classic);
        if (next.equals(parts)) {
            return;
        }
        long start = System.nanoTime();
        SceneLook look = SceneLook.of(assets, tower, classic);
        TowerColours colours = look.colours();
        look.applyFog(assets);
        viewPort.setBackgroundColor(new ColorRGBA(((colours.skyHorizon() >> 16) & 0xFF) / 255f,
                ((colours.skyHorizon() >> 8) & 0xFF) / 255f, (colours.skyHorizon() & 0xFF) / 255f, 1f));
        if (parts == null || !next.sky().equals(parts.sky())) {
            if (sky != null) {
                sky.node().removeFromParent();
                sky.dispose();
            }
            sky = look.sky(assets, tower, app.config().sky().withHalfMoonDetail(options.halfMoonDetail()),
                    Optional.empty());
            root.attachChild(sky.node());
            skyBuilds++;
        }
        if (parts == null || !next.ground().equals(parts.ground())) {
            if (terrain != null) {
                terrain.removeFromParent();
            }
            terrain = look.ground(assets, tower);
            lit.attachChild(terrain);
            terrainBuilds++;
        }
        if (parts == null || !next.world().equals(parts.world())) {
            if (world != null) {
                world.node().removeFromParent();
            }
            ambientEffects = options.ambientEffects();
            WorldState state = WorldState.load(PreviewParts.viewable(tower), new SimOptions(true));
            world = look.world(assets, state, ambientEffects);
            world.update(0f, CameraId.NORTH);
            lit.attachChild(world.node());
            shadows.setSun(sun, ShadowRange.distance(tower));
            worldBuilds++;
        }
        parts = next;
        LOG.debug("Editor preview rebuilt in {} ms", (System.nanoTime() - start) / 1_000_000);
    }

    /** Whether the preview is shown (tests). */
    boolean visible() {
        return visible;
    }

    /** The viewport, or null before the first {@link #show} (tests). */
    ViewPort viewPort() {
        return viewPort;
    }

    /** Whether the preview's scene is in the Classic Look (tests, Phase 5e). */
    boolean classic() {
        return classic;
    }

    /** The sky shown, or null while hidden (tests). */
    SkyView sky() {
        return sky;
    }

    /** How often each part was built: sky, terrain, world (tests: what an edit rebuilds). */
    int[] builds() {
        return new int[] {skyBuilds, terrainBuilds, worldBuilds};
    }

    /** The GUI quad that shows the rendered preview (tests). */
    Geometry quad() {
        return quad;
    }
}
