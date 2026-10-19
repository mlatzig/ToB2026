package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

class CameraPoseTest {

    @Test
    void everyTowerStartResetsTheFourCamerasToTheLoadLoopPositions() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build(), SimOptions.DEFAULT);
        // S:17708-17720: ($6, $A) = (0, -3136), (-3136, 0), (0, 3136), (3136, 0) for N, E, S, W
        int[][] expected = {{0, -3136}, {-3136, 0}, {0, 3136}, {3136, 0}};
        for (CameraId id : CameraId.values()) {
            CameraPose pose = sim.camera(id);
            assertEquals(0x100, pose.height(), id.toString());
            assertEquals(0, pose.offset(), id.toString());
            assertEquals(-3136, pose.distance(), id.toString());
            assertEquals(expected[id.ordinal()][0], pose.east(), id + " east");
            assertEquals(expected[id.ordinal()][1], pose.north(), id + " north");
        }
    }

    @Test
    void theHeightStepsByAQuarterCellBetween0x80And0xF80() {
        CameraPose pose = new CameraPose(CameraId.NORTH);
        int raises = 0;
        while (pose.raise()) {
            raises++;
        }
        assertEquals(29, raises);
        assertEquals(0xF80, pose.height(), "0x1000 is out of range");
        int lowers = 0;
        while (pose.lower()) {
            lowers++;
        }
        assertEquals(30, lowers);
        assertEquals(0x80, pose.height());
        assertFalse(pose.lower(), "ignored, not clamped");
        assertEquals(0x80, pose.height());
    }

    @Test
    void theOffsetStepsByAQuarterCellUpToEightCellsEachSide() {
        CameraPose pose = new CameraPose(CameraId.EAST);
        for (int i = 0; i < 32; i++) {
            assertTrue(pose.shiftRight(), "step " + i);
        }
        assertEquals(0x1000, pose.offset());
        assertFalse(pose.shiftRight());
        for (int i = 0; i < 64; i++) {
            assertTrue(pose.shiftLeft(), "step " + i);
        }
        assertEquals(-0x1000, pose.offset());
        assertFalse(pose.shiftLeft());
        assertEquals(-0x1000, pose.offset());
    }

    @Test
    void theDistanceStepsByHalfACellBetweenTheInAndOutLimits() {
        CameraPose pose = new CameraPose(CameraId.SOUTH);
        for (int i = 0; i < 3; i++) {
            assertTrue(pose.zoomIn(), "step " + i);
        }
        assertEquals(-2368, pose.distance(), "deviation 5: three In steps from the start");
        assertFalse(pose.zoomIn());
        assertEquals(-2368, pose.distance());
        int outs = 0;
        while (pose.zoomOut()) {
            outs++;
        }
        assertEquals(49, outs);
        assertEquals(-14912, pose.distance());
    }

    @Test
    void aPositiveOffsetIsTheCamerasRightAndTheDistanceIsBehindTheCentre() {
        // L00A574: (offset, distance) turned a quarter per heading step; $6 = east, $A = north
        int[][] expected = {{128, -3136}, {-3136, -128}, {-128, 3136}, {3136, 128}};
        for (CameraId id : CameraId.values()) {
            CameraPose pose = new CameraPose(id);
            pose.shiftRight();
            assertEquals(expected[id.ordinal()][0], pose.east(), id + " east");
            assertEquals(expected[id.ordinal()][1], pose.north(), id + " north");
        }
    }

    @Test
    void theInLimitKeepsTheEyeMoreThanHalfACellInFrontOfTheOriginalTowerEdge() {
        int towerEdge = -4 * 512;
        assertTrue(CameraPose.NEAREST < towerEdge - 256, "deviation 5: the camera must not enter the tower");
        assertEquals(CameraPose.START_DISTANCE + 3 * CameraPose.DISTANCE_STEP, CameraPose.NEAREST);
    }
}
