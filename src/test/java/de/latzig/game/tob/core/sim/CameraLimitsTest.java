package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerExtent;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6a spec decision 2 and §4: the cameras' start distance and "In" limit follow a large tower's extent. */
class CameraLimitsTest {

    private static final float UNITS_PER_CELL = 512f;
    private static final float CENTRE = 7.5f;

    @Test
    void theOriginalLimitsAreTheOriginalsValues() {
        for (CameraId id : CameraId.values()) {
            assertEquals(CameraPose.START_DISTANCE, CameraLimits.ORIGINAL.startDistance(id), id.toString());
            assertEquals(CameraPose.NEAREST, CameraLimits.ORIGINAL.nearest(id), id.toString());
            assertEquals(CameraPose.OFFSET_LIMIT, CameraLimits.ORIGINAL.sidewaysLimit(id),
                    id + ": bit-exact within the 8 x 8 area (Task 9)");
        }
        assertEquals(1088, CameraLimits.START_MARGIN, "about 2 cells outside the tower edge");
        assertEquals(320, CameraLimits.NEAREST_MARGIN, "about 0.6 cell outside the tower edge");
        assertEquals(CameraLimits.ORIGINAL, CameraLimits.of(TowerGrid.empty()));
        assertEquals(CameraLimits.ORIGINAL, CameraLimits.of(TestTowers.tower().level(0, "F").build().grid()));
    }

