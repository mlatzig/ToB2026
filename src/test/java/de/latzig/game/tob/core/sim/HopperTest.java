package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Phase 2c spec §3.3: the hopper (class 7, {@code L00B4B4}, {@code S:9594-9650}). */
class HopperTest {

    /** A hopper at (1,1) facing N with floor on the given rows (north first) and the expected first step. */
    private record Case(String north, String middle, String south, CellPos target, Direction direction) {
    }

    @Test
    void theHopperTriesRightStraightLeftBackAndStartsAtOnce() {
        List<Case> cases = List.of(
                new Case(".F.", "FFF", ".F.", pos(2, 1, 0), Direction.EAST),
                new Case(".F.", "FF.", ".F.", pos(1, 2, 0), Direction.NORTH),
                new Case("...", "FF.", ".F.", pos(0, 1, 0), Direction.WEST),
                new Case("...", ".F.", ".F.", pos(1, 0, 0), Direction.SOUTH));
        for (Case c : cases) {
            SimRun run = SimRun.startTime(TestTowers.tower().level(0, c.north(), c.middle(), c.south())
                    .object(1, 1, 0, ObjectType.HOP));
            run.runUntilFrame(1);

            SimEvent started = new SimEvent.MoveStarted(1, 3, pos(1, 1, 0), c.target());
            List<SimEvent> expected = c.direction() == Direction.NORTH ? List.of(started)
                    : List.of(started, new SimEvent.Turned(1, 3, c.direction()));
            assertEquals(expected, run.eventsOf(3), c.direction().toString());
        }
    }

    @Test
    void aTrappedHopperWaitsSixteenFramesAndThenRetries() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.HOP));
        SimObject hopper = run.sim.world().object(3);
        run.runUntilFrame(2);
        assertEquals(MoveKind.IDLE, hopper.moveKind());
        assertEquals(15, hopper.moveCounter());
        assertTrue(hopper.isMoving());

        run.runUntilFrame(40);

        assertEquals(List.of(new SimEvent.Idle(1, 3), new SimEvent.Idle(17, 3), new SimEvent.Idle(33, 3)),
                run.eventsOf(3));
        assertEquals(Direction.NORTH, hopper.moveDirection(), "+0E keeps its direction under the idle bit");
        assertEquals(pos(0, 0, 0), hopper.cell());
    }

    @Test
    void aHopperClaimsAfterTwelveFramesAndReleasesTwelveFramesBeforeTheEnd() {
        // F (0,0),(1,0); hopper at (0,0) facing N (slot 3): right is E
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.HOP));
        WorldState world = run.sim.world();
        run.runUntilFrame(20);
        assertEquals(3, world.occupancy().holder(pos(0, 0, 0)));
        run.step();
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)), "released at counter 12 (frame 21)");
        run.runUntilFrame(33);

        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.Turned(1, 3, Direction.EAST), new SimEvent.MoveClaimed(13, 3, pos(1, 0, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveFinished(33, 3, pos(1, 0, 0)),
                new SimEvent.MoveStarted(33, 3, pos(1, 0, 0), pos(0, 0, 0)), new SimEvent.Turned(33, 3, Direction.WEST)),
                run.eventsOf(3));
    }
}
