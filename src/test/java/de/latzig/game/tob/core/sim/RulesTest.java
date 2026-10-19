package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

class RulesTest {

    private final List<SimEvent> out = new ArrayList<>();

    private static WorldState world(TestTowers.Builder tower) {
        return WorldState.load(tower.build(), SimOptions.DEFAULT);
    }

    private static TestTowers.Builder threeSpiders() {
        return TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.PSH)
                .object(2, 0, 0, ObjectType.GRB);
    }

    @Test
    void countersSaturateAtTheirRequiredValue() {
        WorldState world = world(TestTowers.tower().conditions(0, false, 1, 2));

        assertTrue(Rules.addKlondike(world, out));
        assertFalse(Rules.addKlondike(world, out));
        assertTrue(Rules.addRobot(world, out));

        assertEquals(1, world.counters().klondikesCollected());
        assertEquals(1, world.counters().robotsDestroyed());
        assertEquals(List.of(new SimEvent.CounterChanged(0, CounterKind.KLONDIKES, 1),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1)), out);
        assertFalse(world.isExitPending());
    }

    @Test
    void victoryNeedsBothCountersAndWaits21FramesPlusTheClockPhase() {
        WorldState world = world(TestTowers.tower().conditions(0, false, 1, 1));
        world.countClock();
        world.countClock();
        world.countClock();
        assertEquals(4, world.clockPhase());

        Rules.addKlondike(world, out);
        assertFalse(world.isExitPending());
        Rules.addRobot(world, out);

        assertEquals(new SimEvent.ExitPending(0, Rules.EXIT_COMPLETED, 25), out.get(out.size() - 1));
        assertEquals(Rules.EXIT_COMPLETED, world.exitCode());
        assertEquals(25, world.exitDelay());
    }

    @Test
    void aTowerWithoutTasksNeverCompletes() {
        WorldState world = world(TestTowers.tower().conditions(0, false, 0, 0));

        assertFalse(Rules.addKlondike(world, out));
        assertFalse(Rules.addRobot(world, out));

        assertTrue(out.isEmpty());
        assertFalse(world.isExitPending());
    }

    @Test
    void aPendingExitIsNeverOverridden() {
        WorldState world = world(TestTowers.tower());

        assertTrue(Rules.setExit(world, Rules.EXIT_TIMEOUT, out));
        assertFalse(Rules.setExit(world, Rules.EXIT_COMPLETED, out));

        assertEquals(Rules.EXIT_TIMEOUT, world.exitCode());
        assertEquals(1, out.size());
    }

    @Test
    void thePendingExitFinishesTheWorldWhenItsDelayRunsOut() {
        WorldState world = world(TestTowers.tower());
        Rules.setExit(world, Rules.EXIT_COMPLETED, out);

        for (int frame = 1; frame < 21; frame++) {
            assertFalse(world.countDownExit(), "frame " + frame);
        }
        assertTrue(world.countDownExit());

        assertTrue(world.isFinished());
        assertFalse(world.isExitPending());
        assertEquals(Rules.EXIT_COMPLETED, world.exitCode());
    }

    @Test
    void losingTheGrabberWithKlondikesMissingIsExitFour() {
        WorldState world = world(threeSpiders().conditions(0, false, 1, 0));

        assertTrue(Rules.destroy(world, world.spider(SpiderId.GRABBER), out));

        assertEquals(List.of(new SimEvent.Destroyed(0, 2, pos(2, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_GRABBER_DESTROYED, 21)), out);
    }

    @Test
    void losingTheLastSpidersWithTheKlondikesCompleteIsExitThree() {
        WorldState world = world(threeSpiders().conditions(0, false, 0, 1));

        Rules.destroy(world, world.spider(SpiderId.GRABBER), out);
        assertFalse(world.isExitPending());
        Rules.destroy(world, world.spider(SpiderId.ZAPPER), out);
        assertFalse(world.isExitPending());
        Rules.destroy(world, world.spider(SpiderId.PUSHER), out);

        assertEquals(Rules.EXIT_SPIDERS_DESTROYED, world.exitCode());
    }

    @Test
    void nothingFailsWhileTheGrabberLives() {
        WorldState world = world(threeSpiders().conditions(0, false, 1, 0));

        Rules.destroy(world, world.spider(SpiderId.ZAPPER), out);
        Rules.destroy(world, world.spider(SpiderId.PUSHER), out);

        assertFalse(world.isExitPending());
    }

    @Test
    void destroyingAWalkerStopsItAndReleasesItsClaim() {
        WorldState world = world(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.GRB));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        Walk.start(world, zap, Direction.EAST, out);
        for (int frame = 0; frame < 10; frame++) {
            Walk.advance(world, zap, out);
        }
        assertEquals(0, world.occupancy().holder(pos(1, 0, 0)));
        out.clear();

        assertTrue(Rules.destroy(world, zap, out));

        assertEquals(List.of(new SimEvent.Destroyed(0, 0, pos(0, 0, 0))), out);
        assertEquals(ObjectStatus.DESTROYING, zap.status());
        assertFalse(zap.isMoving());
        assertNull(zap.secondCell());
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
        assertEquals(0, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(Rules.DESTROY_FRAMES, zap.destroyCounter());
    }

    @Test
    void destructionClearsTheFlagWord() {
        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.KLD));
        SimObject klondike = world.object(3);
        assertEquals(0x0209, klondike.objectFlags());

        assertTrue(Rules.destroy(world, klondike, out));

        assertEquals(0, klondike.objectFlags(), "S:8786");
    }

    @Test
    void aZappedObjectCountsARobotForAnyShooterAndTheCountSaturates() {
        WorldState world = world(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.FZE)
                .object(1, 0, 0, ObjectType.BFL).object(2, 0, 0, ObjectType.KLD).conditions(0, false, 0, 2));

        Rules.zap(world, world.object(3), out);
        Rules.zap(world, world.object(4), out);
        Rules.zap(world, world.object(5), out);

        assertEquals(List.of(new SimEvent.Destroyed(0, 3, pos(0, 0, 0)),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1),
                new SimEvent.Destroyed(0, 4, pos(1, 0, 0)),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 2),
                new SimEvent.ExitPending(0, Rules.EXIT_COMPLETED, 21),
                new SimEvent.Destroyed(0, 5, pos(2, 0, 0))), out, "the zapped klondike would count, but 2 is reached");
        assertEquals(2, world.counters().robotsDestroyed());
    }

    @Test
    void aZappedSpiderIsNotCountedButRunsTheFailureCheck() {
        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.GRB)
                .conditions(0, false, 1, 1));

        Rules.zap(world, world.spider(SpiderId.GRABBER), out);

        assertEquals(List.of(new SimEvent.Destroyed(0, 2, pos(0, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_GRABBER_DESTROYED, 21)), out);
        assertEquals(0, world.counters().robotsDestroyed());
    }

    @Test
    void zappingAnActiveFreezerThawsTheWorld() {
        WorldState world = world(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.FRZ)
                .object(1, 0, 0, ObjectType.FRZ).conditions(0, false, 0, 2));
        world.thaw();
        world.freeze();
        SimObject active = world.object(3);
        SimObject idle = world.object(4);
        active.setClassTimer(100);

        Rules.zap(world, idle, out);
        assertTrue(world.isFrozen(), "a freezer with timer 0 changes nothing");
        Rules.zap(world, active, out);

        assertFalse(world.isFrozen());
        assertEquals(0, active.classTimer());
        assertEquals(2, world.counters().robotsDestroyed());
    }

    @Test
    void anExplosionDestroysEveryNeighbourHolderButSparesRidersAndDiagonals() {
        // level 0, north first: z=2 "FFF", z=1 "FFU", z=0 ".F."; level 1: the up stop above (2,1)
        // slots: zapper 0 on the lift (2,1); converter (1,0) 3; block (0,1) 4; bomb (1,1) 5; mine (1,2) 6; block (2,2) 7
        WorldState world = world(TestTowers.tower().level(0, "FFF", "FFU", ".F.").level(1, "..u", "")
                .object(2, 1, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.CVN).object(0, 1, 0, ObjectType.BLK)
                .object(1, 1, 0, ObjectType.TMB).object(1, 2, 0, ObjectType.PRM).object(2, 2, 0, ObjectType.BLK)
                .conditions(0, false, 0, 5));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        assertTrue(LiftRide.start(world, zap, out));
        out.clear();

        Rules.explode(world, world.object(5), false, out);

        assertEquals(List.of(
                new SimEvent.Exploded(0, 5, List.of(pos(0, 1, 0), pos(1, 0, 0), pos(2, 1, 0), pos(1, 2, 0))),
                new SimEvent.Destroyed(0, 4, pos(0, 1, 0)), new SimEvent.Destroyed(0, 3, pos(1, 0, 0)),
                new SimEvent.Destroyed(0, 6, pos(1, 2, 0)), new SimEvent.Destroyed(0, 5, pos(1, 1, 0))), out);
        assertTrue(zap.isAlive(), "a lift rider is spared");
        assertTrue(world.object(7).isAlive(), "diagonal cells are never hit");
        assertEquals(0, world.counters().robotsDestroyed(), "explosions never count");
        assertEquals(0, world.object(5).objectFlags());
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void anExplosionReArmsANeighbourThatIsAlreadyBeingDestroyed() {
        // F (0..1,0): block (0,0) slot 3, mine (1,0) slot 4; no spiders, so the failure check ends the attempt
        WorldState world = world(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.BLK)
                .object(1, 0, 0, ObjectType.PRM));
        SimObject block = world.object(3);
        Rules.destroy(world, block, out);
        for (int tick = 0; tick < 10; tick++) {
            Rules.tickDestroyed(world, block, out);
        }
        out.clear();

        Rules.explode(world, world.object(4), true, out);

        assertEquals(Rules.DESTROY_FRAMES, block.destroyCounter(), "class $1A and +1A := 32 again (S:9699-9706)");
        assertEquals(List.of(
                new SimEvent.Exploded(0, 4, List.of(pos(0, 0, 0), pos(1, -1, 0), pos(2, 0, 0), pos(1, 1, 0))),
                new SimEvent.Destroyed(0, 3, pos(0, 0, 0)), new SimEvent.Destroyed(0, 4, pos(1, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_SPIDERS_DESTROYED, 21)), out);
        assertEquals(ObjectFlags.DESTROYS_FLOOR, world.object(4).objectFlags());
    }

    @Test
    void aMineMarkedToDestroyTheFloorTakesItWhenItsDestructionEndsUnlessTheRuleSparesIt() {
        // level 0: mine (0,0) slot 3 on F, box (1,0); level 1: mine slot 4 on F (1,0,1) above the box
        WorldState world = world(TestTowers.tower().level(0, "FB").level(1, ".F").object(0, 0, 0, ObjectType.PRM)
                .object(1, 0, 1, ObjectType.PRM));
        SimObject low = world.object(3);
        SimObject high = world.object(4);
        Rules.explode(world, low, true, out);
        Rules.explode(world, high, true, out);
        out.clear();
        for (int tick = 1; tick < Rules.DESTROY_FRAMES; tick++) {
            Rules.tickDestroyed(world, low, out);
            Rules.tickDestroyed(world, high, out);
        }
        assertTrue(world.hasFloor(pos(0, 0, 0)), "the floor stays during the 32 frames");

        Rules.tickDestroyed(world, low, out);
        Rules.tickDestroyed(world, high, out);

        assertEquals(List.of(new SimEvent.Removed(0, 3, pos(0, 0, 0)), new SimEvent.FloorEaten(0, pos(0, 0, 0)),
                new SimEvent.Removed(0, 4, pos(1, 0, 1))), out);
        assertEquals(0, world.flags(pos(0, 0, 0)));
        assertTrue(world.hasFloor(pos(1, 0, 1)), "a floor on a supporting block stays (S:9964-9967)");
        assertEquals(0, low.objectFlags(), "bclr #2,$12(a0) (S:9952)");
    }

    @Test
    void aSpiderKilledByAnExplosionRunsTheFailureCheckAndNothingIsCounted() {
        // F (0..1,0): grabber (0,0) slot 2, bomb (1,0) slot 3; one klondike required
        WorldState world = world(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.GRB)
                .object(1, 0, 0, ObjectType.TMB).conditions(0, false, 1, 1));

        Rules.explode(world, world.object(3), false, out);

        assertEquals(List.of(
                new SimEvent.Exploded(0, 3, List.of(pos(0, 0, 0), pos(1, -1, 0), pos(2, 0, 0), pos(1, 1, 0))),
                new SimEvent.Destroyed(0, 2, pos(0, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_GRABBER_DESTROYED, 21),
                new SimEvent.Destroyed(0, 3, pos(1, 0, 0))), out);
        assertEquals(0, world.counters().robotsDestroyed());
    }

    @Test
    void anExplodingObjectThatHoldsItsOwnSecondCellNeverTreatsItAsANeighbourVictim() {
        // level 0, 3x3 floor: bomb at (1,1) pushed east, so its claimed beyond cell (2,1) is its own east neighbour
        // (global-constraints clarification 3); west (0,1), south (1,0) and north (1,2) are the real neighbours.
        WorldState world = world(TestTowers.tower().level(0, "FFF", "FFF", "FFF").object(0, 1, 0, ObjectType.BLK)
                .object(1, 1, 0, ObjectType.TMB).object(1, 0, 0, ObjectType.BLK).object(1, 2, 0, ObjectType.BLK));
        SimObject bomb = world.object(world.occupancy().holder(pos(1, 1, 0)));
        SimObject west = world.object(world.occupancy().holder(pos(0, 1, 0)));
        SimObject south = world.object(world.occupancy().holder(pos(1, 0, 0)));
        SimObject north = world.object(world.occupancy().holder(pos(1, 2, 0)));
        assertTrue(Push.start(world, bomb, Direction.EAST, out));
        assertEquals(pos(2, 1, 0), bomb.secondCell(), "the claimed beyond cell is the bomb's own east neighbour");
        assertEquals(bomb.slot(), world.occupancy().holder(pos(2, 1, 0)), "the bomb itself holds that cell");
        out.clear();

        Rules.explode(world, bomb, false, out);

        List<Integer> destroyedOrder = out.stream().filter(SimEvent.Destroyed.class::isInstance)
                .map(SimEvent.Destroyed.class::cast).map(SimEvent.Destroyed::slot).toList();
        assertEquals(List.of(west.slot(), south.slot(), north.slot(), bomb.slot()), destroyedOrder,
                "the real neighbours W, S, N are destroyed, then the centre last, never through its own claimed cell");
        assertFalse(west.isAlive());
        assertFalse(south.isAlive());
        assertFalse(north.isAlive());
        assertEquals(ObjectStatus.DESTROYING, bomb.status());
        assertNull(bomb.secondCell(), "the claimed cell is released when the centre itself is destroyed");
        assertTrue(world.occupancy().isFree(pos(2, 1, 0)));
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void aDestroyedObjectIsRemovedOnTheThirtySecondTick() {
        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.BLK));
        SimObject block = world.object(3);
        assertTrue(Rules.destroy(world, block, out));

        for (int tick = 1; tick < Rules.DESTROY_FRAMES; tick++) {
            Rules.tickDestroyed(world, block, out);
            assertEquals(ObjectStatus.DESTROYING, block.status());
            assertEquals(3, world.occupancy().holder(pos(0, 0, 0)));
        }
        Rules.tickDestroyed(world, block, out);

        assertEquals(ObjectStatus.REMOVED, block.status());
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(new SimEvent.Removed(0, 3, pos(0, 0, 0)), out.get(out.size() - 1));
        assertNull(block.cell(), "the original clears the cell on removal (S:9951)");
        assertEquals(0, world.counters().robotsDestroyed());
    }

    @Test
    void ridersDeadAndAbsentObjectsAreNotDestroyed() {
        WorldState lift = world(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP));
        SimObject rider = lift.spider(SpiderId.ZAPPER);
        LiftRide.start(lift, rider, out);
        assertFalse(Rules.destroy(lift, rider, out));
        assertEquals(ObjectStatus.ALIVE, rider.status());

        WorldState world = world(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.BLK));
        SimObject block = world.object(3);
        assertTrue(Rules.destroy(world, block, out));
        assertFalse(Rules.destroy(world, block, out));
        assertFalse(Rules.destroy(world, world.spider(SpiderId.ZAPPER), out));
    }
}