    @Test
    void everyConvertedTowerKeepsTheOriginalLimits() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        for (TowerDefinition tower : TowerAssets.allTowers()) {
            assertEquals(CameraLimits.ORIGINAL, CameraLimits.of(tower.grid()), tower.id());
            Simulation sim = Simulation.start(tower, SimOptions.DEFAULT);
            for (CameraId id : CameraId.values()) {
                assertEquals(CameraPose.START_DISTANCE, sim.camera(id).distance(), tower.id() + " " + id);
                assertEquals(CameraPose.NEAREST, sim.camera(id).nearest(), tower.id() + " " + id);
            }
        }
    }

    @Test
    void theFullTowerMovesEveryCameraFourCellsFurtherOut() {
        CameraLimits limits = CameraLimits.of(LargeTestTowers.tower(LargeTestTowers.FULL).grid());

        assertEquals(new CameraLimits(4096, 4096, 4096, 4096), limits, "8 cells each way");
        for (CameraId id : CameraId.values()) {
            assertEquals(-5184, limits.startDistance(id), id + ": 10.125 cells");
            assertEquals(-4416, limits.nearest(id), id + ": 8.625 cells");
            assertEquals(0x1800, limits.sidewaysLimit(id), id + ": 0x1000 + 0x800, Task 9");
        }
    }

    @Test
    void theNarrowTowerSidewaysLimitFollowsTheLongAxis() {
        CameraLimits limits = CameraLimits.of(LargeTestTowers.tower(LargeTestTowers.NARROW).grid());

        // columns 0..15 (16 wide): NORTH/SOUTH move sideways along X, reach 4096 -> 4096 + 0x800 = 0x1800
        assertEquals(0x1800, limits.sidewaysLimit(CameraId.NORTH), "sideways along the long (16 wide) axis");
        assertEquals(0x1800, limits.sidewaysLimit(CameraId.SOUTH), "sideways along the long (16 wide) axis");
        // rows 6..9, within 4..11: EAST/WEST move sideways along Y, reach stays the original 2048 -> 0x1000
        assertEquals(CameraPose.OFFSET_LIMIT, limits.sidewaysLimit(CameraId.EAST),
                "sideways along the short (4 wide, within 8 x 8) axis");
        assertEquals(CameraPose.OFFSET_LIMIT, limits.sidewaysLimit(CameraId.WEST),
                "sideways along the short (4 wide, within 8 x 8) axis");
    }

    @Test
    void aSidewaysMoveStopsAtTheNewLimit() {
        Simulation sim = Simulation.start(LargeTestTowers.tower(LargeTestTowers.FULL), SimOptions.DEFAULT);
        CameraPose pose = sim.camera(CameraId.NORTH);

        assertEquals(0x1800, pose.offsetLimit(), "full tower: 0x1000 + 0x800");
        int steps = 0;
        while (pose.shiftRight()) {
            steps++;
        }
        assertEquals(0x1800 / CameraPose.OFFSET_STEP, steps, "steps from 0 to the new limit");
        assertEquals(0x1800, pose.offset(), "stops at the new limit, beyond the originals' 0x1000");
    }

    @Test
    void theNarrowTowerMovesOnlyTheCamerasAlongItsLongSide() {
        CameraLimits limits = CameraLimits.of(LargeTestTowers.tower(LargeTestTowers.NARROW).grid());

        assertEquals(CameraPose.START_DISTANCE, limits.startDistance(CameraId.NORTH), "rows 6..9: inside 4..11");
        assertEquals(CameraPose.START_DISTANCE, limits.startDistance(CameraId.SOUTH));
        assertEquals(-5184, limits.startDistance(CameraId.EAST), "columns 0..15");
        assertEquals(-5184, limits.startDistance(CameraId.WEST));
    }

    @Test
    void aTowerReachingBeyondOneEdgeMovesOnlyThatCamera() {
        TowerDefinition tower = TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP).build();
        TowerGrid grid = tower.grid().toBuilder().set(13, 11, 0, new Cell(BaseType.FLOOR, null)).build();
        CameraLimits limits = CameraLimits.of(grid);

        assertEquals(new CameraLimits(2048, 2048, 2048, 3072), limits, "column 13: 6 cells east of the centre");
        assertEquals(-3072 - 1088, limits.startDistance(CameraId.WEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {LargeTestTowers.FULL, LargeTestTowers.NARROW, LargeTestTowers.CROWDED,
        LargeTestTowers.SOLVABLE})
    void theEyeStaysOutsideTheTowerAtTheStartAndAtTheInLimit(String id) {
        TowerDefinition tower = LargeTestTowers.tower(id);
        TowerExtent extent = TowerExtent.of(tower.grid()).orElseThrow();
        CameraLimits limits = CameraLimits.of(tower.grid());
        Simulation sim = Simulation.start(tower, SimOptions.DEFAULT);

        for (CameraId camera : CameraId.values()) {
            CameraPose pose = sim.camera(camera);
            boolean beyond = limits.reach(camera) > CameraLimits.ORIGINAL_REACH;
            float start = outside(pose, extent);
            int steps = 0;
            while (pose.zoomIn()) {
                steps++;
            }
            float in = outside(pose, extent);

            assertTrue(start >= 2.125f, id + " " + camera + " start: " + start);
            assertEquals(3, steps, id + " " + camera + ": three In steps, as for the originals");
            assertEquals(pose.nearest(), pose.distance());
            assertTrue(in >= 0.625f, id + " " + camera + " In: " + in);
            if (beyond) {
                assertEquals(2.125f, start, 1e-6f, id + " " + camera + ": the originals' start margin");
                assertEquals(0.625f, in, 1e-6f, id + " " + camera + ": the originals' In margin");
            }
        }
    }

    @Test
    void theOutLimitStaysForLargeTowers() {
        Simulation sim = Simulation.start(LargeTestTowers.tower(LargeTestTowers.FULL), SimOptions.DEFAULT);
        CameraPose pose = sim.camera(CameraId.NORTH);
        while (pose.zoomOut()) {
            assertTrue(pose.distance() >= CameraPose.FARTHEST);
        }

        assertTrue(pose.distance() - CameraPose.DISTANCE_STEP < CameraPose.FARTHEST);
    }

    /** How far (cells) the eye stands outside the tower edge on its side, along the camera's heading. */
    private static float outside(CameraPose pose, TowerExtent extent) {
        float eyeX = CENTRE + pose.east() / UNITS_PER_CELL;
        float eyeZ = CENTRE - pose.north() / UNITS_PER_CELL;
        return switch (pose.id()) {
            case NORTH -> eyeZ - (extent.maxY() + 0.5f);
            case EAST -> (extent.minX() - 0.5f) - eyeX;
            case SOUTH -> (extent.minY() - 0.5f) - eyeZ;
            case WEST -> eyeX - (extent.maxX() + 0.5f);
        };
    }
}
