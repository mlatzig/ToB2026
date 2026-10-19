package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Oracle §10 scenarios of Phase 2c in the full frame pipeline. Frame 0 is the READY-ending frame; frame k is the k-th
 * normal frame. "Start time" ({@link SimRun#startTime}) is a zapper at (7,7) that fires into the void at frame 0.
 */
class MoverScenarioTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;

    /**
     * T-22 layout: worm E/W at (0,0) (slot 3) on F (0..3,0); zapper at (2,2) turned S over F (2,1),(2,2), so its ray
     * crosses (2,0); pusher at (5,0) turned W, so its ray crosses (4,0) and (3,0). The zapper's fire at frame 0 passes
     * (2,1) and (2,0) while the worm is still at (0,0).
     */
    private static SimRun t22() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "..F", "..F", "FFFF").object(0, 0, 0, ObjectType.WOE)
                .object(2, 2, 0, ObjectType.ZAP).object(5, 0, 0, ObjectType.PSH).build());
        run.turnInReady(new Command.TurnRight(Z));
        run.turnInReady(new Command.TurnRight(Z));
        run.turnInReady(new Command.TurnLeft(P));
        run.step(new Command.Fire(Z));
        assertEquals(0, run.sim.frame());
        return run;
    }

    @Test
    void t05HopperTieOnOneRow() {
        // F (0..2,0); H1 at (0,0) slot 3, H2 at (2,0) slot 4, both facing N
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.HOP)
                .object(2, 0, 0, ObjectType.HOP));
        run.runUntilFrame(33);

        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.Turned(1, 3, Direction.EAST), new SimEvent.MoveClaimed(13, 3, pos(1, 0, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveFinished(33, 3, pos(1, 0, 0)),
                new SimEvent.MoveStarted(33, 3, pos(1, 0, 0), pos(0, 0, 0)), new SimEvent.Turned(33, 3, Direction.WEST)),
                run.eventsOf(3));
        assertEquals(List.of(new SimEvent.MoveStarted(1, 4, pos(2, 0, 0), pos(1, 0, 0)),
                new SimEvent.Turned(1, 4, Direction.WEST), new SimEvent.MoveReversed(13, 4),
                new SimEvent.MoveFinished(25, 4, pos(2, 0, 0)), new SimEvent.Idle(25, 4)), run.eventsOf(4));
    }

    @Test
    void t06HopperTieByRow() {
        // F (0,0),(1,0),(1,1); H1 (south) at (0,0) slot 3, H2 at (1,1) slot 4, both facing N
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, ".F", "FF").object(0, 0, 0, ObjectType.HOP)
                .object(1, 1, 0, ObjectType.HOP));
        run.runUntilFrame(25);

        assertTrue(run.eventsOf(3).containsAll(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveClaimed(13, 3, pos(1, 0, 0)))));
        assertEquals(List.of(new SimEvent.MoveStarted(1, 4, pos(1, 1, 0), pos(1, 0, 0)),
                new SimEvent.Turned(1, 4, Direction.SOUTH), new SimEvent.MoveReversed(13, 4),
                new SimEvent.MoveFinished(25, 4, pos(1, 1, 0)), new SimEvent.Idle(25, 4)), run.eventsOf(4));
    }

    @Test
    void t07AnEarlierClaimBySpiderWins() {
        // F (0..2,0); zapper (0,0) turned E (slot 0); hopper (2,0) slot 3, whose only exit is W
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.HOP).build());
        run.turnInReady(new Command.TurnRight(Z));
        run.step(new Command.Forward(Z));
        run.runUntilFrame(25);

        assertTrue(run.events.contains(new SimEvent.MoveClaimed(9, 0, pos(1, 0, 0))));
        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(2, 0, 0), pos(1, 0, 0)),
                new SimEvent.Turned(1, 3, Direction.WEST), new SimEvent.MoveReversed(13, 3),
                new SimEvent.MoveFinished(25, 3, pos(2, 0, 0)), new SimEvent.Idle(25, 3)), run.eventsOf(3));
    }

    @Test
    void t22AZapReversesTheWalkingWormExceptAtCountersTwoSixteenAndThirty() {
        for (int fireFrame : new int[] {50, 51, 64, 68}) {
            SimRun run = t22();
            run.runUntilFrame(fireFrame - 1);

            List<SimEvent> fire = run.step(new Command.Fire(Z));

            boolean reversed = fireFrame == 51;
            assertTrue(fire.contains(new SimEvent.RayHit(fireFrame, 0, 3,
                    reversed ? RayOutcome.REVERSED : RayOutcome.ABSORBED)), "frame " + fireFrame);
            if (reversed) {
                assertTrue(fire.containsAll(List.of(new SimEvent.MoveReversed(51, 3),
                        new SimEvent.Turned(51, 3, Direction.WEST),
                        new SimEvent.CellChanged(51, 3, pos(2, 0, 0), pos(1, 0, 0)))));
                run.runUntilFrame(67);
                assertTrue(run.events.containsAll(List.of(new SimEvent.MoveFinished(67, 3, pos(1, 0, 0)),
                        new SimEvent.MoveStarted(67, 3, pos(1, 0, 0), pos(0, 0, 0)))));
            } else {
                assertTrue(run.events(SimEvent.MoveReversed.class).isEmpty(), "frame " + fireFrame);
            }
            assertEquals(List.of(), run.sim.world().occupancyProblems());
        }
    }

    @Test
    void t22ZappingAStandingWormHasNoEffect() {
        // worm E/W at (2,0) with void on both sides (slot 3): it stands and flips every frame; zapper at (2,2) turned S
        SimRun run = new SimRun(TestTowers.tower().level(0, "..F", "..F", "..F").object(2, 0, 0, ObjectType.WOE)
                .object(2, 2, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnRight(Z));
        run.turnInReady(new Command.TurnRight(Z));
        run.step(new Command.Fire(Z));
        run.runUntilFrame(2);

        List<SimEvent> fire = run.step(new Command.Fire(Z));

        assertTrue(run.events.contains(new SimEvent.RayHit(0, 0, 3, RayOutcome.ABSORBED)), "READY frame");
        assertTrue(fire.contains(new SimEvent.RayHit(3, 0, 3, RayOutcome.ABSORBED)));
        assertTrue(run.events(SimEvent.MoveReversed.class).isEmpty());
        assertEquals(pos(2, 0, 0), run.sim.world().object(3).cell());
    }

    @Test
    void t22APushInABlockedFrameMakesTheWormInertAndAZapDuringThePushIsAbsorbed() {
        SimRun run = t22();
        run.runUntilFrame(97);
        List<SimEvent> push = run.step(new Command.Fire(P));
        run.runUntilFrame(106);
        List<SimEvent> zap = run.step(new Command.Fire(Z));
        run.runUntilFrame(200);

        SimObject worm = run.sim.world().object(3);
        assertTrue(run.events.contains(new SimEvent.Turned(97, 3, Direction.WEST)), "blocked at (3,0)");
        assertTrue(push.containsAll(List.of(new SimEvent.PushStarted(98, 3, pos(3, 0, 0), pos(2, 0, 0)),
                new SimEvent.Deactivated(98, 3), new SimEvent.RayHit(98, 1, 3, RayOutcome.PUSHED))));
        assertTrue(zap.contains(new SimEvent.RayHit(107, 0, 3, RayOutcome.ABSORBED)), "deviation 3");
        assertTrue(run.events.containsAll(List.of(new SimEvent.CellChanged(105, 3, pos(3, 0, 0), pos(2, 0, 0)),
                new SimEvent.PushFinished(113, 3, pos(2, 0, 0)))));
        assertFalse(run.eventsOf(3).stream().anyMatch(event -> event.frame() > 98
                && (event instanceof SimEvent.MoveStarted || event instanceof SimEvent.Turned)), "inert");
        assertTrue(worm.isDeactivated());
        assertEquals(pos(2, 0, 0), worm.cell());
        assertEquals(MoveKind.PUSH, worm.moveKind());
        assertEquals(List.of(), run.sim.world().occupancyProblems());
    }

    @Test
    void t23WatcherTurn() {
        // F (0..2, 0..2); watcher at (0,0) facing N (slot 3). Frame 1: left W is void, so it walks N; at (0,1) the
        // same; frame 65 at (0,2): left and straight blocked, right E free: a 4-frame turn, then the step E.
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FFF", "FFF", "FFF")
                .object(0, 0, 0, ObjectType.WAT));
        SimObject watcher = run.sim.world().object(3);
        run.runUntilFrame(64);
        List<Integer> timers = new ArrayList<>();
        for (int frame = 65; frame <= 69; frame++) {
            run.step();
            timers.add(watcher.classTimer());
        }
        run.runUntilFrame(70);

        assertEquals(List.of(4, 3, 2, 1, 0), timers, "+1C after frames 65..69");
        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(0, 1, 0)),
                new SimEvent.MoveClaimed(12, 3, pos(0, 1, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(0, 1, 0)),
                new SimEvent.MoveFinished(33, 3, pos(0, 1, 0)),
                new SimEvent.MoveStarted(33, 3, pos(0, 1, 0), pos(0, 2, 0)),
                new SimEvent.MoveClaimed(44, 3, pos(0, 2, 0)),
                new SimEvent.CellChanged(49, 3, pos(0, 1, 0), pos(0, 2, 0)),
                new SimEvent.MoveFinished(65, 3, pos(0, 2, 0)),
                new SimEvent.TurnStarted(65, 3, Direction.EAST, 4), new SimEvent.TurnFinished(69, 3),
                new SimEvent.MoveStarted(69, 3, pos(0, 2, 0), pos(1, 2, 0))), run.eventsOf(3));
        assertEquals(Walk.FRAMES - 1, watcher.moveCounter(), "the step moves from frame 70");
    }

    @Test
    void t24HopperTrapped() {
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.HOP));
        run.runUntilFrame(33);

        assertEquals(List.of(new SimEvent.Idle(1, 3), new SimEvent.Idle(17, 3), new SimEvent.Idle(33, 3)),
                run.eventsOf(3));
    }

    @Test
    void t26HopperStandingAfterALostClaimSteersFromTheReversedMoveDirectionNotFacing() {
        // Home (0,1), floor to N (0,2) and S (0,0) besides the race cell E (1,1); zapper at (2,1) races W into
        // (1,1) and wins the claim (its claim value 10 < the hopper's 12, S:10096-10098), so the hopper's +0E
        // reverses from EAST to WEST while its facing stays EAST (G-1). Standing again at home, right of WEST
        // is N: the correct next step is N. Right of the stale facing EAST would be S instead.
        SimRun run = new SimRun(TestTowers.tower().level(0, "F..", "FFF", "F..").object(0, 1, 0, ObjectType.HOP)
                .object(2, 1, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnLeft(Z));
        run.step(new Command.Forward(Z));
        run.runUntilFrame(25);

        assertTrue(run.eventsOf(3).contains(new SimEvent.MoveStarted(1, 3, pos(0, 1, 0), pos(1, 1, 0))),
                "starts towards the race cell");
        assertTrue(run.eventsOf(3).contains(new SimEvent.Turned(1, 3, Direction.EAST)));
        assertTrue(run.events(SimEvent.MoveReversed.class).stream().anyMatch(event -> event.slot() == 3),
                "the hopper loses the race");
        assertTrue(run.eventsOf(3).contains(new SimEvent.MoveFinished(25, 3, pos(0, 1, 0))), "standing at home again");

        List<SimEvent.MoveStarted> hopperWalks = run.eventsOf(3).stream()
                .filter(SimEvent.MoveStarted.class::isInstance).map(SimEvent.MoveStarted.class::cast).toList();
        assertEquals(2, hopperWalks.size(), "the race walk, and the one decided while standing at home again");
        assertEquals(pos(0, 2, 0), hopperWalks.get(1).to(),
                "steers from the reversed +0E (WEST: right is N), not the stale facing (EAST: right would be S)");
        assertTrue(run.eventsOf(3).contains(new SimEvent.Turned(25, 3, Direction.NORTH)));
        assertEquals(List.of(), run.sim.world().occupancyProblems());
    }

    @Test
    void t25LizardEatsFloor() {
        // F (0,0),(1,0); lizard E/W at (0,0) (slot 3)
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.LZE));
        run.runUntilFrame(200);

        assertEquals(List.of(new SimEvent.MoveStarted(1, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveClaimed(3, 3, pos(1, 0, 0)),
                new SimEvent.CellChanged(17, 3, pos(0, 0, 0), pos(1, 0, 0)),
                new SimEvent.MoveFinished(33, 3, pos(1, 0, 0)), new SimEvent.Turned(33, 3, Direction.WEST),
                new SimEvent.MoveStarted(34, 3, pos(1, 0, 0), pos(0, 0, 0)),
                new SimEvent.MoveClaimed(36, 3, pos(0, 0, 0)),
                new SimEvent.CellChanged(50, 3, pos(1, 0, 0), pos(0, 0, 0)),
                new SimEvent.MoveFinished(66, 3, pos(0, 0, 0)), new SimEvent.Turned(66, 3, Direction.EAST)),
                run.eventsOf(3).subList(0, 10));
        assertEquals(List.of(new SimEvent.FloorEaten(66, pos(1, 0, 0))), run.events(SimEvent.FloorEaten.class));
        assertFalse(run.sim.world().hasFloor(pos(1, 0, 0)));
        assertTrue(run.sim.world().hasFloor(pos(0, 0, 0)), "it oscillates at (0,0) and never eats its own cell");
        assertEquals(pos(0, 0, 0), run.sim.world().object(3).cell());
    }
}
