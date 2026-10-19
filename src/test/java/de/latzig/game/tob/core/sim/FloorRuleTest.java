package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** The shared floor rule {@code L00BA52} ({@code S:9956-9982}, oracle §6.3, Phase 2c spec §3.5). */
class FloorRuleTest {

    private final List<SimEvent> out = new ArrayList<>();

    private static WorldState world(TestTowers.Builder tower) {
        return WorldState.load(tower.build(), SimOptions.DEFAULT);
    }

    @Test
    void aFreeFloorOnLevelZeroLosesItsWholeBase() {
        WorldState world = world(TestTowers.tower().level(0, "F"));

        assertTrue(FloorRule.canRemove(world, pos(0, 0, 0)));
        assertTrue(FloorRule.remove(world, pos(0, 0, 0), out));

        assertEquals(0, world.flags(pos(0, 0, 0)));
        assertFalse(FloorRule.canRemove(world, pos(0, 0, 0)), "no floor any more");
        assertEquals(List.of(new SimEvent.FloorEaten(0, pos(0, 0, 0))), out);
    }

    @Test
    void voidLiftsAndHeldOrClaimedCellsKeepTheirState() {
        // (0,0) held by a block, (1,0) void, (2,0) lift with its stop above, (3,0) free floor, (4,0) claimed
        WorldState world = world(TestTowers.tower().level(0, "F.UFF").level(1, "..u")
                .object(0, 0, 0, ObjectType.BLK));
        world.occupancy().set(pos(4, 0, 0), 3);

        for (CellPos cell : List.of(pos(0, 0, 0), pos(1, 0, 0), pos(2, 0, 0), pos(2, 0, 1), pos(4, 0, 0))) {
            assertFalse(FloorRule.remove(world, cell, out), cell.toString());
        }
        assertTrue(FloorRule.remove(world, pos(3, 0, 0), out));

        assertTrue(world.hasFloor(pos(0, 0, 0)));
        assertTrue(world.hasFloor(pos(4, 0, 0)));
        assertTrue(world.isLift(pos(2, 0, 0)));
        assertEquals(List.of(new SimEvent.FloorEaten(0, pos(3, 0, 0))), out);
    }

    @Test
    void aSweepPassesEveryCellUpToABlockedCellOrTheGridEdgeAndAppliesTheRuleToEach() {
        // level 0: block (0,0) slot 3 on F, F (1,0), wiper (2,0) slot 4 on F, F (3,0), box (4,0), F (5,0)
        WorldState world = world(TestTowers.tower().level(0, "FFFFBF").object(0, 0, 0, ObjectType.BLK)
                .object(2, 0, 0, ObjectType.WIP));
        SimObject wiper = world.object(4);

        FloorRule.sweep(world, wiper, Direction.EAST, out);
        FloorRule.sweep(world, wiper, Direction.WEST, out);

        assertEquals(List.of(new SimEvent.WiperSweep(0, 4, Direction.EAST, List.of(pos(3, 0, 0))),
                new SimEvent.FloorEaten(0, pos(3, 0, 0)),
                new SimEvent.WiperSweep(0, 4, Direction.WEST, List.of(pos(1, 0, 0), pos(0, 0, 0), pos(-1, 0, 0),
                        pos(-2, 0, 0), pos(-3, 0, 0), pos(-4, 0, 0))),
                new SimEvent.FloorEaten(0, pos(1, 0, 0))), out);
        assertTrue(world.hasFloor(pos(5, 0, 0)), "behind the box");
        assertTrue(world.hasFloor(pos(0, 0, 0)), "held by the block");
        assertTrue(world.hasFloor(pos(2, 0, 0)), "the wiper's own cell is never swept");
    }

    @Test
    void aFloorOnTopOfASupportingBlockSurvives() {
        // level 0: box, void, floor; level 1: floor on all three
        WorldState world = world(TestTowers.tower().level(0, "B.F").level(1, "FFF"));

        assertFalse(FloorRule.remove(world, pos(0, 0, 1), out), "on top of a block");
        assertTrue(FloorRule.remove(world, pos(1, 0, 1), out), "over void");
        assertTrue(FloorRule.remove(world, pos(2, 0, 1), out), "over a plain floor");

        assertTrue(world.hasFloor(pos(0, 0, 1)));
        assertEquals(List.of(new SimEvent.FloorEaten(0, pos(1, 0, 1)), new SimEvent.FloorEaten(0, pos(2, 0, 1))),
                out);
    }
}
