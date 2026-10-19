package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

class WorldStateTest {

    private static WorldState load(TestTowers.Builder tower) {
        return WorldState.load(tower.build(), SimOptions.DEFAULT);
    }

    @Test
    void listOrderIsSpidersThenLevelThenSouthToNorthThenWestToEast() {
        WorldState world = load(TestTowers.tower()
                .level(0, "FFF", "FFF")
                .level(1, "F")
                .object(2, 1, 0, ObjectType.BLK)
                .object(0, 1, 0, ObjectType.HOP)
                .object(1, 0, 0, ObjectType.KLD)
                .object(0, 0, 1, ObjectType.BFL)
                .object(2, 0, 0, ObjectType.GRB));

        assertEquals(List.of(ObjectType.ZAP, ObjectType.PSH, ObjectType.GRB, ObjectType.KLD, ObjectType.HOP,
                ObjectType.BLK, ObjectType.BFL), world.objects().stream().map(SimObject::type).toList());
        for (int slot = 0; slot < world.objects().size(); slot++) {
            assertEquals(slot, world.object(slot).slot());
        }
        assertEquals(ObjectStatus.ABSENT, world.spider(SpiderId.ZAPPER).status());
        assertEquals(ObjectStatus.ABSENT, world.spider(SpiderId.PUSHER).status());
        assertEquals(ObjectStatus.ALIVE, world.spider(SpiderId.GRABBER).status());
        assertEquals(pos(2, 0, 0), world.spider(SpiderId.GRABBER).cell());
        assertEquals(pos(0, 0, 1), world.object(6).cell());
    }

