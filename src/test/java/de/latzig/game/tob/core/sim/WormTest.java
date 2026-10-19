package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.WormBehaviour;

/** Phase 2c spec §3.1: the worm (class 5, {@code L00B2CC}, {@code S:9463-9486}) and its zap and push reactions. */
class WormTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;

    /** F (0..2,0) with a worm E/W at (1,0) (slot 3) and a zapper at (0,0); thawed, as in a normal frame. */
    private static SimContext context(List<SimEvent> out) {
        WorldState world = WorldState.load(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.WOE)
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        SimContext ctx = new SimContext(world);
        ctx.begin(out);
        ctx.thaw();
        return ctx;
    }

    @Test
    void aWormWalksOnAtOnceClaimsAfterTwoFramesAndReleasesTwoFramesBeforeTheEnd() {
        // F (0..2,0); worm E/W at (0,0) (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.WOE));
        WorldState world = run.sim.world();
        run.runUntilFrame(30);
        assertEquals(3, world.occupancy().holder(pos(0, 0, 0)));
        run.step();
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)), "released at counter 2 (frame 31)");
        run.runUntilFrame(66);

        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveClaimed(3, 3, pos(1, 0, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveFinished(33, 3, pos(1, 0, 0)),
                new SimEvent.MoveStarted(33, 3, pos(1, 0, 0), pos(2, 0, 0)),
                new SimEvent.MoveClaimed(35, 3, pos(2, 0, 0)),
                new SimEvent.CellChanged(49, 3, pos(1, 0, 0), pos(2, 0, 0)),
                new SimEvent.MoveFinished(65, 3, pos(2, 0, 0)),
                new SimEvent.Turned(65, 3, Direction.WEST),
                new SimEvent.MoveStarted(66, 3, pos(2, 0, 0), pos(1, 0, 0))), run.eventsOf(3));
    }

    @Test
    void aWormBlockedOnBothSidesFlipsItsDirectionEveryFrame() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.WOE));
        run.runUntilFrame(4);

        assertEquals(List.of(new SimEvent.Turned(1, 3, Direction.WEST), new SimEvent.Turned(2, 3, Direction.EAST),
                new SimEvent.Turned(3, 3, Direction.WEST), new SimEvent.Turned(4, 3, Direction.EAST)),
                run.eventsOf(3));
        assertEquals(Direction.EAST, run.sim.world().object(3).moveDirection());
    }

    @Test
    void aZapReversesAWalkingWormUnlessItsCounterIsTwoSixteenOrThirty() {
        for (int counter = 1; counter <= Walk.FRAMES; counter++) {
            List<SimEvent> out = new ArrayList<>();
            SimContext ctx = context(out);
            SimObject worm = ctx.world().object(3);
            assertTrue(ctx.tryStep(worm, Direction.EAST));
            worm.setMoveCounter(counter);
            boolean absorbed = counter == 2 || counter == 16 || counter == 30;

            boolean hit = WormBehaviour.INSTANCE.onZap(ctx, worm, ctx.world().spider(Z));

            assertEquals(!absorbed, hit, "counter " + counter);
            assertEquals(absorbed ? counter : Walk.FRAMES - counter, worm.moveCounter(), "counter " + counter);
            assertEquals(absorbed ? Direction.EAST : Direction.WEST, worm.moveDirection(), "counter " + counter);
            assertEquals(absorbed ? Direction.EAST : Direction.WEST, worm.facing(), "counter " + counter);
        }
    }

    @Test
    void aStandingWormAndAWormBeingPushedAbsorbTheZap() {
        List<SimEvent> out = new ArrayList<>();
        SimContext ctx = context(out);
        WorldState world = ctx.world();
        SimObject worm = world.object(3);
        assertFalse(WormBehaviour.INSTANCE.onZap(ctx, worm, world.spider(Z)), "standing");
        assertTrue(ctx.startPush(worm, Direction.EAST));
        for (int frame = 0; frame < 9; frame++) {
            Push.advance(world, worm, out);
        }
        assertEquals(7, worm.moveCounter());

        assertFalse(WormBehaviour.INSTANCE.onZap(ctx, worm, world.spider(Z)), "deviation 3: being pushed");

        assertEquals(7, worm.moveCounter());
        assertEquals(MoveKind.PUSH, worm.moveKind());
        assertEquals(Direction.EAST, worm.moveDirection());
    }

    @Test
    void aPushInABlockedFrameDeactivatesTheWormForever() {
        // z=3,2: the pusher's column (1,3),(1,2); z=1: box, worm E/W (slot 3), box; z=0: F F F
        SimRun run = new SimRun(TestTowers.tower().level(0, ".F", ".F", "BFB", "FFF").object(1, 1, 0, ObjectType.WOE)
                .object(1, 3, 0, ObjectType.PSH).object(7, 7, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnRight(P));
        run.turnInReady(new Command.TurnRight(P));
        run.step(new Command.Fire(Z));
        run.runUntilFrame(4);

        List<SimEvent> push = run.step(new Command.Fire(P));
        run.runUntilFrame(100);

        SimObject worm = run.sim.world().object(3);
        assertTrue(push.contains(new SimEvent.RayHit(5, 1, 3, RayOutcome.PUSHED)));
        assertEquals(List.of(new SimEvent.Turned(1, 3, Direction.WEST), new SimEvent.Turned(2, 3, Direction.EAST),
                new SimEvent.Turned(3, 3, Direction.WEST), new SimEvent.Turned(4, 3, Direction.EAST),
                new SimEvent.PushStarted(5, 3, pos(1, 1, 0), pos(1, 0, 0)), new SimEvent.Deactivated(5, 3),
                new SimEvent.CellChanged(12, 3, pos(1, 1, 0), pos(1, 0, 0)),
                new SimEvent.PushFinished(20, 3, pos(1, 0, 0))), run.eventsOf(3));
        assertTrue(worm.isDeactivated());
        assertEquals(pos(1, 0, 0), worm.cell());
        assertEquals(List.of(), run.sim.world().occupancyProblems());
    }
}
