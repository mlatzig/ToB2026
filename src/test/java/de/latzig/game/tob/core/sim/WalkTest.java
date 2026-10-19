package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

class WalkTest {

    private final List<SimEvent> out = new ArrayList<>();

    private static WorldState world(TestTowers.Builder tower) {
        return WorldState.load(tower.build(), SimOptions.DEFAULT);
    }

    private static WorldState row() {
        return world(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH));
    }

    private void advance(WorldState world, SimObject mover, int frames) {
        for (int i = 0; i < frames; i++) {
            Walk.advance(world, mover, out);
        }
    }

    @Test
    void aStepTakes32FramesWithClaimHalfWayAndReleaseAtTheOracleFrames() {
        // oracle §1.4 / T-01: claim after 10 frames, half-way after 16, release after 22, standing after 32
        WorldState world = world(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP));
        SimObject zap = world.spider(SpiderId.ZAPPER);

        assertTrue(Walk.start(world, zap, Direction.NORTH, out));
        assertEquals(Walk.FRAMES, zap.moveCounter());
        for (int step = 1; step <= 32; step++) {
            Walk.advance(world, zap, out);
            assertEquals(step < 16 ? pos(0, 0, 0) : pos(0, 1, 0), zap.cell(), "cell after step " + step);
            assertEquals(step >= 10 ? 0 : Occupancy.FREE, world.occupancy().holder(pos(0, 1, 0)),
                    "target after step " + step);
            assertEquals(step < 22 ? 0 : Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)),
                    "origin after step " + step);
            assertEquals(step < 32, zap.isMoving(), "moving after step " + step);
        }
        assertEquals(10 * SimObject.UNITS_PER_CELL, zap.worldZ());
        assertEquals(List.of(SimEvent.MoveStarted.class, SimEvent.MoveClaimed.class, SimEvent.CellChanged.class,
                SimEvent.MoveFinished.class), out.stream().map(Object::getClass).toList());
        assertEquals(new SimEvent.MoveStarted(0, 0, pos(0, 0, 0), pos(0, 1, 0)), out.get(0));
        assertEquals(new SimEvent.MoveClaimed(0, 0, pos(0, 1, 0)), out.get(1));
        assertEquals(new SimEvent.CellChanged(0, 0, pos(0, 0, 0), pos(0, 1, 0)), out.get(2));
        assertEquals(new SimEvent.MoveFinished(0, 0, pos(0, 1, 0)), out.get(3));
    }

    @Test
    void aStepNeedsAStandingMoverAndAWalkableUnheldTarget() {
        // z = 1: F . . .   z = 0: F B F F   with a block on (3,0)
        WorldState world = world(TestTowers.tower().level(0, "F...", "FBFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).object(3, 0, 0, ObjectType.BLK));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        SimObject psh = world.spider(SpiderId.PUSHER);

        assertFalse(Walk.start(world, zap, Direction.EAST, out), "box");
        assertFalse(Walk.start(world, psh, Direction.NORTH, out), "void");
        assertFalse(Walk.start(world, psh, Direction.EAST, out), "held by the block");
        assertFalse(Walk.start(world, zap, Direction.SOUTH, out), "outside the tower");
        assertTrue(Walk.start(world, zap, Direction.NORTH, out));
        assertFalse(Walk.start(world, zap, Direction.NORTH, out), "already moving");
        assertEquals(1, out.size());
    }

    @Test
    void startingDoesNotClaimButAClaimBlocksLaterStarts() {
        WorldState shared = row();
        assertTrue(Walk.start(shared, shared.spider(SpiderId.ZAPPER), Direction.EAST, out));
        assertTrue(Walk.start(shared, shared.spider(SpiderId.PUSHER), Direction.WEST, out));
        assertEquals(Occupancy.FREE, shared.occupancy().holder(pos(1, 0, 0)));

        WorldState claimed = row();
        SimObject zap = claimed.spider(SpiderId.ZAPPER);
        Walk.start(claimed, zap, Direction.EAST, out);
        advance(claimed, zap, 10);
        assertEquals(0, claimed.occupancy().holder(pos(1, 0, 0)));
        assertFalse(Walk.start(claimed, claimed.spider(SpiderId.PUSHER), Direction.WEST, out));
    }

    @Test
    void aLaterClaimerFindsTheCellTakenAndWalksBack() {
        WorldState world = row();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        SimObject psh = world.spider(SpiderId.PUSHER);
        Walk.start(world, zap, Direction.EAST, out);
        advance(world, zap, 2);
        Walk.start(world, psh, Direction.WEST, out);
        for (int frame = 0; frame < 10; frame++) {
            Walk.advance(world, zap, out);
            Walk.advance(world, psh, out);
        }

        assertTrue(out.contains(new SimEvent.MoveReversed(0, 1)));
        assertEquals(10, psh.moveCounter());
        assertEquals(Direction.EAST, psh.moveDirection());
        assertEquals(Direction.NORTH, psh.facing());
        advance(world, psh, 10);
        assertFalse(psh.isMoving());
        assertEquals(pos(2, 0, 0), psh.cell());
        assertEquals(6 * SimObject.UNITS_PER_CELL, psh.worldX());
        assertEquals(1, world.occupancy().holder(pos(2, 0, 0)));
        assertEquals(0, world.occupancy().holder(pos(1, 0, 0)));
    }

    @Test
    void inTheSameFrameTheMoverAdvancedFirstWins() {
        for (boolean zapperFirst : new boolean[] {true, false}) {
            WorldState world = row();
            SimObject zap = world.spider(SpiderId.ZAPPER);
            SimObject psh = world.spider(SpiderId.PUSHER);
            Walk.start(world, zap, Direction.EAST, out);
            Walk.start(world, psh, Direction.WEST, out);
            for (int frame = 0; frame < 10; frame++) {
                Walk.advance(world, zapperFirst ? zap : psh, out);
                Walk.advance(world, zapperFirst ? psh : zap, out);
            }
            assertEquals(zapperFirst ? 0 : 1, world.occupancy().holder(pos(1, 0, 0)));
            assertEquals(10, (zapperFirst ? psh : zap).moveCounter());
        }
    }

    @Test
    void reverseIsRefusedOnlyAtTheHalfWayReleaseAndClaimCounters() {
        for (int frames = 1; frames < Walk.FRAMES; frames++) {
            WorldState world = row();
            SimObject zap = world.spider(SpiderId.ZAPPER);
            Walk.start(world, zap, Direction.EAST, out);
            advance(world, zap, frames);
            int counter = Walk.FRAMES - frames;
            boolean refused = counter == 16 || counter == 10 || counter == 22;
            assertEquals(!refused, Walk.reverse(world, zap, out), "counter " + counter);
        }
        WorldState standing = row();
        assertFalse(Walk.reverse(standing, standing.spider(SpiderId.ZAPPER), out));
    }

    @Test
    void reversingAfterHalfWayWalksBackAndReleasesTheTarget() {
        WorldState world = row();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        Walk.start(world, zap, Direction.EAST, out);
        advance(world, zap, 20);
        assertEquals(pos(1, 0, 0), zap.cell());

        assertTrue(Walk.reverse(world, zap, out));
        assertEquals(20, zap.moveCounter());
        assertEquals(Direction.WEST, zap.moveDirection());
        advance(world, zap, 4);
        assertEquals(pos(0, 0, 0), zap.cell());
        assertEquals(pos(1, 0, 0), zap.secondCell());
        advance(world, zap, 6);
        assertNull(zap.secondCell());
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
        advance(world, zap, 10);
        assertFalse(zap.isMoving());
        assertEquals(pos(0, 0, 0), zap.cell());
        assertEquals(0, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(4 * SimObject.UNITS_PER_CELL, zap.worldX());
    }

    @Test
    void reversingBeforeTheClaimNeedsNoBookkeeping() {
        WorldState world = row();
        SimObject zap = world.spider(SpiderId.ZAPPER);
        Walk.start(world, zap, Direction.EAST, out);
        advance(world, zap, 5);
        assertTrue(Walk.reverse(world, zap, out));
        assertEquals(5, zap.moveCounter());
        advance(world, zap, 5);

        assertFalse(zap.isMoving());
        assertEquals(pos(0, 0, 0), zap.cell());
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
        assertTrue(out.stream().noneMatch(SimEvent.MoveClaimed.class::isInstance));
    }

    @Test
    void claimValuesFollowTheOriginalTable() {
        assertEquals(10, Walk.claimValue(ObjectType.ZAP));
        assertEquals(10, Walk.claimValue(ObjectType.PSH));
        assertEquals(10, Walk.claimValue(ObjectType.GRB));
        assertEquals(11, Walk.claimValue(ObjectType.WAT));
        assertEquals(12, Walk.claimValue(ObjectType.HOP));
        assertEquals(2, Walk.claimValue(ObjectType.WON));
        assertEquals(2, Walk.claimValue(ObjectType.WOE));
        assertEquals(2, Walk.claimValue(ObjectType.LZN));
        assertEquals(2, Walk.claimValue(ObjectType.LZE));
        assertThrows(IllegalArgumentException.class, () -> Walk.claimValue(ObjectType.BLK));
    }
}
