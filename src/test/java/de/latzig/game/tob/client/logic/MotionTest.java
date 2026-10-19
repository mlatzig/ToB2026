package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.LiftHeights;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.MoveKind;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class MotionTest {

    private static final float EPS = 1e-6f;

    @Test
    void aWalkAddsSixteenUnitsOfTheNextFramePerAlpha() {
        // zapper at oracle (1,0) = grid (5,11), facing north
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF")
                .object(1, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        sim.step(List.of(new Command.Forward(SpiderId.ZAPPER)));
        SimObject zap = sim.world().spider(SpiderId.ZAPPER);
        assertEquals(31, zap.moveCounter());

        Pose pose = Motion.position(zap, sim.world().isFrozen(), 0.5f);

        assertEquals(5f, pose.x(), EPS);
        assertEquals(0f, pose.y(), EPS);
        assertEquals((11 * 512 - 16 - 8) / 512f, pose.z(), EPS);
    }

    @Test
    void aPushFollowsThePushEaseTableAndStandsStillWhileFrozen() {
        // pusher (0,0) facing north, block (0,1), floor up to (0,2); the READY frame is frozen, so the push waits
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "F", "F", "F")
                .object(0, 0, 0, ObjectType.PSH).object(0, 1, 0, ObjectType.BLK).build(), SimOptions.DEFAULT);
        sim.step(List.of(new Command.Fire(SpiderId.PUSHER)));
        SimObject block = sim.world().object(3);
        assertEquals(MoveKind.PUSH, block.moveKind());
        assertEquals(16, block.moveCounter());
        assertFalse(sim.world().isFrozen());

        assertEquals((10 * 512 - 21) / 512f, Motion.position(block, false, 0.5f).z(), EPS);
        assertEquals(10f, Motion.position(block, true, 0.5f).z(), EPS);
    }

    @Test
    void aLiftRideFollowsTheLiftEaseTableAndCarriesThePlatform() {
        // zapper on the up lift at (0,0,0); its stop is above
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "U").level(1, "u")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        sim.step(List.of(new Command.Back(SpiderId.ZAPPER)));
        SimObject zap = sim.world().spider(SpiderId.ZAPPER);
        assertEquals(MoveKind.LIFT_UP, zap.moveKind());
        assertEquals(31, zap.moveCounter());

        float y = Motion.position(zap, sim.world().isFrozen(), 0.5f).y();

        assertEquals((3 + 3.5f) / 512f, y, EPS);
        Lift lift = sim.world().liftAt(new CellPos(4, 11, 0)).orElseThrow();
        assertEquals(y, LiftHeights.ratio(lift, sim.world(), 0.5f), EPS);
    }

    @Test
    void theForcerLiftRatioSumsTheLiftEaseOfTheFramesDone() {
        assertEquals(0f, LiftHeights.forcerRatio(true, 32, 0f), EPS);
        assertEquals(3f / 512f, LiftHeights.forcerRatio(true, 31, 0f), EPS);
        // frames 32..17 move 256 units (half a level); frame 16 moves 18
        assertEquals((256 + 9) / 512f, LiftHeights.forcerRatio(true, 16, 0.5f), EPS);
        assertEquals(1f - (256 + 9) / 512f, LiftHeights.forcerRatio(false, 16, 0.5f), EPS);
        assertEquals(1f, LiftHeights.forcerRatio(true, 1, 1f), EPS);
    }
}
