package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.core.sim.Direction;

class YawSmootherTest {

    private static final float EPS = 1e-4f;
    private static final float PI = (float) Math.PI;

    @Test
    void aQuarterTurnTakesTwoTenthsOfASecond() {
        YawSmoother yaw = new YawSmoother();
        yaw.snap(Motion.heading(Direction.NORTH));

        assertEquals(PI / 4, yaw.update(Motion.heading(Direction.EAST), 0.1f), EPS);
        assertEquals(PI / 2, yaw.update(Motion.heading(Direction.EAST), 0.1f), EPS);
        assertEquals(PI / 2, yaw.update(Motion.heading(Direction.EAST), 0.5f), EPS);
        assertEquals(PI / 2 + PI / 4, yaw.update(Motion.heading(Direction.WEST), 0.1f), EPS,
                "a half turn goes one way, at the same speed");
    }

    @Test
    void theCameraTurnsTheShortWayAcrossNorth() {
        YawSmoother yaw = new YawSmoother();
        yaw.snap(Motion.heading(Direction.WEST));

        assertEquals(3 * PI / 2 + PI / 4, yaw.update(Motion.heading(Direction.NORTH), 0.1f), EPS);
        assertEquals(0f, yaw.update(Motion.heading(Direction.NORTH), 0.1f), EPS);
        yaw.snap(-PI / 2);
        assertEquals(3 * PI / 2, yaw.current(), EPS);
    }
}
