package de.latzig.game.tob.client.view;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;

import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.effects.Lightning;

/**
 * The ground haze of a tower with the blue pulse flag (Phase 4b-2 spec §2 decision 4): ToB 2005's quad
 * {@code 0930} (±4096 units, 3.5 units up, just above the level-0 floor tops, the texture repeated 32 times) around
 * the tower centre with the generated water texture ({@link AssetLibrary#hazeMaterial}), unlit and alpha-blended,
 * as ToB 2005 drew it ({@code twr.cpp:2389-2391}). Its brightness pulses gently with the simulation time
 * ({@link AmbientTuning#hazeBrightness}; amplitude 0 is ToB 2005's static haze). It is marked for
 * {@link HazeFirstComparator}, so it is drawn before every other transparent geometry and never over them. Phase 5e:
 * the material comes from the scene ({@link SceneMaterials#haze}); the pulse scales its own colour (white for the
 * Remake look's water texture, the translucent blue of the Classic Look) and keeps its alpha.
 */
public final class HazeView {

    public static final String MESH = "meshes/0930-bluepulse.tobmesh";

    private final Geometry geometry;
    private final Material material;
    /** The material colour, rewritten in place every frame (no allocation per frame). */
    private final ColorRGBA colour = new ColorRGBA(1f, 1f, 1f, 1f);
    /** The material's own colour, scaled by the pulse. */
    private final ColorRGBA base;
    private float brightness = 1f;

    /** @throws AssetLoadException if the haze mesh, its base entry or its texture is missing */
    public HazeView(AssetLibrary assets) {
        this(assets, assets.hazeMaterial());
    }

    /**
     * Phase 5e: the haze on {@code material} (its {@code Color} is the colour the pulse scales).
     *
     * @throws AssetLoadException if the haze mesh is missing
     */
    public HazeView(AssetLibrary assets, Material material) {
        this.material = material;
        base = ((ColorRGBA) material.getParam("Color").getValue()).clone();
        geometry = new Geometry("haze", new KeyframeMesh(assets.mesh(MESH)).mesh());
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Transparent);
        geometry.setShadowMode(ShadowMode.Off);
        geometry.setLocalTranslation(Lightning.CENTRE, 0f, Lightning.CENTRE);
        HazeFirstComparator.mark(geometry);
    }

    public Geometry geometry() {
        return geometry;
    }

    /** The haze at simulation time {@code now} (frames: steps + alpha). */
    public void update(float now) {
        brightness = AmbientTuning.hazeBrightness(now);
        material.setColor("Color", colour.set(base.r * brightness, base.g * brightness, base.b * brightness, base.a));
    }

    /** The brightness of the last {@link #update} (tests). */
    float brightness() {
        return brightness;
    }
}
