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

class PushTest {

    private final List<SimEvent> out = new ArrayList<>();

    private static WorldState world(TestTowers.Builder tower) {
        return WorldState.load(tower.build(), SimOptions.DEFAULT);
    }

    @Test
    void theEaseTableIsTheOriginalOneAndAddsUpToOneCell() {
        int[] expected = {42, 64, 64, 64, 62, 54, 46, 38, 30, 22, 14, 8, 4, 0, 0, 0};
        int sum = 0;
        for (int counter = Push.FRAMES; counter >= 1; counter--) {
            assertEquals(expected[Push.FRAMES - counter], Push.ease(counter), "counter " + counter);
            sum += Push.ease(counter);
        }
        assertEquals(SimObject.UNITS_PER_CELL, sum);
    }

    @Test
    void aPushClaimsAtOnceSwitchesAtHalfWayAndReleasesAtTheEnd() {
        WorldState world = world(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.BLK));
        SimObject block = world.object(3);

        assertTrue(Push.start(world, block, Direction.EAST, out));
        assertEquals(List.of(new SimEvent.PushStarted(0, 3, pos(0, 0, 0), pos(1, 0, 0))), out);
        assertEquals(MoveKind.PUSH, block.moveKind());
        assertEquals(Push.FRAMES, block.moveCounter());
        assertEquals(3, world.occupancy().holder(pos(1, 0, 0)), "claimed at fire time");
        int startX = block.worldX();
        for (int step = 1; step <= Push.FRAMES; step++) {
            Push.advance(world, block, out);
            assertEquals(step < 8 ? pos(0, 0, 0) : pos(1, 0, 0), block.cell(), "cell after step " + step);
            assertEquals(step < 16 ? 3 : Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)),
                    "old cell after step " + step);
            assertEquals(3, world.occupancy().holder(pos(1, 0, 0)));
            assertEquals(List.of(), world.occupancyProblems(), "step " + step);
        }
        assertFalse(block.isMoving());
        assertNull(block.secondCell());
        assertEquals(MoveKind.PUSH, block.moveKind(), "+0E keeps the push (S:10062-10066)");
        assertEquals(startX + SimObject.UNITS_PER_CELL, block.worldX());
        assertEquals(List.of(new SimEvent.PushStarted(0, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.CellChanged(0, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.PushFinished(0, 3, pos(1, 0, 0))), out);
    }

    @Test
    void aPushNeedsAStandingTargetAndAFreeFloorBeyond() {
        // z = 1: . F F F
        // z = 0: F B F F .   (B = box) with blocks at (0,0), (2,0), (3,0) and a zapper at (2,1)
        WorldState world = world(TestTowers.tower().level(0, ".FFF", "FBFF.").object(0, 0, 0, ObjectType.BLK)
                .object(2, 0, 0, ObjectType.BLK).object(3, 0, 0, ObjectType.BLK).object(2, 1, 0, ObjectType.ZAP));
        SimObject west = world.object(3);
        SimObject middle = world.object(4);
        SimObject east = world.object(5);

        assertFalse(Push.start(world, west, Direction.EAST, out), "box beyond");
        assertFalse(Push.start(world, west, Direction.NORTH, out), "void beyond");
        assertFalse(Push.start(world, middle, Direction.EAST, out), "occupied beyond");
        assertFalse(Push.start(world, middle, Direction.NORTH, out), "occupied by the zapper");
        assertFalse(Push.start(world, east, Direction.EAST, out), "void beyond");
        assertFalse(Push.start(world, west, Direction.WEST, out), "outside the original tower: void");
        assertTrue(Push.start(world, east, Direction.NORTH, out));
        assertFalse(Push.start(world, east, Direction.NORTH, out), "already moving");
        assertFalse(Push.start(world, world.spider(SpiderId.ZAPPER), Direction.EAST, out), "claimed by the push");
        assertEquals(1, out.size());
    }

    @Test
    void aWalkerReachingItsClaimFrameAtAPushClaimedCellReverses() {
        // the pusher's ray claims (2,0) for the block before the zapper's claim frame (oracle §1.5 rule 4)
        WorldState world = world(TestTowers.tower().level(0, "FFFF").object(1, 0, 0, ObjectType.BLK)
                .object(3, 0, 0, ObjectType.ZAP));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        SimObject block = world.object(3);
        assertTrue(Walk.start(world, zap, Direction.WEST, out));
        for (int frame = 0; frame < 5; frame++) {
            Walk.advance(world, zap, out);
        }

        assertTrue(Push.start(world, block, Direction.EAST, out));
        for (int frame = 0; frame < 5; frame++) {
            Walk.advance(world, zap, out);
            Push.advance(world, block, out);
        }

        assertTrue(out.contains(new SimEvent.MoveReversed(0, 0)));
        assertEquals(Direction.EAST, zap.moveDirection());
        assertEquals(3, world.occupancy().holder(pos(2, 0, 0)));
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void aPushedSpiderTurnsAndFiresButCannotMoveOrReverseAndMovesWhileFrozen() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.BLK).build());
        WorldState world = run.sim.world();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        SimObject block = world.object(3);
        List<SimEvent> pushes = new ArrayList<>();
        assertTrue(Push.start(world, zap, Direction.EAST, pushes));
        assertFalse(Push.start(world, block, Direction.WEST, pushes), "(1,0) is claimed by the zapper's push");

        run.step();
        assertTrue(world.isReady());
        assertEquals(Push.FRAMES - 1, zap.moveCounter(), "spiders move even in a frozen READY frame");
        assertEquals(List.of(new SimEvent.Turned(0, 0, Direction.WEST)),
                run.step(new Command.TurnLeft(SpiderId.ZAPPER)));
        assertEquals(List.of(), run.step(new Command.Back(SpiderId.ZAPPER)).stream()
                .filter(event -> !(event instanceof SimEvent.ReadyEnded)).toList(), "no reversal of a push");
        for (int i = 0; i < 4; i++) {
            run.step();
        }
        List<SimEvent> forward = run.step(new Command.Forward(SpiderId.ZAPPER));
        assertTrue(forward.stream().noneMatch(SimEvent.MoveStarted.class::isInstance), "no move while pushed");
        assertTrue(run.step(new Command.Fire(SpiderId.ZAPPER)).stream()
                .anyMatch(SimEvent.SpiderFired.class::isInstance));
        run.runUntilFrame(20);
        assertEquals(pos(1, 0, 0), zap.cell());
        assertFalse(zap.isMoving());
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void aTargetThatIsNotAliveIsNeverPushed() {
        // 2b carry-over M5: F (0..2,0) with a block being destroyed at (0,0) (slot 3) and a removed one at (2,0) (slot 4)
        WorldState world = world(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.BLK)
                .object(2, 0, 0, ObjectType.BLK));
        SimObject destroying = world.object(3);
        SimObject removed = world.object(4);
        Rules.destroy(world, destroying, out);
        Rules.remove(world, removed, out);
        out.clear();

        assertFalse(Push.start(world, destroying, Direction.EAST, out));
        assertFalse(Push.start(world, removed, Direction.WEST, out));

        assertEquals(List.of(), out);
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
    }

    @Test
    void aPushedObjectWaitsWhileTheWorldIsFrozen() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.BLK).build());
        SimObject block = run.sim.world().object(3);
        assertTrue(Push.start(run.sim.world(), block, Direction.EAST, new ArrayList<>()));

        run.step();
        run.step();
        assertEquals(Push.FRAMES, block.moveCounter(), "frozen in READY");
        run.step(new Command.Fire(SpiderId.ZAPPER));
        assertEquals(Push.FRAMES, block.moveCounter(), "READY ends after the movement step of this frame");
        run.step();
        assertEquals(Push.FRAMES - 1, block.moveCounter());
    }
}
