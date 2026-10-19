package de.latzig.game.tob.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;

import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;

/**
 * The sun's shadows (Phase 4a spec §2 decision 6): one jME directional shadow map renderer for the tower's sun, at a
 * fixed medium quality ({@value #MAP_SIZE}² maps, {@value #SPLITS} splits, PCF4 edges; the quality option comes in
 * Phase 5). Shadows are drawn up to {@value #DISTANCE} cells from the eye, fading over the last {@value #FADE}, and
 * darken by {@link #INTENSITY} (0.7). Casters and receivers are chosen per node: the world (blocks, spiders, enemies)
 * casts and receives, the terrain and the socket receive, the sky does neither. The renderer works per frame, so the
 * shadows follow moving objects. Phase 6a: every tower start sets the distance for its tower
 * ({@link ShadowRange#distance}; larger for a tower beyond the 8 × 8 area).
 *
 * <p>Final review I1: there is one instance per application ({@code TobApp.shadows()}), and every tower start only
 * points it at its own sun ({@link #setSun}). jME 3.9's {@code AbstractShadowRenderer.cleanup()} frees nothing, so a
 * renderer per start would keep its {@value #SPLITS} depth maps and its dummy colour map in video memory until a full
 * garbage collection.
 */
public final class ShadowSetup {

    public static final int MAP_SIZE = 2048;
    public static final int SPLITS = 3;
    /**
     * How much the shadows darken: {@link MaterialTuning#SHADOW_INTENSITY} (Phase 4b-4 decision 6: 0.7; Phase 4a's
     * acceptance on 2026-10-10 had raised ToB 2005's 80/255 stencil shadow alpha to 0.55).
     */
    public static final float INTENSITY = MaterialTuning.SHADOW_INTENSITY;
    public static final float DISTANCE = ShadowRange.DISTANCE;
    public static final float FADE = ShadowRange.FADE;

    private final DirectionalLightShadowRenderer renderer;

    public ShadowSetup(AssetManager manager) {
        this(manager, MAP_SIZE, SPLITS);
    }

    /**
     * A shadow renderer with {@code mapSize}² maps in {@code splits} splits (Phase 6b-2: the Tower Designer's small
     * preview has its own, smaller one; one per application as well).
     */
    public ShadowSetup(AssetManager manager, int mapSize, int splits) {
        renderer = new DirectionalLightShadowRenderer(manager, mapSize, splits);
        renderer.setShadowIntensity(INTENSITY);
        renderer.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        renderer.setShadowZExtend(DISTANCE);
        renderer.setShadowZFadeLength(FADE);
    }

    /** Makes {@code sun}, the light of the tower being shown, cast the shadows up to {@link #DISTANCE}. */
    public void setSun(DirectionalLight sun) {
        setSun(sun, DISTANCE);
    }

    /**
     * Makes {@code sun}, the light of the tower being shown, cast the shadows up to {@code distance} cells from the eye
     * (Phase 6a: {@link ShadowRange#distance} of the tower).
     */
    public void setSun(DirectionalLight sun, float distance) {
        renderer.setLight(sun);
        renderer.setShadowZExtend(distance);
    }

    /** Adds the shadow renderer to {@code viewPort} (once). */
    public void attach(ViewPort viewPort) {
        if (!viewPort.getProcessors().contains(renderer)) {
            viewPort.addProcessor(renderer);
        }
    }

    /** Removes the shadow renderer from {@code viewPort}. */
    public void detach(ViewPort viewPort) {
        viewPort.removeProcessor(renderer);
    }

    /** The jME shadow renderer (tests). */
    public DirectionalLightShadowRenderer renderer() {
        return renderer;
    }
}
