package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

class OccupancyProblemsTest {

    private static WorldState world() {
        return WorldState.load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.BLK).build(), SimOptions.DEFAULT);
    }

    @Test
    void aFreshWorldIsConsistent() {
        assertEquals(List.of(), world().occupancyProblems());
    }

    @Test
    void foreignHoldersAndLostCellsAreReported() {
        WorldState world = world();
        world.occupancy().set(pos(1, 0, 0), 0);
        world.occupancy().release(pos(2, 0, 0));

        assertEquals(List.of("slot 3 does not hold its cell " + pos(2, 0, 0),
                "cell " + pos(1, 0, 0) + " held by slot 0, which neither stands in it nor claimed it"),
                world.occupancyProblems());
    }
}
