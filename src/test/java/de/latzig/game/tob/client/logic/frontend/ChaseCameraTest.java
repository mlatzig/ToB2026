package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TestTowers;

/** Phase 5c-2 decision 8: the original's chase camera (report 17 §5) in ToB 2026's units. */
class ChaseCameraTest {

    private static void assertPose(Pose expected, Pose actual, String message) {
        assertEquals(expected.x(), actual.x(), 1e-5f, message + " x");
        assertEquals(expected.y(), actual.y(), 1e-5f, message + " y");
        assertEquals(expected.z(), actual.z(), 1e-5f, message + " z");
    }

    @Test
    void theEyeSitsBehindAndAboveTheSpiderPulledTowardsTheCentre() {
        ChaseCamera chase = new ChaseCamera();

        assertEquals(288, chase.dolly(), "W001698 = $120");
        assertPose(new Pose(7.5f - 3.5f * 205f / 256f, 0.5625f, 15.5f), chase.eye(new Pose(4f, 0f, 11f), 0f),
                "a level-0 spider at the west edge: 288/512 above, 8 * 288/512 = 4.5 south");
        assertPose(new Pose(7.5f, 1.25f + 0.5625f, 13.5f), chase.eye(new Pose(7.5f, 1f, 9f), 0.7f),
                "on the centre line no pull; one level up the eye rises 320/256");
        float pull = (4f - 7.5f) - (chase.eye(new Pose(4f, 0f, 11f), 0f).x() - 7.5f);
        assertEquals(-0.2f * 3.5f, pull, 0.005f, "x pulled 20 % towards the centre");
        assertPose(new Pose(0f, 0f, -1f), ChaseCamera.DIRECTION, "the fixed view north, level");
    }

    @Test
    void theDollyFollowsBitsSixAndSevenOfTheLastScriptByte() {
        ChaseCamera chase = new ChaseCamera();

        for (int i = 0; i < 32; i++) {
            chase.step((byte) 0x41);
        }
        assertEquals(288 + 64, chase.dolly(), "F+: +2 per frame over a 32-frame walk");
        assertEquals((288 + 64 + 1) / 512f, chase.eye(new Pose(7.5f, 0f, 9f), 0.5f).y(), 1e-6f,
                "between frames: half of the next +2");
        chase.step((byte) 0x46);
        chase.step((byte) 0x05);
        assertEquals(288 + 66, chase.dolly(), "W+ moves, X (bits 00) does not");
        for (int i = 0; i < 200; i++) {
            chase.step((byte) 0xC1);
        }
        assertEquals(288 + 66 - 400, chase.dolly(), "F-: -2 per frame, never clamped");
        chase.step((byte) 0);
        assertEquals(288 + 66 - 400, chase.dolly(), "the script's end stops the dolly");
        assertEquals(chase.eye(new Pose(7.5f, 0f, 9f), 0f), chase.eye(new Pose(7.5f, 0f, 9f), 0.9f));
    }

    /** The dolly driven by a running script: +2 per frame while F+ is the last byte, still after the end. */
    @Test
    void aScriptMovesTheDollyUntilItsEnd() {
        TowerDefinition tower = TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).build();
        Simulation sim = Simulation.start(tower, new SimOptions(true));
        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "04 41 00"));
        ChaseCamera chase = new ChaseCamera();
        int frames = 0;
        for (; frames < 120 && !player.isFinished(); frames++) {
            Optional<Command> command = player.next(sim);
            player.afterStep(sim.step(command.map(List::of).orElse(List.of())));
            chase.step(player.lastByte());
        }
        int atEnd = chase.dolly();
        for (int i = 0; i < 20; i++) {
            player.afterStep(sim.step(player.next(sim).map(List::of).orElse(List.of())));
            chase.step(player.lastByte());
        }

        assertTrue(player.isFinished(), "the script ended within " + frames + " frames");
        assertEquals(288 + 2 * 32, atEnd, "the walk's 32 frames with F+ (the turn's 04 does not move it)");
        assertEquals(atEnd, chase.dolly(), "no movement after the end");
    }

    @Test
    void theFrustumPutsTheAxisInTheBandAboveTheResultWindow() {
        float[] frustum = ChaseCamera.frustum(60f, 16f / 9f, 0.1f);
        float half = 0.1f * (float) Math.tan(Math.toRadians(30.0));

        assertEquals(-half * 16f / 9f, frustum[0], 1e-6f, "left");
        assertEquals(half * 16f / 9f, frustum[1], 1e-6f, "right");
        assertEquals(2f * half, frustum[2] - frustum[3], 1e-6f, "the same vertical field of view");
        assertEquals(StartScreens.INTERLUDE_AXIS, frustum[2] / (frustum[2] - frustum[3]), 1e-6f,
                "the axis at INTERLUDE_AXIS from the top");
    }
}
