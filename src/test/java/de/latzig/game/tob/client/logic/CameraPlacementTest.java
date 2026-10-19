package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.PoseGlide;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TestTowers;

class CameraPlacementTest {

    private static final float EPS = 1e-5f;
    private static final float PI = (float) Math.PI;

    /** A full 8 × 8 level like an original tower: floor area 4..11, centre (7.5, 7.5). */
    private static TowerDefinition fullTower() {
        String row = "FFFFFFFF";
        return TestTowers.tower().level(0, row, row, row, row, row, row, row, row).object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build();
    }

    /**
     * A narrow, off-centre level like {@code original-000} "Softly Softly!" (content box (4,4)-(8,11) in grid
     * cells, i.e. 5 cells wide instead of 8): the camera origin must still be the fixed grid centre (7.5, 7.5), not
     * the centre of this content box (which would be (6.0, 7.5)).
     */
    private static TowerDefinition narrowTower() {
        String row = "FFFFF";
        return TestTowers.tower().level(0, row, row, row, row, row, row, row, row).object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build();
    }

    private static void assertPose(float x, float y, float z, Pose actual, String message) {
        assertEquals(x, actual.x(), EPS, message + " x");
        assertEquals(y, actual.y(), EPS, message + " y");
        assertEquals(z, actual.z(), EPS, message + " z");
    }

    @Test
    void cameraNorthStandsSouthOfTheTowerAndLooksNorth() {
        Simulation sim = Simulation.start(fullTower(), SimOptions.DEFAULT);
        CameraPlacement placement = CameraPlacement.of(fullTower());
        assertEquals(new CameraPlacement(7.5f, 7.5f), placement);

        // north is -z: the camera NORTH stands at +z (south) and looks along -z (heading 0)
        assertPose(7.5f, 0.5f, 7.5f + 6.125f, placement.eye(sim.camera(CameraId.NORTH)), "NORTH");
        assertEquals(0f, CameraPlacement.heading(CameraId.NORTH), EPS);
        assertPose(7.5f - 6.125f, 0.5f, 7.5f, placement.eye(sim.camera(CameraId.EAST)), "EAST stands west");
        assertEquals(PI / 2, CameraPlacement.heading(CameraId.EAST), EPS);
        assertPose(7.5f, 0.5f, 7.5f - 6.125f, placement.eye(sim.camera(CameraId.SOUTH)), "SOUTH stands north");
        assertEquals(PI, CameraPlacement.heading(CameraId.SOUTH), EPS);
        assertPose(7.5f + 6.125f, 0.5f, 7.5f, placement.eye(sim.camera(CameraId.WEST)), "WEST stands east");
        assertEquals(3 * PI / 2, CameraPlacement.heading(CameraId.WEST), EPS);
    }

    @Test
    void cameraOriginIsTheFixedGridCentreEvenForANarrowOffCentreTower() {
        Simulation sim = Simulation.start(narrowTower(), SimOptions.DEFAULT);
        CameraPlacement placement = CameraPlacement.of(narrowTower());
        assertEquals(new CameraPlacement(7.5f, 7.5f), placement, "camera origin must be the fixed grid centre, "
                + "not the centre of the narrow content box (6.0, 7.5)");

        assertPose(7.5f, 0.5f, 7.5f + 6.125f, placement.eye(sim.camera(CameraId.NORTH)), "NORTH");
    }

    @Test
    void stepsMoveTheEyeUpToTheCamerasRightAndTowardsTheTower() {
        Simulation sim = Simulation.start(fullTower(), SimOptions.DEFAULT);
        CameraPlacement placement = CameraPlacement.of(fullTower());
        CameraId n = CameraId.NORTH;
        CameraId e = CameraId.EAST;

        sim.step(List.of(new Command.CameraForward(n), new Command.CameraTurnRight(n), new Command.CameraZoomIn(n),
                new Command.CameraTurnRight(e)));

        assertPose(7.75f, 0.75f, 7.5f + 5.625f, placement.eye(sim.camera(n)), "NORTH: right is east");
        assertPose(7.5f - 6.125f, 0.5f, 7.75f, placement.eye(sim.camera(e)), "EAST: right is south (+z)");
    }

    @Test
    void theGlideReachesANewPoseInTwoTenthsOfASecond() {
        PoseGlide glide = new PoseGlide();
        assertPose(1f, 2f, 3f, glide.update(new Pose(1f, 2f, 3f), 0.05f), "the first update snaps");

        assertPose(1.5f, 2f, 3f, glide.update(new Pose(2f, 2f, 3f), 0.1f), "half way");
        assertPose(2f, 2f, 3f, glide.update(new Pose(2f, 2f, 3f), 0.1f), "arrived");
        assertPose(2f, 2f, 3f, glide.update(new Pose(2f, 2f, 3f), 0.3f), "stays");

        glide.update(new Pose(3f, 2f, 3f), 0.1f);
        assertPose(2.5f, 2f, 3f, glide.current(), "half way to the next step");
        assertPose(3.25f, 2f, 3f, glide.update(new Pose(4f, 2f, 3f), 0.1f), "a new target glides on from there");

        glide.snap(new Pose(0f, 0f, 0f));
        assertPose(0f, 0f, 0f, glide.update(new Pose(0f, 0f, 0f), 0.01f), "snap");
    }
}
