package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Phase 2c spec §3.2: the watcher (class 6, {@code L00B32E}, {@code S:9487-9576}). */
class WatcherTest {

    private static final SpiderId P = SpiderId.PUSHER;

    @Test
    void leftComesFirstAQuarterTurnTakesFourFramesAndTheStepClaimsAtEleven() {
        // F (0,0),(1,0); watcher at (1,0) facing N (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(1, 0, 0, ObjectType.WAT));
        SimObject watcher = run.sim.world().object(3);
        List<Integer> timers = new ArrayList<>();
        for (int frame = 1; frame <= 5; frame++) {
            run.step();
            timers.add(watcher.classTimer());
        }
        run.runUntilFrame(16);

        assertEquals(List.of(4, 3, 2, 1, 0), timers, "+1C after frames 1..5");
        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.WEST, 4), new SimEvent.TurnFinished(5, 3),
                new SimEvent.MoveStarted(5, 3, pos(1, 0, 0), pos(0, 0, 0)),
                new SimEvent.MoveClaimed(16, 3, pos(0, 0, 0))), run.eventsOf(3));
        assertEquals(Direction.WEST, watcher.facing());
    }

    @Test
    void leftIsNotTestedAgainWhenTheTurnEnds() {
        // z=1: F F; z=0: . F — watcher at (1,1) facing N (slot 3); after its turn to W, its left (S, (1,0)) is free
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF", ".F").object(1, 1, 0, ObjectType.WAT));
        run.runUntilFrame(5);

        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.WEST, 4), new SimEvent.TurnFinished(5, 3),
                new SimEvent.MoveStarted(5, 3, pos(1, 1, 0), pos(0, 1, 0))), run.eventsOf(3));
    }

    @Test
    void rightComesWhenLeftAndStraightAreBlocked() {
        // F (0,0),(1,0); watcher at (0,0) facing N (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.WAT));
        run.runUntilFrame(5);

        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.EAST, 4), new SimEvent.TurnFinished(5, 3),
                new SimEvent.MoveStarted(5, 3, pos(0, 0, 0), pos(1, 0, 0))), run.eventsOf(3));
    }

    @Test
    void aDeadEndGivesAnEightFrameHalfTurn() {
        // F (0,0),(0,1); watcher at (0,1) facing N (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F", "F").object(0, 1, 0, ObjectType.WAT));
        run.runUntilFrame(9);

        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.SOUTH, 8), new SimEvent.TurnFinished(9, 3),
                new SimEvent.MoveStarted(9, 3, pos(0, 1, 0), pos(0, 0, 0))), run.eventsOf(3));
    }

    @Test
    void aBoxedInWatcherIdlesEveryFrame() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.WAT));
        run.runUntilFrame(3);

        assertEquals(List.of(new SimEvent.Idle(1, 3), new SimEvent.Idle(2, 3), new SimEvent.Idle(3, 3)),
                run.eventsOf(3));
    }

    @Test
    void aPushDuringATurnDeactivatesTheWatcherForever() {
        // F (0..2,0); watcher at (1,0) facing N (slot 3); pusher at (3,0) turned W; the zapper at (7,7) starts time
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.WAT)
                .object(3, 0, 0, ObjectType.PSH).object(7, 7, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnLeft(P));
        run.step(new Command.Fire(SpiderId.ZAPPER));
        run.runUntilFrame(2);

        List<SimEvent> push = run.step(new Command.Fire(P));
        run.runUntilFrame(60);

        SimObject watcher = run.sim.world().object(3);
        assertTrue(push.contains(new SimEvent.RayHit(3, 1, 3, RayOutcome.PUSHED)));
        assertEquals(List.of(new SimEvent.TurnStarted(1, 3, Direction.WEST, 4),
                new SimEvent.PushStarted(3, 3, pos(1, 0, 0), pos(0, 0, 0)), new SimEvent.Deactivated(3, 3),
                new SimEvent.CellChanged(10, 3, pos(1, 0, 0), pos(0, 0, 0)),
                new SimEvent.PushFinished(18, 3, pos(0, 0, 0))), run.eventsOf(3));
        assertTrue(watcher.isDeactivated());
        assertEquals(3, watcher.classTimer(), "the turn never finishes");
    }
}
