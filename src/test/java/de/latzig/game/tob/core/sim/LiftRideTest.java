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

class LiftRideTest {

    private final List<SimEvent> out = new ArrayList<>();

    /** Up-lift: start U at (1,0,0), stop u at (1,0,1); the zapper stands on the platform. */
    private static WorldState upLift() {
        return WorldState.load(TestTowers.tower().level(0, "FU").level(1, ".u").object(1, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
    }

    /** Down-lift: start D at (0,0,1) above stop d at (0,0,0); the zapper stands on the platform. */
    private static WorldState downLift() {
        return WorldState.load(TestTowers.tower().level(0, "d").level(1, "D").object(0, 0, 1, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
    }

    private void advance(WorldState world, SimObject rider, int frames) {
        for (int i = 0; i < frames; i++) {
            LiftRide.advance(world, rider, out);
        }
    }

    @Test
    void theEaseTableSumsToOneLevelAndIsSymmetric() {
        int sum = 0;
        for (int counter = 1; counter <= LiftRide.FRAMES; counter++) {
            sum += LiftRide.ease(counter);
            assertEquals(LiftRide.ease(counter), LiftRide.ease(LiftRide.FRAMES + 1 - counter));
        }
        assertEquals(SimObject.UNITS_PER_CELL, sum);
        assertEquals(3, LiftRide.ease(32));
        assertEquals(17, LiftRide.ease(29));
        assertEquals(18, LiftRide.ease(16));
    }

    @Test
    void aRideUpFloorsAndClaimsTheUpperCellAtOnce() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);

        assertTrue(LiftRide.start(world, zap, out));
        assertEquals(List.of(new SimEvent.LiftRideStarted(0, 0, pos(1, 0, 0), pos(1, 0, 1))), out);
        assertTrue(world.hasFloor(pos(1, 0, 1)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 1)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 0)));
        assertFalse(world.isBlocked(pos(1, 0, 0)));
        assertEquals(MoveKind.LIFT_UP, zap.moveKind());
        assertTrue(zap.isRiding());
        Lift lift = world.liftAt(pos(1, 0, 0)).orElseThrow();
        assertEquals(LiftState.MOVING_UP, lift.state());
        assertEquals(0, lift.rider());
    }

    @Test
    void aRideUpEndsAfter32FramesWithTheLowerCellBlocked() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        LiftRide.start(world, zap, out);

        advance(world, zap, 1);
        assertEquals(3, zap.worldY());
        advance(world, zap, 30);
        assertTrue(zap.isRiding());
        assertEquals(pos(1, 0, 0), zap.cell());
        advance(world, zap, 1);

