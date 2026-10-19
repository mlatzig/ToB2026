package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import com.jme3.bounding.BoundingBox;
import com.jme3.light.DirectionalLight;
import com.jme3.scene.Geometry;

import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.core.sim.LargeTestTowers;

/** Phase 6a spec decision 4: the ground haze covers the full grid; the shadow range is set per tower. */
class LargeTowerViewsTest {

    @Test
    void theGroundHazeCoversTheFull16x16GridAndItsSocket() throws Exception {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        Geometry haze = new HazeView(new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root()))
                .geometry();
        haze.updateModelBound();
        BoundingBox bound = (BoundingBox) haze.getModelBound();
        float half = 8f + Socket.BORDER;

        assertEquals(Lightning.CENTRE, haze.getLocalTranslation().x, 0f, "centred on the grid centre");
        assertTrue(bound.getXExtent() > half && bound.getZExtent() > half, "half extents " + bound.getXExtent()
                + " x " + bound.getZExtent());
        assertEquals(0f, bound.getCenter().x, 1e-3f);
        assertEquals(0f, bound.getCenter().z, 1e-3f);
    }

    @Test
    void everyTowerStartSetsItsShadowRange() {
        ShadowSetup shadows = new ShadowSetup(GeneratedAssets.assetManager());
        DirectionalLight sun = new DirectionalLight();

        shadows.setSun(sun, ShadowRange.distance(LargeTestTowers.tower(LargeTestTowers.FULL)));
        assertEquals(ShadowRange.distance(LargeTestTowers.tower(LargeTestTowers.FULL)),
                shadows.renderer().getShadowZExtend(), 0f);
        assertTrue(shadows.renderer().getShadowZExtend() > ShadowSetup.DISTANCE);

        shadows.setSun(sun);
        assertEquals(ShadowSetup.DISTANCE, shadows.renderer().getShadowZExtend(), 0f, "an original tower: 48");
        assertEquals(sun, shadows.renderer().getLight());
    }
}
