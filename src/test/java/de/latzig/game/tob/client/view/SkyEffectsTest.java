package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.texture.Texture;

import de.latzig.game.tob.client.logic.effects.CloudDrift;
import de.latzig.game.tob.client.logic.effects.SkyTuning;

class SkyEffectsTest {

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    @Test
    void theCloudsDriftWithTheSimulationTimeAndBlendOverTheSky() {
        CloudView clouds = new CloudView(assets(), 42L, SkyTuning.CLOUD_COVER);
        CloudDrift drift = CloudDrift.of(42L, SkyTuning.CLOUD_COVER);

        clouds.update(250f);
        assertEquals(drift.offsetU(250f), clouds.offset()[0], 0f);
        assertEquals(drift.offsetV(250f), clouds.offset()[1], 0f);
        clouds.update(250f);
        assertEquals(drift.offsetU(250f), clouds.offset()[0], 0f, "paused: the clouds stand still");
        Material material = clouds.geometry().getMaterial();
        assertEquals(BlendMode.Alpha, material.getAdditionalRenderState().getBlendMode());
        assertFalse(material.getAdditionalRenderState().isDepthWrite());
        Texture texture = material.getTextureParam("ColorMap").getTextureValue();
        assertEquals(Texture.WrapMode.Repeat, texture.getWrap(Texture.WrapAxis.S), "the texture tiles");
        assertEquals(256, texture.getImage().getWidth());
        assertEquals(Bucket.Sky, clouds.geometry().getQueueBucket());
    }
}