        assertFalse(zap.isMoving());
        assertEquals(pos(1, 0, 1), zap.cell());
        assertNull(zap.secondCell());
        assertEquals(SimObject.UNITS_PER_CELL, zap.worldY());
        assertTrue(world.isBlocked(pos(1, 0, 0)));
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 1)));
        Lift lift = world.liftAt(pos(1, 0, 1)).orElseThrow();
        assertEquals(LiftState.TOP, lift.state());
        assertEquals(Occupancy.FREE, lift.rider());
        assertEquals(new SimEvent.CellChanged(0, 0, pos(1, 0, 0), pos(1, 0, 1)), out.get(1));
        assertEquals(new SimEvent.LiftRideFinished(0, 0, pos(1, 0, 1)), out.get(2));
    }

    @Test
    void aRideDownUnblocksAndClaimsTheLowerCellAndRemovesTheUpperFloorAtTheEnd() {
        WorldState world = downLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);

        assertTrue(LiftRide.start(world, zap, out));
        assertEquals(MoveKind.LIFT_DOWN, zap.moveKind());
        assertFalse(world.isBlocked(pos(0, 0, 0)));
        assertEquals(0, world.occupancy().holder(pos(0, 0, 0)));
        advance(world, zap, 32);

        assertEquals(pos(0, 0, 0), zap.cell());
        assertEquals(0, zap.worldY());
        assertFalse(world.hasFloor(pos(0, 0, 1)));
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 1)));
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(0, 0, 0)).orElseThrow().state());
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT, world.flags(pos(0, 0, 0)));
        assertEquals(CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(pos(0, 0, 1)));
    }

    @Test
    void reversingARideRetracesItAtAnyCounter() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        LiftRide.start(world, zap, out);
        advance(world, zap, 10);

        assertTrue(LiftRide.reverse(world, zap, out), "counter 22 is refused for walks only");
        assertEquals(MoveKind.LIFT_DOWN, zap.moveKind());
        assertEquals(10, zap.moveCounter());
        assertEquals(pos(1, 0, 1), zap.cell());
        assertEquals(LiftState.MOVING_DOWN, world.liftAt(pos(1, 0, 0)).orElseThrow().state());
        advance(world, zap, 10);

        assertFalse(zap.isMoving());
        assertEquals(pos(1, 0, 0), zap.cell());
        assertEquals(0, zap.worldY());
        assertFalse(world.hasFloor(pos(1, 0, 1)));
        assertFalse(world.isBlocked(pos(1, 0, 0)));
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 1)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 0)));
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(1, 0, 0)).orElseThrow().state());
    }

    @Test
    void aRideThatHasNotMovedYetCannotBeReversed() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        LiftRide.start(world, zap, out);

        assertFalse(LiftRide.reverse(world, zap, out), "counter 32: 32 − 32 would stop the rider between the cells");
        assertEquals(LiftRide.FRAMES, zap.moveCounter());
        assertEquals(MoveKind.LIFT_UP, zap.moveKind());
        assertEquals(pos(1, 0, 0), zap.cell());
        advance(world, zap, 1);
        assertTrue(LiftRide.reverse(world, zap, out), "from counter 31 on, as before");
    }

    @Test
    void aRideNeedsAStandingSpiderOnALiftCell() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        assertTrue(LiftRide.start(world, zap, out));
        assertFalse(LiftRide.start(world, zap, out), "already riding");

        WorldState plain = WorldState.load(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP).build(),
                SimOptions.DEFAULT);
        assertFalse(LiftRide.start(plain, plain.spider(SpiderId.ZAPPER), out));
        assertFalse(LiftRide.reverse(plain, plain.spider(SpiderId.ZAPPER), out));
    }

    /**
     * Deviation 2 pinning test (reviewer's I1 probe, final-review-result.md): an exchange mid-ride leaves the shaft
     * half-switched (the upper cell keeps its ride-start floor; the lower cell is never blocked). A pusher then
     * walks onto the now-floored upper cell. Before the fix, a later {@code LiftRide.start} from the grabber
     * overwrote the pusher's occupancy there (reported as "slot 1 does not hold its cell"); now it must be refused.
     */
    @Test
    void anExchangeMidRideLeavesAWalkableCellButARepeatedRideIsRefused() {
        WorldState world = WorldState.load(TestTowers.tower().level(0, "UF").level(1, "uF")
                .object(0, 0, 0, ObjectType.GRB).object(1, 0, 0, ObjectType.EXC).object(1, 0, 1, ObjectType.PSH)
                .build(), SimOptions.DEFAULT);
        SimObject grabber = world.spider(SpiderId.GRABBER);
        SimObject exchanger = world.object(3);
        SimObject pusher = world.spider(SpiderId.PUSHER);
        SimContext ctx = new SimContext(world);
        ctx.begin(out);

        assertTrue(LiftRide.start(world, grabber, out));
        advance(world, grabber, 5);
        assertTrue(grabber.isRiding(), "mid-ride");

        // The exchanger's grab fires while the grabber rides (ExchangerBehaviour.onGrab, S:8981-9022): both claims
        // are released and the two swap cells; the grabber's ride stops mid-shaft.
        ctx.swap(grabber, exchanger);
        ctx.stop(grabber);
        Rules.remove(world, exchanger, out);

        assertFalse(grabber.isMoving(), "the ride stops mid-shaft");
        assertTrue(world.hasFloor(pos(0, 0, 1)), "the upper cell keeps the floor it got at ride start");
        assertFalse(world.isBlocked(pos(0, 0, 0)), "the lower cell never gets blocked");
        assertEquals(List.of(), world.occupancyProblems());

        // The pusher walks onto the now-floored, now-unheld upper cell.
        assertTrue(Walk.start(world, pusher, Direction.WEST, out));
        for (int i = 0; i < Walk.FRAMES; i++) {
            Walk.advance(world, pusher, out);
        }
        assertEquals(pos(0, 0, 1), pusher.cell());
        assertEquals(pusher.slot(), world.occupancy().holder(pos(0, 0, 1)));
        assertEquals(List.of(), world.occupancyProblems());

        // The grabber walks back onto the lift's lower cell (now free) and presses Back.
        assertTrue(Walk.start(world, grabber, Direction.WEST, out));
        for (int i = 0; i < Walk.FRAMES; i++) {
            Walk.advance(world, grabber, out);
        }
        assertEquals(pos(0, 0, 0), grabber.cell());

        assertFalse(LiftRide.start(world, grabber, out), "the far cell is held by the pusher, slot " + pusher.slot());
        assertEquals(List.of(), world.occupancyProblems());
        assertEquals(pusher.slot(), world.occupancy().holder(pos(0, 0, 1)), "the pusher still holds its cell");
    }

    @Test
    void upAndDownAgainRestoresTheStartFlags() {
        WorldState world = upLift();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        int lower = world.flags(pos(1, 0, 0));
        int upper = world.flags(pos(1, 0, 1));

        LiftRide.start(world, zap, out);
        advance(world, zap, 32);
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT | CellFlags.BLOCKED, world.flags(pos(1, 0, 0)));
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(pos(1, 0, 1)));
        assertTrue(LiftRide.start(world, zap, out));
        assertEquals(MoveKind.LIFT_DOWN, zap.moveKind());
        advance(world, zap, 32);

        assertEquals(lower, world.flags(pos(1, 0, 0)));
        assertEquals(upper, world.flags(pos(1, 0, 1)));
        assertEquals(pos(1, 0, 0), zap.cell());
    }
}
