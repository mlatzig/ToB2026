package de.latzig.game.tob.client.view;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.effects.CloudDrift;
import de.latzig.game.tob.client.logic.effects.CloudPlane;
import de.latzig.game.tob.client.logic.effects.CloudTexture;

/**
 * ToB 2005's drifting clouds (Phase 4b-3 spec §2 decisions 3 and 4): the {@link CloudPlane} with the tower's
 * {@link CloudTexture}, unshaded, alpha-blended (texture alpha × vertex alpha), both sides, no depth write, in the sky
 * bucket after the dome, the stars and the moon, so it covers them while the hills cover it; fogged towards the
 * horizon colour like ToB 2005's clouds ({@code sky.cpp:831}, Phase 4b-4), the alpha unchanged. The texture
 * coordinates move by the {@link CloudDrift} of the simulation time; the arrays are reused, nothing is allocated per
 * frame.
 */
final class CloudView {

    private final Geometry geometry;
    private final CloudDrift drift;
    private final float[] base;
    private final float[] uvs;
    private float shownU = Float.NaN;
    private float shownV = Float.NaN;

    CloudView(AssetLibrary assets, long seed, int cover) {
        this(assets, seed, cover, CloudTexture.generate(seed, cover));
    }

    /** @param texture the cloud texture's RGBA bytes ({@link CloudTexture#generate}), e.g. prepared for the Briefing */
    CloudView(AssetLibrary assets, long seed, int cover, byte[] texture) {
        MeshArrays arrays = CloudPlane.arrays();
        Mesh mesh = MeshBuilder.triangles(arrays);
        mesh.setStreamed();
        base = arrays.uvs().clone();
        uvs = new float[base.length];
        drift = CloudDrift.of(seed, cover);
        Material material = assets.unlitFogMaterial();
        material.setTexture("ColorMap", GeneratedTextures.of("clouds", CloudTexture.SIZE, CloudTexture.SIZE,
                texture, true, true, true));
        material.setBoolean("VertexColor", true);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        geometry = new Geometry("clouds", mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Sky);
        geometry.setCullHint(CullHint.Never);
        update(0f);
    }

    Geometry geometry() {
        return geometry;
    }

    /** Moves the texture to the drift of simulation time {@code now}. */
    void update(float now) {
        float u = drift.offsetU(now);
        float v = drift.offsetV(now);
        if (u == shownU && v == shownV) {
            return;
        }
        shownU = u;
        shownV = v;
        for (int i = 0; i < base.length; i += 2) {
            uvs[i] = base[i] + u;
            uvs[i + 1] = base[i + 1] + v;
        }
        SpriteBatch.upload(geometry.getMesh(), Type.TexCoord, uvs);
    }

    /** The texture offset shown, u then v (tests). */
    float[] offset() {
        return new float[] {shownU, shownV};
    }
}
