package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.Behaviours;

class SimContextTest {

    private final List<SimEvent> out = new ArrayList<>();
    private WorldState world;
    private SimContext ctx;

    private void start(TestTowers.Builder tower) {
        world = WorldState.load(tower.build(), SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
    }

    @Test
    void eventsGoToTheListOfTheCurrentFrame() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP));
        ctx.emit(new SimEvent.ReadyEnded(0));
        List<SimEvent> next = new ArrayList<>();
        ctx.begin(next);

        ctx.setFacing(world.spider(SpiderId.ZAPPER), Direction.EAST);

        assertEquals(List.of(new SimEvent.ReadyEnded(0)), out);
        assertEquals(List.of(new SimEvent.Turned(0, 0, Direction.EAST)), next);
        assertSame(next, ctx.out());
        assertSame(world, ctx.world());
        assertEquals(0, ctx.frame());
        assertEquals(Direction.EAST, world.spider(SpiderId.ZAPPER).facing());
    }

    @Test
    void everySlotGetsTheBehaviourOfItsType() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.BLK));

        for (SimObject object : world.objects()) {
            assertSame(Behaviours.forType(object.type()), ctx.behaviour(object), "slot " + object.slot());
        }
    }

    @Test
    void destroyAndTheCountersDelegateToTheRules() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.BFL)
                .conditions(0, false, 1, 1));
        SimObject flag = world.object(3);

        assertTrue(ctx.destroy(flag));
        assertFalse(ctx.destroy(flag), "already being destroyed");
        assertTrue(ctx.addRobot());
        assertTrue(ctx.addKlondike());

        assertEquals(ObjectStatus.DESTROYING, flag.status());
        assertEquals(List.of(new SimEvent.Destroyed(0, 3, pos(1, 0, 0)),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1),
                new SimEvent.CounterChanged(0, CounterKind.KLONDIKES, 1),
                new SimEvent.ExitPending(0, Rules.EXIT_COMPLETED, 21)), out);
    }

    @Test
    void claimAndReleaseManageTheSecondCell() {
        start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).object(2, 0, 0, ObjectType.BLK));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        SimObject block = world.object(3);

        assertTrue(ctx.claim(zap, pos(1, 0, 0)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 0)));
        assertEquals(pos(1, 0, 0), zap.secondCell());
        assertFalse(ctx.claim(zap, pos(1, 0, 0)), "it already holds a second cell");
        assertFalse(ctx.claim(block, pos(1, 0, 0)), "the cell is held");
        assertEquals(List.of(), world.occupancyProblems());

        ctx.release(zap);
        ctx.release(zap);

        assertNull(zap.secondCell());
        assertTrue(world.occupancy().isFree(pos(1, 0, 0)));
        assertEquals(List.of(), world.occupancyProblems());
        assertTrue(out.isEmpty());
    }

    @Test
    void removeFreesTheCellAndClearsThePosition() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.KLD));
        SimObject klondike = world.object(3);

        ctx.remove(klondike);

        assertEquals(ObjectStatus.REMOVED, klondike.status());
        assertNull(klondike.cell());
        assertTrue(world.occupancy().isFree(pos(0, 0, 0)));
        assertEquals(List.of(new SimEvent.Removed(0, 3, pos(0, 0, 0))), out);
    }

    @Test
    void flagsCountersAndTheFreezeCounterAreSetDirectly() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.KLD));
        SimObject klondike = world.object(3);

        ctx.setObjectFlags(klondike, klondike.objectFlags() | ObjectFlags.KLONDIKE_GRABBED);
        ctx.setDestroyCounter(klondike, 7);
        assertTrue(world.isFrozen(), "READY");
        ctx.thaw();
        assertFalse(world.isFrozen());
        ctx.freeze();

        assertTrue(world.isFrozen());
        assertTrue(klondike.isVanishing());
        assertEquals(0x0309, klondike.objectFlags());
        assertEquals(7, klondike.destroyCounter());
        assertTrue(out.isEmpty());
    }

    @Test
    void tryStepStartsAWalkWithoutClaimingAndIsFreeIsAPureQuery() {
        // F (0..2,0): hopper (0,0) slot 3, block (2,0) slot 4
        start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.HOP).object(2, 0, 0, ObjectType.BLK));
        SimObject hopper = world.object(3);

        assertTrue(ctx.isFree(pos(1, 0, 0)));
        assertFalse(ctx.isFree(pos(2, 0, 0)), "held by the block");
        assertFalse(ctx.isFree(pos(0, 1, 0)), "no floor");
        assertFalse(ctx.tryStep(hopper, Direction.NORTH), "no floor");
        assertTrue(ctx.tryStep(hopper, Direction.EAST));
        assertFalse(ctx.tryStep(hopper, Direction.EAST), "already walking");

        assertEquals(Walk.FRAMES, hopper.moveCounter());
        assertEquals(MoveKind.WALK, hopper.moveKind());
        assertEquals(Direction.EAST, hopper.moveDirection());
        assertTrue(ctx.isFree(pos(1, 0, 0)), "the target is claimed only at the claim frame");
        assertEquals(List.of(new SimEvent.MoveStarted(0, 3, pos(0, 0, 0), pos(1, 0, 0))), out);
    }

    @Test
    void removeFloorAppliesTheSharedRuleAndEmitsFloorEaten() {
        start(TestTowers.tower().level(0, "FF").object(1, 0, 0, ObjectType.BLK));

        assertTrue(ctx.removeFloor(pos(0, 0, 0)));
        assertFalse(ctx.removeFloor(pos(0, 0, 0)), "no floor any more");
        assertFalse(ctx.removeFloor(pos(1, 0, 0)), "occupied");

        assertFalse(world.hasFloor(pos(0, 0, 0)));
        assertTrue(world.hasFloor(pos(1, 0, 0)));
        assertEquals(List.of(new SimEvent.FloorEaten(0, pos(0, 0, 0))), out);
    }

    @Test
    void headingReverseWalkAndDeactivateChangeTheMover() {
        start(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.WOE));
        SimObject worm = world.object(3);

        ctx.setHeading(worm, Direction.WEST);
        assertFalse(ctx.reverseWalk(worm), "standing");
        assertTrue(ctx.tryStep(worm, Direction.WEST));
        worm.setMoveCounter(20);
        assertTrue(ctx.reverseWalk(worm));
        ctx.deactivate(worm);
        ctx.deactivate(worm);

        assertEquals(Direction.EAST, worm.moveDirection());
        assertEquals(12, worm.moveCounter());
        assertEquals(Direction.WEST, worm.facing(), "a reversal keeps the facing");
        assertTrue(worm.isDeactivated());
        assertEquals(List.of(new SimEvent.Turned(0, 3, Direction.WEST),
                new SimEvent.MoveStarted(0, 3, pos(1, 0, 0), pos(0, 0, 0)), new SimEvent.MoveReversed(0, 3),
                new SimEvent.Deactivated(0, 3)), out);
    }

    @Test
    void startTurnSetsDirectionFacingAndClassTimer() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.WAT));
        SimObject watcher = world.object(3);

        ctx.startTurn(watcher, Direction.WEST, 4);
        assertEquals(4, watcher.classTimer());
        ctx.setClassTimer(watcher, 3);

        assertEquals(Direction.WEST, watcher.moveDirection());
        assertEquals(Direction.WEST, watcher.facing());
        assertEquals(3, watcher.classTimer());
        assertEquals(List.of(new SimEvent.TurnStarted(0, 3, Direction.WEST, 4)), out);
    }

    @Test
    void startIdleStartsAWaitThatKeepsTheDirection() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.HOP));
        SimObject hopper = world.object(3);

        ctx.startIdle(hopper, 16);

        assertEquals(MoveKind.IDLE, hopper.moveKind());
        assertEquals(16, hopper.moveCounter());
        assertEquals(Direction.NORTH, hopper.moveDirection());
        assertEquals(List.of(new SimEvent.Idle(0, 3)), out);
    }

    @Test
    void setMarkStoresAndClearsTheLizardMark() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.LZE));
        SimObject lizard = world.object(3);
        assertNull(lizard.markedCell());

        ctx.setMark(lizard, pos(0, 0, 0));
        assertEquals(pos(0, 0, 0), lizard.markedCell());
        ctx.setMark(lizard, null);

        assertNull(lizard.markedCell());
        assertTrue(out.isEmpty());
    }

    @Test
    void explodeAndSweepDelegateToTheCore() {
        // F (0..2,0), box (3,0), F (4,0): mine (0,0) slot 3, wiper (1,0) slot 4; no spiders
        start(TestTowers.tower().level(0, "FFFBF").object(0, 0, 0, ObjectType.PRM).object(1, 0, 0, ObjectType.WIP));
        SimObject mine = world.object(3);
        SimObject wiper = world.object(4);

        ctx.sweep(wiper, Direction.EAST);
        ctx.explode(mine, true);

        assertEquals(List.of(new SimEvent.WiperSweep(0, 4, Direction.EAST, List.of(pos(2, 0, 0))),
                new SimEvent.FloorEaten(0, pos(2, 0, 0)),
                new SimEvent.Exploded(0, 3, List.of(pos(-1, 0, 0), pos(0, -1, 0), pos(1, 0, 0), pos(0, 1, 0))),
                new SimEvent.Destroyed(0, 4, pos(1, 0, 0)), new SimEvent.Destroyed(0, 3, pos(0, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_SPIDERS_DESTROYED, 21)), out);
        assertEquals(ObjectFlags.DESTROYS_FLOOR, mine.objectFlags());
        assertEquals(-1, world.freezeCounter(), "READY");
    }

    @Test
    void startPushClaimsTheCellBeyond() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.BLK));
        SimObject block = world.object(3);

        assertTrue(ctx.startPush(block, Direction.EAST));
        assertFalse(ctx.startPush(block, Direction.EAST), "already moving");

        assertEquals(List.of(new SimEvent.PushStarted(0, 3, pos(0, 0, 0), pos(1, 0, 0))), out);
        assertEquals(3, world.occupancy().holder(pos(1, 0, 0)));
    }
}
