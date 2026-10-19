package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.LizardBehaviour;

/** Phase 2c spec §3.4: the lizard (class $D, {@code L00B7A6}, {@code S:9782-9813}). */
class LizardTest {

    @Test
    void aBlockedLizardReversesItsFacingAndMarksItsCell() {
        // F (0,0),(1,0); lizard E/W at (0,0) (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.LZE));
        SimObject lizard = run.sim.world().object(3);
        run.runUntilFrame(33);

        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveClaimed(3, 3, pos(1, 0, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveFinished(33, 3, pos(1, 0, 0)), new SimEvent.Turned(33, 3, Direction.WEST)),
                run.eventsOf(3));
        assertEquals(pos(1, 0, 0), lizard.markedCell());
        assertEquals(Direction.WEST, lizard.facing());
    }

    @Test
    void aNewerMarkOverwritesTheOlderOneSoTheOlderFloorSurvives() {
        // F (0..2,0): klondike (0,0) slot 3, lizard E/W (1,0) slot 4, block (2,0) slot 5
        WorldState world = WorldState.load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.KLD)
                .object(1, 0, 0, ObjectType.LZE).object(2, 0, 0, ObjectType.BLK).build(), SimOptions.DEFAULT);
        List<SimEvent> out = new ArrayList<>();
        SimContext ctx = new SimContext(world);
        ctx.begin(out);
        ctx.thaw();
        SimObject lizard = world.object(4);
        ctx.setMark(lizard, pos(2, 0, 0));

        LizardBehaviour.INSTANCE.act(ctx, lizard);
        assertEquals(pos(1, 0, 0), lizard.markedCell(), "(2,0) is held: east blocked, turn and mark (1,0)");
        ctx.remove(world.object(5));
        LizardBehaviour.INSTANCE.act(ctx, lizard);

        assertTrue(world.hasFloor(pos(2, 0, 0)), "the older mark was overwritten before (2,0) became free");
        assertEquals(pos(1, 0, 0), lizard.markedCell());
        assertEquals(List.of(new SimEvent.Turned(0, 4, Direction.WEST), new SimEvent.Removed(0, 5, pos(2, 0, 0)),
                new SimEvent.Turned(0, 4, Direction.EAST)), out);
    }

    @Test
    void aFloorOnTopOfASupportingBlockIsNotEaten() {
        // level 0: box at (1,0); level 1: F (0,0),(1,0); lizard E/W at (0,0,1) (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, ".B").level(1, "FF")
                .object(0, 0, 1, ObjectType.LZE));
        run.runUntilFrame(200);

        assertTrue(run.events.contains(new SimEvent.MoveStarted(67, 3, pos(0, 0, 1), pos(1, 0, 1))),
                "the mark (1,0,1) was cleared in frame 66 without eating it");
        assertEquals(List.of(new SimEvent.FloorEaten(99, pos(0, 0, 1))), run.events(SimEvent.FloorEaten.class),
                "only the unsupported floor goes");
        assertTrue(run.sim.world().hasFloor(pos(1, 0, 1)));
    }

    @Test
    void aLizardBlockedOnBothSidesNeverEatsItsOwnCell() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.LZN));
        run.runUntilFrame(10);

        SimObject lizard = run.sim.world().object(3);
        assertEquals(10L, run.events(SimEvent.Turned.class).stream().filter(turned -> turned.slot() == 3).count());
        assertEquals(pos(0, 0, 0), lizard.markedCell());
        assertTrue(run.events(SimEvent.FloorEaten.class).isEmpty());
        assertTrue(run.sim.world().hasFloor(pos(0, 0, 0)));
    }
}
