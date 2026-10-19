package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Enemy emitters in the frame pipeline (oracle §3.5). A zapper spider at (7,7) facing N ends READY with a fire into
 * the void; frame 0 is that READY-ending frame, so enemies first act in frame 1.
 */
class EnemyTest {

    private static final SpiderId Z = SpiderId.ZAPPER;

    private static SimRun started(TestTowers.Builder tower) {
        SimRun run = new SimRun(tower.object(7, 7, 0, ObjectType.ZAP).build());
        run.step();
        run.step(new Command.Fire(Z));
        assertEquals(0, run.sim.frame());
        return run;
    }

    private static List<String> firesOf(SimRun run, int slot) {
        List<String> fires = new ArrayList<>();
        for (SimEvent.RayFired fired : run.events(SimEvent.RayFired.class)) {
            if (fired.slot() == slot) {
                fires.add(fired.frame() + " " + fired.type() + " " + fired.path().get(0).direction());
            }
        }
        return fires;
    }

    @Test
    void aFixedZapperFiresInEveryFrameUnlessFrozen() {
        SimRun run = started(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.FZE));
        run.runUntilFrame(3);
        run.sim.world().freeze();
        run.runUntilFrame(5);
        run.sim.world().thaw();
        run.runUntilFrame(6);

        assertEquals(List.of("1 ZAP EAST", "2 ZAP EAST", "3 ZAP EAST", "6 ZAP EAST"), firesOf(run, 3));
    }

    @Test
    void aFixedPusherFiresInEveryFrameAndPushesWhenItsTargetStands() {
        SimRun run = started(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.FPE)
                .object(1, 0, 0, ObjectType.BLK));
        run.runUntilFrame(3);

        assertEquals(List.of("1 PUSH EAST", "2 PUSH EAST", "3 PUSH EAST"), firesOf(run, 3));
        assertEquals(List.of(new SimEvent.PushStarted(1, 4, TestTowers.pos(1, 0, 0), TestTowers.pos(2, 0, 0))),
                run.events(SimEvent.PushStarted.class));
        assertTrue(run.events.contains(new SimEvent.RayHit(2, 3, 4, RayOutcome.ABSORBED)), "moving: absorbed");
    }

    @Test
    void aRotatingZapperFiresOnlyInTheStillFramesOfItsCycle() {
        SimRun run = started(TestTowers.tower().level(0, "F").object(3, 3, 0, ObjectType.RZN));
        run.runUntilFrame(64);

        List<String> expected = new ArrayList<>();
        for (int frame = 1; frame <= 64; frame++) {
            int inQuarter = frame % 16;
            if (inQuarter <= 8) {
                expected.add(frame + " ZAP " + Direction.values()[(frame / 16) & 3]);
            }
        }
        assertEquals(expected, firesOf(run, 3));
        assertEquals(36, expected.size());
    }

    @Test
    void aRotatingPusherFiresOncePerSixteenFramesClockwise() {
        SimRun run = started(TestTowers.tower().level(0, "F").object(3, 3, 0, ObjectType.RPN));
        run.runUntilFrame(80);

        assertEquals(List.of("16 PUSH EAST", "32 PUSH SOUTH", "48 PUSH WEST", "64 PUSH NORTH", "80 PUSH EAST"),
                firesOf(run, 3));
    }
}
