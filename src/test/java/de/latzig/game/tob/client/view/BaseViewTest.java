package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.FloatBuffer;

import org.junit.jupiter.api.Test;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.StaticAtlas;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class BaseViewTest {

    @Test
    void everyQuadrantHasItsOwnGeometryAndTheWalkableTopsCarryDetailCoordinates() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        WorldState world = Simulation.start(TestTowers.tower().level(0, "UF").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT).world();

        BaseView view = new BaseView(assets, world, TowerColours.of(world.tower()));

        Node level = (Node) view.node().getChild("level-0");
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            Geometry geometry = (Geometry) level.getChild("level-0-" + quadrant.baseName());
            assertNotNull(geometry, quadrant.name());
            assertSame(assets.staticMaterial(quadrant), geometry.getMaterial(), quadrant.name());
            FloatBuffer uv = geometry.getMesh().getFloatBuffer(Type.TexCoord);
            for (int i = 0; i < uv.limit(); i++) {
                assertTrue(uv.get(i) >= 0f && uv.get(i) <= 1f, "quadrant-local UV " + uv.get(i));
            }
        }
        assertEquals(2f, StaticAtlas.DETAIL_REPEAT, 0f, "ToB 2005's 4 repeats per atlas, 2 per face");
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            // Phase 4b-4 decision 3: the sides and bottoms carry the detail coordinates too
            Geometry geometry = (Geometry) level.getChild("level-0-" + quadrant.baseName());
            FloatBuffer uv = geometry.getMesh().getFloatBuffer(Type.TexCoord);
            FloatBuffer detail = geometry.getMesh().getFloatBuffer(Type.TexCoord2);
            assertEquals(uv.limit(), detail.limit(), quadrant.name());
            for (int i = 0; i < uv.limit(); i++) {
                assertEquals(StaticAtlas.DETAIL_REPEAT * uv.get(i), detail.get(i), 1e-6f);
            }
        }
        Node lift = (Node) view.node().getChild("lift-" + world.lifts().get(0).lower());
        assertSame(assets.staticMaterial(StaticQuadrant.TOP),
                ((Geometry) lift.getChild(lift.getName() + "-static-top")).getMaterial(),
                "the lift platform is a walkable top too");
    }
}
