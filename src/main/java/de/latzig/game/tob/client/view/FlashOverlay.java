package de.latzig.game.tob.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;

/**
 * The lightning flash's weak white full-screen overlay (Phase 4b-2 spec §2 decision 2), ToB 2005's blend sprite in
 * front of the camera ({@code bpe.cpp:723-752}), much fainter: a white quad over the 3D view in the GUI node, between
 * the noise ({@code z −1}) and the HUD, alpha-blended with the alpha {@link #update} receives; hidden at 0.
 */
public final class FlashOverlay {

    private final Geometry geometry;
    private final Material material;
    /** The material colour, rewritten in place every frame (no allocation per frame). */
    private final ColorRGBA colour = new ColorRGBA(1f, 1f, 1f, 0f);
    private float alpha;

    public FlashOverlay(AssetManager assets) {
        material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry = new Geometry("lightning-flash", new Quad(1f, 1f));
        geometry.setMaterial(material);
        geometry.setCullHint(CullHint.Always);
    }

    public Geometry geometry() {
        return geometry;
    }

    /** Covers the {@code width} × {@code height} view with white at {@code alpha} (0..1; 0 hides it). */
    public void update(float alpha, int width, int height) {
        this.alpha = alpha;
        geometry.setCullHint(alpha > 0f ? CullHint.Never : CullHint.Always);
        if (alpha <= 0f) {
            return;
        }
        geometry.setLocalScale(width, height, 1f);
        geometry.setLocalTranslation(0f, 0f, -0.5f);
        material.setColor("Color", colour.set(1f, 1f, 1f, Math.min(1f, alpha)));
    }

    /** The alpha of the last {@link #update} (tests). */
    public float alpha() {
        return alpha;
    }
}
