package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.effects.StaticAtlas;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

class StaticAtlasTest {

    private static final float EPS = 1e-6f;

    private static void assertLocal(MeshArrays arrays) {
        for (float uv : arrays.uvs()) {
            assertTrue(uv >= -EPS && uv <= 1f + EPS, "quadrant-local UV " + uv);
        }
    }

    @Test
    void theQuadrantOfAnAtlasUvAndItsLocalCoordinates() {
        assertEquals(StaticQuadrant.TOP, StaticQuadrant.at(0.2f, 0.3f));
        assertEquals(StaticQuadrant.BOTTOM, StaticQuadrant.at(0.7f, 0.3f));
        assertEquals(StaticQuadrant.SIDE_A, StaticQuadrant.at(0.2f, 0.8f));
        assertEquals(StaticQuadrant.SIDE_B, StaticQuadrant.at(0.7f, 0.8f));
        assertEquals(0.5f, StaticQuadrant.SIDE_B.localU(0.75f), EPS);
        assertEquals(1f, StaticQuadrant.SIDE_B.localV(1f), EPS);
        assertEquals(1f, StaticQuadrant.TOP.localU(0.5f), EPS);
        assertEquals("static-side-a", StaticQuadrant.SIDE_A.baseName());
    }

    @Test
    void aLoneFloorSplitsIntoItsTopItsBottomAndTwoSideQuadrants() {
        // the lone floor at grid (4,11,0): rotate1 = 3, rotate2 = 0 (BaseGeometryTest); cell() emits top, bottom,
        // north, east, south, west; north/east edges lie in the lower-left quadrant, south/west in the lower right
        WorldState world = Simulation.start(TestTowers.tower().level(0, "F").build(), SimOptions.DEFAULT).world();
        MeshArrays atlas = BaseGeometry.level(world, 0, TowerColours.of(world.tower()));

        Map<StaticQuadrant, MeshArrays> split = StaticAtlas.split(atlas);

        assertEquals(2, split.get(StaticQuadrant.TOP).triangles());
        assertEquals(2, split.get(StaticQuadrant.BOTTOM).triangles());
        assertEquals(4, split.get(StaticQuadrant.SIDE_A).triangles());
        assertEquals(4, split.get(StaticQuadrant.SIDE_B).triangles());
        assertArrayEquals(new float[] {1f, 1f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f},
                split.get(StaticQuadrant.TOP).uvs(), EPS, "the rotation-3 top, atlas UV x 2");
        assertArrayEquals(new float[] {1f, 0.1875f, 0f, 0.1875f, 0f, 0.25f, 1f, 0.1875f, 0f, 0.25f, 1f, 0.25f},
                Arrays.copyOfRange(split.get(StaticQuadrant.SIDE_A).uvs(), 0, 12), EPS,
                "the north edge's strip: atlas (0.5..0, 0.59375..0.625) in the lower-left quadrant");
        assertArrayEquals(Arrays.copyOfRange(atlas.positions(), 0, 18), split.get(StaticQuadrant.TOP).positions(),
                EPS, "positions are unchanged");
        assertArrayEquals(Arrays.copyOfRange(atlas.colors(), 0, 24), split.get(StaticQuadrant.TOP).colors(), EPS);
        split.values().forEach(StaticAtlasTest::assertLocal);
    }

    @Test
    void aLiftShaftUsesTheBottomQuadrantAndItsPlatformTheWalkableTop() {
        Lift lift = Simulation.start(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT).world().lifts().get(0);

        Map<StaticQuadrant, MeshArrays> split = StaticAtlas.split(BaseGeometry.lift(lift, 0.5f, false, 0x00FF00,
                0xFF0000));

        assertEquals(2, split.get(StaticQuadrant.TOP).triangles(), "the platform top");
        assertEquals(10, split.get(StaticQuadrant.BOTTOM).triangles(), "the bottom and the four shaft sides");
        assertEquals(4, split.get(StaticQuadrant.SIDE_A).triangles(), "the north and east rims");
        assertEquals(4, split.get(StaticQuadrant.SIDE_B).triangles(), "the south and west rims");
        // the north shaft: atlas (0.5..1, 0..0.25) is the upper-right quadrant's (0..1, 0..0.5)
        assertArrayEquals(new float[] {0f, 0.5f, 1f, 0.5f, 1f, 0f, 0f, 0.5f, 1f, 0f, 0f, 0f},
                Arrays.copyOfRange(split.get(StaticQuadrant.BOTTOM).uvs(), 12, 24), EPS);
        split.values().forEach(StaticAtlasTest::assertLocal);
    }
}
