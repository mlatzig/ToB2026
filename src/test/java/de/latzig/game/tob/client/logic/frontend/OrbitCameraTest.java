package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class OrbitCameraTest {

    private static final double ELEVATION_RADIANS = Math.toRadians(OrbitCamera.ELEVATION_DEGREES);

    @Test
    void theOrbitCirclesTheMidpointOfTheBaseElementsAboveTheTopLevel() {
        // TestTowers: oracle (x, z) -> grid (x + 4, 11 - z); "FFF" on levels 0 and 2 covers grid x 4..6, y 11
        TowerDefinition tower = TestTowers.tower().level(0, "FFF").level(2, "F").build();

        OrbitCamera orbit = OrbitCamera.of(tower);

        assertEquals(new Pose(4.5f, 2f + 10f / 32f, 11.5f), orbit.target(), "integer middle 5, corner -> field");
        Pose start = orbit.eye(0.0);
        assertEquals(4.5f, start.x(), 1e-5f);
        assertEquals(2f + 10f / 32f + OrbitCamera.DISTANCE * (float) Math.tan(ELEVATION_RADIANS), start.y(), 1e-5f,
                "the eye rises with the target, Task 17");
        assertEquals(11.5f - 230f / 32f, start.z(), 1e-5f);
        Pose quarter = orbit.eye(Math.PI / 2.0 / OrbitCamera.SPEED);
        assertEquals(4.5f - 230f / 32f, quarter.x(), 1e-4f, "0.1 rad/s, x = D sin(-t)");
        assertEquals(11.5f, quarter.z(), 1e-4f);
    }

    @Test
    void anEmptyTowerOrbitsTheGridCentre() {
        OrbitCamera orbit = OrbitCamera.of(TestTowers.tower().build());

        assertEquals(new Pose(7.5f, 10f / 32f, 7.5f), orbit.target());
    }

    @Test
    void theEyeStaysAtAFixedElevationAngleAboveTheTargetForEveryTowerHeight() {
        OrbitCamera empty = OrbitCamera.of(TestTowers.tower().build());
        OrbitCamera oneLevel = OrbitCamera.of(TestTowers.tower().level(0, "F").build());
        OrbitCamera twoLevels = OrbitCamera.of(TestTowers.tower().level(0, "F").level(1, "F").build());

        assertElevationHoldsAt25Degrees(empty, "empty tower");
        assertElevationHoldsAt25Degrees(oneLevel, "one-level tower");
        assertElevationHoldsAt25Degrees(twoLevels, "two-level tower");

        assertEquals(oneLevel.target().y() - empty.target().y(), oneLevel.eye(0.0).y() - empty.eye(0.0).y(), 1e-4f,
                "the eye rises exactly as much as the target between an empty and a one-level tower");
        assertEquals(twoLevels.target().y() - oneLevel.target().y(), twoLevels.eye(0.0).y() - oneLevel.eye(0.0).y(),
                1e-4f, "the eye rises exactly as much as the target between a one-level and a two-level tower");
    }

    private static void assertElevationHoldsAt25Degrees(OrbitCamera orbit, String label) {
        Pose eye = orbit.eye(0.0);
        Pose target = orbit.target();
        double dx = eye.x() - target.x();
        double dz = eye.z() - target.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        assertEquals(OrbitCamera.DISTANCE, horizontal, 1e-4, label + ": the eye circles at a fixed distance");
        double pitch = Math.atan2(eye.y() - target.y(), horizontal);
        assertEquals(ELEVATION_RADIANS, pitch, 1e-4, label + ": the pitch toward the target is 25 degrees");
    }
}
