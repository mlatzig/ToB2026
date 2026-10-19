package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Oracle §10 scenarios of Phase 2d (T-08 to T-14, T-27) and the own tests of the Phase 2d spec §5, in the full frame
 * pipeline. Frame 0 is the READY-ending frame; frame k is the k-th normal frame. "Start time" ({@link SimRun#startTime})
 * is a zapper at (7,7) that fires into the void at frame 0.
 */
class SpecialScenarioTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;
    private static final SpiderId G = SpiderId.GRABBER;

    private static String moveState(SimObject object) {
        return object.moveCounter() + "/" + object.worldX() + "/" + object.worldZ();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void t08ASpiderWalkingAwayTriggersTheMineInItsFirstMovingFrame(boolean minesDestroyFloor) {
        // z=1 ".F", z=0 "FFF": pusher (1,0) turned W, mine (1,1) slot 3; grabber (7,7) keeps the attempt going
        SimRun run = new SimRun(TestTowers.tower().level(0, ".F", "FFF").object(1, 0, 0, ObjectType.PSH)
                .object(1, 1, 0, ObjectType.PRM).object(7, 7, 0, ObjectType.GRB).minesDestroyFloor(minesDestroyFloor)
                .build());
        run.turnInReady(new Command.TurnLeft(P));
        List<SimEvent> frame0 = run.step(new Command.Forward(P));
        assertTrue(frame0.contains(new SimEvent.MoveStarted(0, 1, pos(1, 0, 0), pos(0, 0, 0))));
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty(), "frame 0 is frozen");

        List<SimEvent> frame1 = run.step();

        int exploded = frame1.indexOf(new SimEvent.Exploded(1, 3,
                List.of(pos(0, 1, 0), pos(1, 0, 0), pos(2, 1, 0), pos(1, 2, 0))));
        assertEquals(List.of(new SimEvent.Destroyed(1, 1, pos(1, 0, 0)), new SimEvent.Destroyed(1, 3, pos(1, 1, 0))),
                frame1.subList(exploded + 1, exploded + 3));
        run.runUntilFrame(32);
        assertTrue(run.sim.world().hasFloor(pos(1, 1, 0)));
        run.runUntilFrame(33);
        assertTrue(run.events.containsAll(List.of(new SimEvent.Removed(33, 1, pos(1, 0, 0)),
                new SimEvent.Removed(33, 3, pos(1, 1, 0)))));
        assertEquals(!minesDestroyFloor, run.sim.world().hasFloor(pos(1, 1, 0)));
        assertEquals(0, run.sim.world().counters().robotsDestroyed());
    }

    @Test
    void t09ASpiderWalkingIntoANeighbourCellTriggersTheMineAtItsClaimFrame() {
        // z=1 "..F", z=0 "FFF": zapper (0,0) turned E, mine (2,1) slot 3; grabber (7,7)
        SimRun run = new SimRun(TestTowers.tower().level(0, "..F", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 1, 0, ObjectType.PRM).object(7, 7, 0, ObjectType.GRB).build());
        run.turnInReady(new Command.TurnRight(Z));
        run.step(new Command.Forward(Z));
        run.runUntilFrame(31);
        assertTrue(run.events.containsAll(List.of(new SimEvent.MoveClaimed(9, 0, pos(1, 0, 0)),
                new SimEvent.MoveFinished(31, 0, pos(1, 0, 0)))));
        run.step(new Command.Forward(Z));
        run.runUntilFrame(40);
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        List<SimEvent> frame41 = run.step();

        assertTrue(frame41.contains(new SimEvent.MoveClaimed(41, 0, pos(2, 0, 0))));
        int exploded = frame41.indexOf(new SimEvent.Exploded(41, 3,
                List.of(pos(1, 1, 0), pos(2, 0, 0), pos(3, 1, 0), pos(2, 2, 0))));
        assertEquals(List.of(new SimEvent.Destroyed(41, 0, pos(1, 0, 0)), new SimEvent.Destroyed(41, 3, pos(2, 1, 0))),
                frame41.subList(exploded + 1, exploded + 3));
        assertTrue(run.sim.world().occupancy().isFree(pos(2, 0, 0)), "the claim is released");
    }

    @Test
    void t10ALiftRiderNextToAMineDoesNotTriggerIt() {
        // level 0 "UF": zapper on the up-lift (0,0), mine (1,0) slot 3; level 1 "u"
        SimRun run = new SimRun(TestTowers.tower().level(0, "UF").level(1, "u").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.PRM).build());
        run.step(new Command.Back(Z));
        run.runUntilFrame(40);

        assertTrue(run.events.containsAll(List.of(new SimEvent.LiftRideStarted(0, 0, pos(0, 0, 0), pos(0, 0, 1)),
                new SimEvent.LiftRideFinished(31, 0, pos(0, 0, 1)))));
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());
        assertTrue(run.sim.world().object(3).isAlive());
        assertEquals(pos(0, 0, 1), run.sim.world().spider(Z).cell());
    }

    @ParameterizedTest
    @CsvSource({"0, 800", "1, 400", "2, 267", "3, 200"})
    void t11TheTimeBombExplodesAfterItsFuseAndHitsOnlyItsFourNeighbours(int speed, int frame) {
        // z=4 "...FF", z=3 "...F": bomb (3,3) slot 3, blocks (3,4) slot 4 and (4,4) slot 5 (diagonal)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "...FF", "...F", "", "", "")
                .object(3, 3, 0, ObjectType.TMB).object(3, 4, 0, ObjectType.BLK).object(4, 4, 0, ObjectType.BLK)
                .timeBombSpeed(speed));
        run.runUntilFrame(frame - 1);
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        List<SimEvent> exploding = run.step();

        int exploded = exploding.indexOf(new SimEvent.Exploded(frame, 3,
                List.of(pos(2, 3, 0), pos(3, 2, 0), pos(4, 3, 0), pos(3, 4, 0))));
        assertEquals(List.of(new SimEvent.Destroyed(frame, 4, pos(3, 4, 0)),
                new SimEvent.Destroyed(frame, 3, pos(3, 3, 0))), exploding.subList(exploded + 1, exploded + 3));
        run.runUntilFrame(frame + 32);
        WorldState world = run.sim.world();
        assertTrue(world.object(5).isAlive(), "the diagonal block");
        assertEquals(ObjectStatus.REMOVED, world.object(3).status());
        assertTrue(world.hasFloor(pos(3, 3, 0)), "a bomb never destroys the floor");
        assertEquals(0, world.counters().robotsDestroyed());
    }

    /**
     * T-12/T-13 layout: z=2 "FFFFFFFF" with the worm E/W at (0,2) (slot 4); z=1 "..F" with the zapper (2,1); z=0 "FFF"
     * with the grabber (0,0) turned E and the freezer (2,0) (slot 3); 2 robots required. The zapper's fire north into
     * the void ends READY.
     */
    private static SimRun t12() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFFFFFFF", "..F", "FFF").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.FRZ).object(2, 1, 0, ObjectType.ZAP).object(0, 2, 0, ObjectType.WOE)
                .conditions(0, false, 0, 2).build());
        run.turnInReady(new Command.TurnRight(G));
        run.step(new Command.Fire(Z));
        return run;
    }

    @Test
    void t12TheFreezerStopsEveryNonSpiderMovementForSixtyFourFrames() {
        SimRun run = t12();
        run.runUntilFrame(29);
        SimObject worm = run.sim.world().object(4);
        String before = moveState(worm);
        assertTrue(worm.isMoving());

        List<SimEvent> frame30 = run.step(new Command.Fire(G));

        assertTrue(frame30.containsAll(List.of(new SimEvent.Frozen(30, 3),
                new SimEvent.RayHit(30, 2, 3, RayOutcome.GRABBED))));
        for (int frame = 30; frame <= 93; frame++) {
            if (frame > 30) {
                run.step();
            }
            assertEquals(before, moveState(worm), "frame " + frame);
        }
        assertEquals(List.of(new SimEvent.Thawed(93, 3)), run.events(SimEvent.Thawed.class));
        assertFalse(run.sim.world().isFrozen());
        run.step();
        assertNotEquals(before, moveState(worm), "movement resumes in frame 94");
        run.runUntilFrame(156);
        assertTrue(run.step(new Command.Fire(G)).contains(new SimEvent.RayHit(157, 2, 3, RayOutcome.ABSORBED)));
        assertTrue(run.step(new Command.Fire(G)).contains(new SimEvent.Frozen(158, 3)));
    }

    @Test
    void t13AZapOnTheFreezerInItsFreezePhaseThawsTheWorldInTheSameFrame() {
        SimRun run = t12();
        run.step(new Command.TurnRight(Z));
        run.runUntilFrame(4);
        run.step(new Command.TurnRight(Z));
        run.runUntilFrame(29);
        run.step(new Command.Fire(G));
        run.runUntilFrame(39);
        SimObject worm = run.sim.world().object(4);
        String frozen = moveState(worm);
        assertTrue(run.sim.world().isFrozen());

        List<SimEvent> frame40 = run.step(new Command.Fire(Z));

        int destroyed = frame40.indexOf(new SimEvent.Destroyed(40, 3, pos(2, 0, 0)));
        assertEquals(List.of(new SimEvent.Destroyed(40, 3, pos(2, 0, 0)), new SimEvent.Thawed(40, 3),
                new SimEvent.CounterChanged(40, CounterKind.ROBOTS, 1),
                new SimEvent.RayHit(40, 0, 3, RayOutcome.DESTROYED)), frame40.subList(destroyed, destroyed + 4));
        assertFalse(run.sim.world().isFrozen());
        assertNotEquals(frozen, moveState(worm), "the worm moved in frame 40");
    }

    @Test
    void t14TheWiperSweepsEverySixteenFramesAndSparesSupportedLiftAndOccupiedFloors() {
        // level 1: row z=3 and column x=3 of floor, a box at (3,6,1) and an up-lift at (3,5,1); level 0: a box under
        // (1,3,1); wiper (3,3,1) slot 3; grabber (5,3,1), turned W in frame 1
        SimRun run = SimRun.startTime(TestTowers.tower()
                .level(0, ".B", "", "", "")
                .level(1, "...F....", "...B....", "...U....", "...F....", "FFFFFFFF", "...F....", "...F....", "...F....")
                .level(2, "...u", "", "", "", "", "")
                .object(3, 3, 1, ObjectType.WIP).object(5, 3, 1, ObjectType.GRB));
        run.step(new Command.TurnLeft(G));
        run.runUntilFrame(9);
        assertTrue(run.step(new Command.Fire(G)).contains(new SimEvent.RayHit(10, 2, 3, RayOutcome.GRABBED)));
        run.runUntilFrame(80);

        List<SimEvent.WiperSweep> sweeps = run.events(SimEvent.WiperSweep.class);
        assertEquals(List.of("24 WEST", "40 SOUTH", "56 EAST", "72 NORTH"),
                sweeps.stream().map(sweep -> sweep.frame() + " " + sweep.direction()).toList());
        assertEquals(List.of(pos(3, 4, 1), pos(3, 5, 1)), sweeps.get(3).cells(), "the sweep stops at the box (3,6,1)");
        assertEquals(List.of(new SimEvent.FloorEaten(24, pos(2, 3, 1)), new SimEvent.FloorEaten(24, pos(0, 3, 1)),
                new SimEvent.FloorEaten(40, pos(3, 2, 1)), new SimEvent.FloorEaten(40, pos(3, 1, 1)),
                new SimEvent.FloorEaten(40, pos(3, 0, 1)), new SimEvent.FloorEaten(56, pos(4, 3, 1)),
                new SimEvent.FloorEaten(56, pos(6, 3, 1)), new SimEvent.FloorEaten(56, pos(7, 3, 1)),
                new SimEvent.FloorEaten(72, pos(3, 4, 1))), run.events(SimEvent.FloorEaten.class));
        WorldState world = run.sim.world();
        assertTrue(world.hasFloor(pos(1, 3, 1)), "on the supporting box (1,3,0)");
        assertTrue(world.hasFloor(pos(5, 3, 1)), "held by the grabber");
        assertTrue(world.isLift(pos(3, 5, 1)), "a lift");
        assertTrue(world.hasFloor(pos(3, 7, 1)), "behind the box");
        assertTrue(world.hasFloor(pos(3, 3, 1)), "the wiper's own cell");
    }

    @Test
    void t27ForcerUpMovesOnlyTheFreeLiftAndLocksFurtherGrabsForThirtyTwoFrames() {
        // z=2 "F....", z=1 "F....", z=0 "F.U.U", level 1 "..u.u": grabber (0,0) facing N, hopper (2,0) slot 3 on the
        // west lift, forcer up (0,2) slot 4
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F....", "F....", "F.U.U").level(1, "..u.u")
                .object(0, 0, 0, ObjectType.GRB).object(2, 0, 0, ObjectType.HOP).object(0, 2, 0, ObjectType.FCU));
        run.runUntilFrame(9);

        List<SimEvent> frame10 = run.step(new Command.Fire(G));

        assertTrue(frame10.containsAll(List.of(new SimEvent.ForcerLiftStarted(10, pos(4, 0, 0), true),
                new SimEvent.RayHit(10, 2, 4, RayOutcome.GRABBED))));
        run.runUntilFrame(17);
        assertTrue(run.step(new Command.Fire(G)).contains(new SimEvent.RayHit(18, 2, 4, RayOutcome.ABSORBED)),
                "locked");
        run.runUntilFrame(40);
        WorldState world = run.sim.world();
        assertEquals(LiftState.MOVING_UP, world.liftAt(pos(4, 0, 0)).orElseThrow().state());
        List<SimEvent> frame41 = run.step(new Command.Fire(G));
        assertTrue(frame41.contains(new SimEvent.RayHit(41, 2, 4, RayOutcome.ABSORBED)), "still locked at input");
        assertTrue(frame41.contains(new SimEvent.ForcerLiftFinished(41, pos(4, 0, 0))));
        assertTrue(run.step(new Command.Fire(G)).contains(new SimEvent.RayHit(42, 2, 4, RayOutcome.GRABBED)),
                "a hit although no lift qualifies");

        assertEquals(List.of(new SimEvent.ForcerLiftStarted(10, pos(4, 0, 0), true)),
                run.events(SimEvent.ForcerLiftStarted.class));
        assertTrue(world.isBlocked(pos(4, 0, 0)));
        assertTrue(world.hasFloor(pos(4, 0, 1)));
        assertEquals(LiftState.TOP, world.liftAt(pos(4, 0, 0)).orElseThrow().state());
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(2, 0, 0)).orElseThrow().state());
        assertFalse(world.hasFloor(pos(2, 0, 1)));
        assertEquals(pos(2, 0, 0), world.object(3).cell(), "the hopper stays on its lift");
    }

    @Test
    void deviationFourAZapDuringTheRechargeDestroysTheFreezerAndASecondFreezerStillFreezes() {
        // z=1 "..F", z=0 "FFFFF": grabber (0,0) turned E, freezer A (2,0) slot 3, freezer B (4,0) slot 4, zapper (2,1)
        // turned S; the pusher at (7,4) fires north into the void to end READY
        SimRun run = new SimRun(TestTowers.tower().level(0, "..F", "FFFFF").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.FRZ).object(4, 0, 0, ObjectType.FRZ).object(2, 1, 0, ObjectType.ZAP)
                .object(7, 4, 0, ObjectType.PSH).conditions(0, false, 0, 3).build());
        run.turnInReady(new Command.TurnRight(G));
        run.turnInReady(new Command.TurnRight(Z));
        run.turnInReady(new Command.TurnRight(Z));
        run.step(new Command.Fire(P));
        run.runUntilFrame(9);
        run.step(new Command.Fire(G));
        run.runUntilFrame(99);
        WorldState world = run.sim.world();
        assertEquals(38, world.object(3).classTimer(), "recharge phase");
        assertEquals(0, world.freezeCounter());

        List<SimEvent> frame100 = run.step(new Command.Fire(Z));

        assertTrue(frame100.contains(new SimEvent.Destroyed(100, 3, pos(2, 0, 0))));
        assertEquals(List.of(new SimEvent.Thawed(73, 3)), run.events(SimEvent.Thawed.class), "no thaw by the zap");
        assertEquals(0, world.freezeCounter(), "the original would reach +1 here");
        assertEquals(1, world.counters().robotsDestroyed());
        run.runUntilFrame(109);

        List<SimEvent> frame110 = run.step(new Command.Fire(G));

        assertTrue(frame110.contains(new SimEvent.Frozen(110, 4)), "the grab ray passes the destroyed freezer A");
        assertTrue(world.isFrozen());
        assertEquals(-1, world.freezeCounter());
    }

    @Test
    void twoStackedFreezersKeepTheWorldFrozenUntilBothHaveThawed() {
        // z=2 "F", z=1 "F", z=0 "FFF": grabber (0,0) turned E, freezer A (2,0) slot 3, freezer B (0,2) slot 4;
        // the pusher at (7,4) ends READY
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F", "FFF").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.FRZ).object(0, 2, 0, ObjectType.FRZ).object(7, 4, 0, ObjectType.PSH)
                .build());
        run.turnInReady(new Command.TurnRight(G));
        run.step(new Command.Fire(P));
        run.runUntilFrame(9);
        run.step(new Command.Fire(G));
        run.runUntilFrame(17);
        run.step(new Command.TurnLeft(G));
        run.runUntilFrame(21);
        run.step(new Command.Fire(G));
        WorldState world = run.sim.world();
        assertEquals(-2, world.freezeCounter());
        run.runUntilFrame(84);
        assertEquals(-1, world.freezeCounter(), "A thawed in frame 73");
        assertTrue(world.isFrozen());

        run.runUntilFrame(85);

        assertEquals(List.of(new SimEvent.Frozen(10, 3), new SimEvent.Frozen(22, 4)), run.events(SimEvent.Frozen.class));
        assertEquals(List.of(new SimEvent.Thawed(73, 3), new SimEvent.Thawed(85, 4)),
                run.events(SimEvent.Thawed.class));
        assertFalse(world.isFrozen());
        assertEquals(0, world.freezeCounter());
    }

    @Test
    void aPushedObjectWaitsHalfWayWhileTheWorldIsFrozen() {
        // z=2 "FFF", z=1 "", z=0 "FFFF": pusher (0,0) turned E, block (1,0) slot 3; grabber (0,2) turned E,
        // freezer (2,2) slot 4
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF", "", "FFFF").object(0, 0, 0, ObjectType.PSH)
                .object(1, 0, 0, ObjectType.BLK).object(0, 2, 0, ObjectType.GRB).object(2, 2, 0, ObjectType.FRZ)
                .build());
        run.turnInReady(new Command.TurnRight(P));
        run.turnInReady(new Command.TurnRight(G));
        run.step(new Command.Fire(P));
        run.runUntilFrame(9);
        SimObject block = run.sim.world().object(3);
        assertEquals(7, block.moveCounter());
        assertEquals(pos(2, 0, 0), block.cell(), "half-way since frame 8");

        run.step(new Command.Fire(G));
        for (int frame = 10; frame <= 73; frame++) {
            if (frame > 10) {
                run.step();
            }
            assertEquals(7, block.moveCounter(), "frame " + frame);
        }

        assertEquals(pos(2, 0, 0), block.cell());
        assertEquals(pos(1, 0, 0), block.secondCell());
        run.step();
        assertEquals(6, block.moveCounter(), "frame 74");
    }

    @Test
    void aBombNextToABombDestroysItWithoutAChainExplosion() {
        // F (0..1,0): bombs (0,0) slot 3 and (1,0) slot 4, speed 3
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.TMB)
                .object(1, 0, 0, ObjectType.TMB).timeBombSpeed(3));
        run.runUntilFrame(240);

        assertEquals(List.of(new SimEvent.Exploded(200, 3,
                List.of(pos(-1, 0, 0), pos(0, -1, 0), pos(1, 0, 0), pos(0, 1, 0)))), run.events(SimEvent.Exploded.class));
        assertTrue(run.events.containsAll(List.of(new SimEvent.Destroyed(200, 4, pos(1, 0, 0)),
                new SimEvent.Removed(231, 4, pos(1, 0, 0)), new SimEvent.Removed(232, 3, pos(0, 0, 0)))));
    }

    @Test
    void aMineDestroysABlockAndItsOwnFloorGoesWhenItsDestructionEnds() {
        // z=1 ".FF", z=0 "FFF": pusher (1,0) turned W walks away; mine (1,1) slot 3, block (2,1) slot 4; grabber (7,7)
        SimRun run = new SimRun(TestTowers.tower().level(0, ".FF", "FFF").object(1, 0, 0, ObjectType.PSH)
                .object(1, 1, 0, ObjectType.PRM).object(2, 1, 0, ObjectType.BLK).object(7, 7, 0, ObjectType.GRB)
                .minesDestroyFloor(true).build());
        run.turnInReady(new Command.TurnLeft(P));
        run.step(new Command.Forward(P));
        run.runUntilFrame(32);
        WorldState world = run.sim.world();
        assertTrue(run.events.containsAll(List.of(new SimEvent.Destroyed(1, 4, pos(2, 1, 0)),
                new SimEvent.Removed(32, 4, pos(2, 1, 0)))));
        assertTrue(world.hasFloor(pos(1, 1, 0)));

        run.runUntilFrame(33);

        assertTrue(run.events.containsAll(List.of(new SimEvent.Removed(33, 3, pos(1, 1, 0)),
                new SimEvent.FloorEaten(33, pos(1, 1, 0)))));
        assertFalse(world.hasFloor(pos(1, 1, 0)));
        assertTrue(world.hasFloor(pos(2, 1, 0)), "only the mine's own cell");
    }
}
