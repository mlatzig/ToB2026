package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Movement paths that the ToB 2009 classes produce as well (secondary reference, plan clarification 11): the hopper's
 * priorities right, straight, left, back ({@code ObjectElementHOP.java:102-125}), the watcher's left, straight,
 * right, back with 4/8-unit rotations ({@code ObjectElementWAT.java:176-195}) and the worm's reversal with a
 * one-unit stop ({@code AbstractObjectElementWOX.java:233-260}). The original rules (spec §3) give the same paths.
 */
class MoverPathTest {

    private static final String[] OPEN = {"FFFF", "FFFF", "FFFF", "FFFF"};
    private static final String[] RING = {"FFF", "F.F", "FFF"};

    private static List<CellPos> targets(SimRun run, int slot) {
        return run.events(SimEvent.MoveStarted.class).stream().filter(started -> started.slot() == slot)
                .map(SimEvent.MoveStarted::to).toList();
    }

    private static List<Integer> startFrames(SimRun run, int slot) {
        return run.events(SimEvent.MoveStarted.class).stream().filter(started -> started.slot() == slot)
                .map(SimEvent.MoveStarted::frame).toList();
    }

    @Test
    void aHopperOnAnOpenFloorCirclesClockwiseAroundASquare() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, OPEN).object(1, 1, 0, ObjectType.HOP));
        run.runUntilFrame(129);

        assertEquals(List.of(pos(2, 1, 0), pos(2, 0, 0), pos(1, 0, 0), pos(1, 1, 0), pos(2, 1, 0)), targets(run, 3));
        assertEquals(List.of(1, 33, 65, 97, 129), startFrames(run, 3));
    }

    @Test
    void aHopperWhoseRightIsNeverFreeRunsCounterClockwise() {
        // a ring of 8 cells around a void centre; hopper at (0,0) facing N
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, RING).object(0, 0, 0, ObjectType.HOP));
        run.runUntilFrame(257);

        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0), pos(2, 1, 0), pos(2, 2, 0), pos(1, 2, 0), pos(0, 2, 0),
                pos(0, 1, 0), pos(0, 0, 0), pos(1, 0, 0)), targets(run, 3));
        assertTrue(run.events(SimEvent.Idle.class).isEmpty());
    }

    @Test
    void aHopperAtADeadEndJumpsBackWithoutTurnTime() {
        // F (0,0),(1,0); hopper at (1,0) facing N: right and straight are void, left W is free
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(1, 0, 0, ObjectType.HOP));
        run.runUntilFrame(33);

        assertEquals(List.of(pos(0, 0, 0), pos(1, 0, 0)), targets(run, 3));
        assertEquals(List.of(1, 33), startFrames(run, 3));
        assertTrue(run.events(SimEvent.Idle.class).isEmpty());
    }

    @Test
    void aWatcherOnAnOpenFloorCirclesCounterClockwiseAroundASquare() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, OPEN).object(1, 1, 0, ObjectType.WAT));
        run.runUntilFrame(145);

        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.WEST, 4),
                new SimEvent.TurnStarted(37, 3, Direction.SOUTH, 4), new SimEvent.TurnStarted(73, 3, Direction.EAST, 4),
                new SimEvent.TurnStarted(109, 3, Direction.NORTH, 4), new SimEvent.TurnStarted(145, 3, Direction.WEST, 4)),
                run.events(SimEvent.TurnStarted.class));
        assertEquals(List.of(pos(0, 1, 0), pos(0, 0, 0), pos(1, 0, 0), pos(1, 1, 0)), targets(run, 3));
        assertEquals(List.of(5, 41, 77, 113), startFrames(run, 3));
    }

    @Test
    void aWatcherWhoseLeftIsNeverFreeRunsClockwise() {
        // the ring; watcher at (0,0) facing N: right turns at the corners (0,2), (2,2) and (2,0)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, RING).object(0, 0, 0, ObjectType.WAT));
        run.runUntilFrame(237);

        assertEquals(List.of(pos(0, 1, 0), pos(0, 2, 0), pos(1, 2, 0), pos(2, 2, 0), pos(2, 1, 0), pos(2, 0, 0),
                pos(1, 0, 0), pos(0, 0, 0)), targets(run, 3));
        assertEquals(List.of(new SimEvent.TurnStarted(65, 3, Direction.EAST, 4),
                new SimEvent.TurnStarted(133, 3, Direction.SOUTH, 4), new SimEvent.TurnStarted(201, 3, Direction.WEST, 4)),
                run.events(SimEvent.TurnStarted.class));
        assertTrue(run.events(SimEvent.Idle.class).isEmpty());
    }

    @Test
    void aWatcherAtADeadEndTurnsRoundInEightFrames() {
        // F (0,0),(1,0); watcher at (1,0) facing N: left W, then the dead end at (0,0)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(1, 0, 0, ObjectType.WAT));
        run.runUntilFrame(45);

        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.WEST, 4),
                new SimEvent.TurnStarted(37, 3, Direction.EAST, 8)), run.events(SimEvent.TurnStarted.class));
        assertEquals(List.of(pos(0, 0, 0), pos(1, 0, 0)), targets(run, 3));
        assertEquals(List.of(5, 45), startFrames(run, 3));
    }

    @Test
    void aWormShuttlesWithAOneFrameStopAtEachEnd() {
        // F (0..2,0); worm E/W at (0,0)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.WOE));
        run.runUntilFrame(131);

        assertEquals(List.of(pos(1, 0, 0), pos(2, 0, 0), pos(1, 0, 0), pos(0, 0, 0), pos(1, 0, 0)), targets(run, 3));
        assertEquals(List.of(1, 33, 66, 98, 131), startFrames(run, 3));
        assertEquals(List.of(new SimEvent.Turned(65, 3, Direction.WEST), new SimEvent.Turned(130, 3, Direction.EAST)),
                run.events(SimEvent.Turned.class).stream().filter(turned -> turned.slot() == 3).toList());
    }
}
