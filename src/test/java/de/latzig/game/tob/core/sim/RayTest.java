package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Ray tracing (oracle §3.1–§3.4), driven directly on a world. Slots: 0–2 spiders, then list order. */
class RayTest {

    private final List<SimEvent> out = new ArrayList<>();
    private WorldState world;
    private SimContext ctx;

    private void start(TestTowers.Builder tower) {
        out.clear();
        world = WorldState.load(tower.build(), SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
    }

    private boolean fire(int slot, Direction direction, RayType type) {
        return Ray.fire(ctx, world.object(slot), direction, type);
    }

    private List<RayStep> path() {
        List<SimEvent.RayFired> fired = out.stream().filter(SimEvent.RayFired.class::isInstance)
                .map(SimEvent.RayFired.class::cast).toList();
        return fired.get(fired.size() - 1).path();
    }

    private List<CellPos> cells() {
        return path().stream().map(RayStep::cell).toList();
    }

    private List<SimEvent.RayHit> hits() {
        return out.stream().filter(SimEvent.RayHit.class::isInstance).map(SimEvent.RayHit.class::cast).toList();
    }

    private void walk(SimObject mover, Direction direction, int frames) {
        assertTrue(Walk.start(world, mover, direction, out));
        for (int i = 0; i < frames; i++) {
            Walk.advance(world, mover, out);
        }
        out.clear();
    }

    @Test
    void boxesStopARayButVoidAndLiftTopStopsDoNot() {
        // F . u F B F with a flag behind the box
        start(TestTowers.tower().level(0, "F.uFBF").object(0, 0, 0, ObjectType.ZAP).object(5, 0, 0, ObjectType.BFL));

        assertFalse(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0), pos(3, 0, 0), pos(4, 0, 0)), cells());
        assertEquals(List.of(), hits());
        assertEquals(ObjectStatus.ALIVE, world.object(3).status());
        assertEquals(List.of(new SimEvent.RayFired(0, 0, RayType.ZAP, path())), out);
    }

    @Test
    void theGridEdgeStopsARayAndTheRayStartsOneCellAhead() {
        start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP));

        assertFalse(fire(0, Direction.SOUTH, RayType.ZAP));

        // (4,11) is the zapper; south of it the grid has rows 12..15
        assertEquals(List.of(new CellPos(4, 12, 0), new CellPos(4, 13, 0), new CellPos(4, 14, 0),
                new CellPos(4, 15, 0)), cells());
        assertTrue(path().stream().allMatch(step -> step.direction() == Direction.SOUTH && step.type() == RayType.ZAP));
    }

    @Test
    void theEmitterAndObjectsBeingDestroyedArePassed() {
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.BFL)
                .object(2, 0, 0, ObjectType.BFL));
        Rules.destroy(world, world.object(3), out);
        out.clear();

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(new SimEvent.RayHit(0, 0, 4, RayOutcome.DESTROYED)), hits());
        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0)), cells());
    }

    @Test
    void aWalkingZapperPassesItsOwnClaimedCell() {
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).object(3, 0, 0, ObjectType.BFL));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        walk(zap, Direction.EAST, 10);
        assertEquals(pos(1, 0, 0), zap.secondCell());

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(ObjectStatus.DESTROYING, world.object(3).status());
    }

    @Test
    void theGrabRayHasNoOwnSlotTestSoAWalkingGrabberAbsorbsItsOwnRay() {
        // S:8917-8936: the grab loop never compares the slot with the emitter's (the zap and push loops do)
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.GRB).object(3, 0, 0, ObjectType.EXC));
        SimObject grabber = world.spider(SpiderId.GRABBER);
        walk(grabber, Direction.EAST, 10);

        assertFalse(fire(2, Direction.EAST, RayType.GRAB));

        assertEquals(List.of(new SimEvent.RayHit(0, 2, 2, RayOutcome.ABSORBED)), hits());
        assertEquals(List.of(pos(1, 0, 0)), cells());
    }

    @Test
    void aZapReachesAWalkerOnlyInItsCurrentCell() {
        // pusher at (1,1) walks south into (1,0); the zap runs along row 0 to a flag at (3,0)
        start(TestTowers.tower().level(0, ".F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 1, 0, ObjectType.PSH).object(3, 0, 0, ObjectType.BFL));
        SimObject pusher = world.spider(SpiderId.PUSHER);
        walk(pusher, Direction.SOUTH, 10);
        assertEquals(pos(1, 0, 0), pusher.secondCell(), "claimed");

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.DESTROYED)), hits(), "claimed cell transparent");

        for (int i = 0; i < 6; i++) {
            Walk.advance(world, pusher, out);
        }
        assertEquals(pos(1, 0, 0), pusher.cell(), "half-way");
        out.clear();
        assertTrue(fire(0, Direction.EAST, RayType.ZAP));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 1, RayOutcome.DESTROYED)), hits());
    }

    @Test
    void aZapPassesTheOldCellOfAWalkerUntilItIsReleased() {
        // pusher at (1,0) walks north into (1,1); after half-way (1,0) is its old cell until counter 10
        start(TestTowers.tower().level(0, ".F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.PSH).object(3, 0, 0, ObjectType.BFL));
        SimObject pusher = world.spider(SpiderId.PUSHER);
        walk(pusher, Direction.NORTH, 17);
        assertEquals(pos(1, 0, 0), pusher.secondCell(), "old cell, not yet released");

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.DESTROYED)), hits());
        assertEquals(ObjectStatus.ALIVE, pusher.status());
    }

    @Test
    void pushAndGrabRaysTakeTheFirstHolderClaimedOrNot() {
        start(TestTowers.tower().level(0, ".F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 1, 0, ObjectType.PSH).object(3, 0, 0, ObjectType.KLD));
        SimObject pusher = world.spider(SpiderId.PUSHER);
        walk(pusher, Direction.SOUTH, 10);

        assertFalse(fire(0, Direction.EAST, RayType.PUSH));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 1, RayOutcome.ABSORBED)), hits(), "moving: not pushed");
        out.clear();
        assertFalse(fire(0, Direction.EAST, RayType.GRAB));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 1, RayOutcome.ABSORBED)), hits(), "no grab bit");
    }

    @Test
    void aLiftRiderBlocksAZapAndIsImmune() {
        start(TestTowers.tower().level(0, "FFUFF").level(1, "..u").object(2, 0, 0, ObjectType.ZAP)
                .object(0, 0, 0, ObjectType.FZE).object(4, 0, 0, ObjectType.BFL));
        SimObject rider = world.spider(SpiderId.ZAPPER);
        assertTrue(LiftRide.start(world, rider, out));
        out.clear();

        assertFalse(fire(3, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(new SimEvent.RayHit(0, 3, 0, RayOutcome.BLOCKED)), hits());
        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0)), cells());
        assertEquals(ObjectStatus.ALIVE, rider.status());
        assertEquals(ObjectStatus.ALIVE, world.object(4).status());
        out.clear();
        assertFalse(fire(3, Direction.EAST, RayType.PUSH));
        assertEquals(List.of(new SimEvent.RayHit(0, 3, 0, RayOutcome.ABSORBED)), hits(), "pushable but moving");
    }

    @Test
    void aReflectedZapCanHitItsEmitter() {
        start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP).object(2, 0, 0, ObjectType.REL));

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0), pos(1, 0, 0), pos(0, 0, 0)), cells());
        assertEquals(List.of(Direction.EAST, Direction.EAST, Direction.WEST, Direction.WEST),
                path().stream().map(RayStep::direction).toList());
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.REFLECTED),
                new SimEvent.RayHit(0, 0, 0, RayOutcome.DESTROYED)), hits());
        assertEquals(ObjectStatus.DESTROYING, world.spider(SpiderId.ZAPPER).status());
    }

    @Test
    void theEventsComeInTraceOrderWithTheLastHitAfterTheEffect() {
        start(TestTowers.tower().level(0, "...F", "...F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(3, 0, 0, ObjectType.PSE).object(3, 2, 0, ObjectType.BFL).conditions(0, false, 0, 1));

        fire(0, Direction.EAST, RayType.ZAP);

        assertEquals(List.of(new SimEvent.RayFired(0, 0, RayType.ZAP, path()),
                new SimEvent.RayHit(0, 0, 3, RayOutcome.DEFLECTED),
                new SimEvent.Destroyed(0, 4, pos(3, 2, 0)),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1),
                new SimEvent.ExitPending(0, Rules.EXIT_COMPLETED, 21),
                new SimEvent.RayHit(0, 0, 4, RayOutcome.DESTROYED)), out);
    }

    @Test
    void theRangeEndsAfterSixteenCellsAcrossDeflections() {
        // spec §5 asks for two facing reflectors, but every reflected ray retraces its path to the emitter, which is
        // hittable after the reflection; so the round trip zapper → prism → reflector → prism → zapper is measured:
        // 16 cells reach the zapper, 18 do not
        for (int reflectorZ : new int[] {1, 2}) {
            start(TestTowers.tower().level(0, ".......F", ".......F", "FFFFFFFF").object(0, 0, 0, ObjectType.ZAP)
                    .object(7, 0, 0, ObjectType.PSE).object(7, reflectorZ, 0, ObjectType.REL));

            boolean hit = fire(0, Direction.EAST, RayType.ZAP);

            assertEquals(reflectorZ == 1, hit, "reflector at z = " + reflectorZ);
            assertEquals(16, cells().size());
            assertEquals(reflectorZ == 1 ? pos(0, 0, 0) : pos(2, 0, 0), cells().get(15));
            assertEquals(reflectorZ == 1 ? ObjectStatus.DESTROYING : ObjectStatus.ALIVE,
                    world.spider(SpiderId.ZAPPER).status());
        }
    }

    @Test
    void prismsFollowTheOracleTableForAllFourCodesAndDirections() {
        // oracle §3.3: code -> {ray moving -> leaves moving}; every other direction is absorbed
        Map<ObjectType, Map<Direction, Direction>> table = Map.of(
                ObjectType.PNE, Map.of(Direction.NORTH, Direction.WEST, Direction.EAST, Direction.SOUTH),
                ObjectType.PSE, Map.of(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST),
                ObjectType.PSW, Map.of(Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH),
                ObjectType.PNW, Map.of(Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.EAST));
        table.forEach((type, rule) -> {
            for (Direction moving : Direction.values()) {
                assertEquals(rule.get(moving), Ray.deflect(ObjectDescriptor.direction(type), moving),
                        type.code() + " moving " + moving);
            }
        });
    }

    @Test
    void aPrismBendsAZapAndBecomesTheEmitter() {
        // zapper (0,0) facing E, prism S/E at (3,0) turns E into N, flag at (3,2)
        start(TestTowers.tower().level(0, "...F", "...F", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(3, 0, 0, ObjectType.PSE).object(3, 2, 0, ObjectType.BFL));

        assertTrue(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.DEFLECTED),
                new SimEvent.RayHit(0, 0, 4, RayOutcome.DESTROYED)), hits());
        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0), pos(3, 0, 0), pos(3, 1, 0), pos(3, 2, 0)), cells());
        out.clear();
        assertFalse(fire(0, Direction.EAST, RayType.PUSH), "a push ray is never bent: it pushes the prism");
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), hits(), "no floor beyond");
    }

    @Test
    void aPrismAbsorbsTheOtherDirections() {
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).object(3, 0, 0, ObjectType.PNW));

        assertFalse(fire(0, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), hits());
    }

    @Test
    void convertersTurnParallelRaysIntoTheOtherTypeAndAbsorbTheRest() {
        // pusher (0,0) fires E; converter E/W at (1,0); flag at (3,0)
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.CVE)
                .object(3, 0, 0, ObjectType.BFL));

        assertTrue(fire(1, Direction.EAST, RayType.PUSH), "push -> zap");

        assertEquals(List.of(new SimEvent.RayHit(0, 1, 3, RayOutcome.CONVERTED),
                new SimEvent.RayHit(0, 1, 4, RayOutcome.DESTROYED)), hits());
        assertEquals(List.of(RayType.PUSH, RayType.ZAP, RayType.ZAP), path().stream().map(RayStep::type).toList());

        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.CVN)
                .object(3, 0, 0, ObjectType.BFL));
        assertFalse(fire(0, Direction.EAST, RayType.ZAP), "across the axis");
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), hits());
        assertFalse(Ray.parallel(Direction.NORTH, Direction.EAST));
        assertTrue(Ray.parallel(Direction.NORTH, Direction.SOUTH));
        assertTrue(Ray.parallel(Direction.EAST, Direction.WEST));
    }

    @Test
    void aPushRayClaimsTheCellBeyondAtOnceOrIsAbsorbed() {
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.REL)
                .object(3, 0, 0, ObjectType.ZAP));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        walk(zap, Direction.WEST, 10);
        assertEquals(0, world.occupancy().holder(pos(2, 0, 0)), "the zapper claimed the cell beyond");

        assertFalse(fire(1, Direction.EAST, RayType.PUSH));
        assertEquals(List.of(new SimEvent.RayHit(0, 1, 3, RayOutcome.ABSORBED)), hits(), "beyond is claimed");

        start(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.REL));
        assertTrue(fire(1, Direction.EAST, RayType.PUSH), "a reflector is pushable");
        assertEquals(List.of(new SimEvent.RayFired(0, 1, RayType.PUSH, path()),
                new SimEvent.PushStarted(0, 3, pos(1, 0, 0), pos(2, 0, 0)),
                new SimEvent.RayHit(0, 1, 3, RayOutcome.PUSHED)), out);
        assertEquals(3, world.occupancy().holder(pos(2, 0, 0)));
    }

    @Test
    void objectsWithoutAMatchingBitAbsorbTheRay() {
        start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.FPE)
                .object(2, 0, 0, ObjectType.WOE));

        assertFalse(fire(0, Direction.EAST, RayType.ZAP));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), hits(), "fixed pusher: flags 0");

        out.clear();
        assertFalse(fire(3, Direction.EAST, RayType.ZAP), "worm $0800: the hook is a no-op until Phase 2c");
        assertEquals(List.of(new SimEvent.RayHit(0, 3, 4, RayOutcome.ABSORBED)), hits());
        assertEquals(ObjectStatus.ALIVE, world.object(4).status());

        out.clear();
        assertFalse(fire(0, Direction.EAST, RayType.GRAB));
        assertEquals(List.of(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), hits());
    }
}