    @Test
    void theStartDirectionIsAlsoTheMoveDirection() {
        // S:17584-17586: the descriptor direction goes into +0C and +0E
        WorldState world = load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.WOE)
                .object(1, 0, 0, ObjectType.LZE).object(2, 0, 0, ObjectType.HOP));

        assertEquals(Direction.EAST, world.object(3).moveDirection());
        assertEquals(Direction.EAST, world.object(4).moveDirection());
        assertEquals(Direction.NORTH, world.object(5).moveDirection());
        assertEquals(MoveKind.NONE, world.object(3).moveKind());
    }

    @Test
    void baseFlagsFollowTheOriginalCellTable() {
        WorldState world = load(TestTowers.tower().level(0, ".FBUd").level(1, "...uD"));

        assertEquals(0, world.flags(pos(0, 0, 0)));
        assertEquals(CellFlags.FLOOR, world.flags(pos(1, 0, 0)));
        assertEquals(CellFlags.FLOOR | CellFlags.BLOCKED, world.flags(pos(2, 0, 0)));
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT, world.flags(pos(3, 0, 0)));
        assertEquals(CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(pos(3, 0, 1)));
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(pos(4, 0, 1)));
        assertEquals(CellFlags.FLOOR | CellFlags.LIFT | CellFlags.BLOCKED, world.flags(pos(4, 0, 0)));
        assertEquals(0, world.flags(new CellPos(-1, 0, 0)));
        assertTrue(world.isWalkable(pos(1, 0, 0)));
        assertFalse(world.isWalkable(pos(2, 0, 0)));
        assertTrue(world.isWalkable(pos(3, 0, 0)));
        assertFalse(world.isWalkable(pos(3, 0, 1)));
        assertTrue(world.isWalkable(pos(4, 0, 1)));
        assertFalse(world.isWalkable(pos(4, 0, 0)));
        assertTrue(world.liftRidesDown(pos(4, 0, 1)));
        assertFalse(world.liftRidesDown(pos(3, 0, 0)));
    }

    @Test
    void liftsArePairedWithTheirPlatformPosition() {
        WorldState world = load(TestTowers.tower().level(0, ".FBUd").level(1, "...uD"));

        assertEquals(2, world.lifts().size());
        Lift up = world.liftAt(pos(3, 0, 0)).orElseThrow();
        assertSame(up, world.liftAt(pos(3, 0, 1)).orElseThrow());
        assertEquals(pos(3, 0, 0), up.lower());
        assertEquals(pos(3, 0, 1), up.upper());
        assertEquals(LiftState.BOTTOM, up.state());
        assertEquals(Occupancy.FREE, up.rider());
        Lift down = world.liftAt(pos(4, 0, 1)).orElseThrow();
        assertEquals(pos(4, 0, 0), down.lower());
        assertEquals(pos(4, 0, 1), down.upper());
        assertEquals(LiftState.TOP, down.state());
        assertTrue(world.liftAt(pos(1, 0, 0)).isEmpty());
    }

    @Test
    void anOrphanStopIsToleratedAsAnInertFlagCell() {
        WorldState world = load(TestTowers.tower().level(0, "F").level(1, "u"));

        assertTrue(world.lifts().isEmpty());
        assertEquals(CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(pos(0, 0, 1)));
        assertFalse(world.isWalkable(pos(0, 0, 1)));
    }

    @Test
    void aLiftStartWithoutItsStopIsRejected() {
        IllegalArgumentException up = assertThrows(IllegalArgumentException.class,
                () -> load(TestTowers.tower().level(0, "U")));
        assertTrue(up.getMessage().contains("no up stop"), up.getMessage());
        IllegalArgumentException down = assertThrows(IllegalArgumentException.class,
                () -> load(TestTowers.tower().level(0, "F").level(1, "D")));
        assertTrue(down.getMessage().contains("no down stop"), down.getMessage());
    }

    @Test
    void occupancyHoldsExactlyTheObjectCells() {
        WorldState world = load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.BLK));

        assertEquals(0, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(3, world.occupancy().holder(pos(1, 0, 0)));
        assertTrue(world.occupancy().isFree(pos(2, 0, 0)));
        assertFalse(world.occupancy().isFree(new CellPos(16, 0, 0)));
        assertEquals(2, world.occupancy().heldCells().size());
    }

    @Test
    void theStartStateComesFromTheConditions() {
        WorldState limited = load(TestTowers.tower().conditions(30, true, 2, 3));

        assertEquals(30, limited.timeLeft());
        assertEquals(2, limited.counters().klondikesRequired());
        assertEquals(3, limited.counters().robotsRequired());
        assertEquals(0, limited.counters().klondikesCollected());
        assertEquals(0, limited.counters().robotsDestroyed());
        assertEquals(0, limited.frame());
        assertEquals(0, limited.clockPhase());
        assertTrue(limited.isReady());
        assertTrue(limited.isFrozen());
        assertEquals(0, limited.exitCode());
        assertFalse(limited.isExitPending());
        assertFalse(limited.isFinished());
        assertEquals(0, load(TestTowers.tower().conditions(30, false, 0, 0)).timeLeft());
    }

    @Test
    void spidersFaceNorthAndObjectsStartAtTheirCell() {
        WorldState world = load(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.FZE));

        SimObject zap = world.spider(SpiderId.ZAPPER);
        assertEquals(Direction.NORTH, zap.facing());
        assertEquals(Direction.EAST, world.object(3).facing());
        assertEquals(4 * SimObject.UNITS_PER_CELL, zap.worldX());
        assertEquals(0, zap.worldY());
        assertEquals(11 * SimObject.UNITS_PER_CELL, zap.worldZ());
        assertFalse(zap.isMoving());
    }

    @Test
    void objectsStartWithTheDescriptorFlagsAndDirection() {
        WorldState world = load(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.PSE).object(2, 0, 0, ObjectType.CVE).object(3, 0, 0, ObjectType.KLD));

        assertEquals(0x0005, world.spider(SpiderId.ZAPPER).objectFlags());
        assertEquals(Direction.EAST, world.object(3).facing(), "prism S/E is code 26, p = 1");
        assertEquals(0x0024, world.object(3).objectFlags());
        assertEquals(Direction.EAST, world.object(4).facing(), "converter E/W");
        assertEquals(0x0209, world.object(5).objectFlags());
        assertFalse(world.object(5).isVanishing());
        assertEquals(0, world.object(5).classTimer());
    }

    @Test
    void everyRealTowerLoadsWithItsLiftsPaired() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        List<TowerDefinition> towers = TowerAssets.allTowers();
        assertEquals(144, towers.size());

        int lifts = 0;
        for (TowerDefinition tower : towers) {
            WorldState world = WorldState.load(tower, SimOptions.DEFAULT);
            lifts += world.lifts().size();
            if (tower.id().equals("original-052")) {
                CellPos orphan = new CellPos(4, 10, 2);
                assertTrue(world.liftAt(orphan).isEmpty());
                assertEquals(CellFlags.LIFT | CellFlags.LIFT_DOWN, world.flags(orphan));
            }
        }
        assertEquals(329, lifts);
    }
}
