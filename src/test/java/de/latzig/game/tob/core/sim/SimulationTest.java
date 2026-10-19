package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

class SimulationTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;
    private static final SpiderId G = SpiderId.GRABBER;

    /** Zapper (0,0) facing E and pusher (2,0) facing W on F (0..2,0); the pusher's program is FORWARD. */
    private static SimRun facingPair() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).build());
        run.step(new Command.TurnRight(Z));
        run.step();
        run.step();
        run.step();
        run.step(new Command.TurnLeft(P));
        run.writeProgram(P, ProgramStep.FORWARD);
        return run;
    }

    @Test
    void readyFramesKeepFrameZeroAFrozenWorldAndAStoppedClock() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(1, true, 0, 0).build());

        for (int i = 0; i < 20; i++) {
            run.step(new Command.TurnLeft(Z));
            assertEquals(0, run.sim.frame());
            assertTrue(run.sim.isReady());
            assertTrue(run.sim.world().isFrozen());
        }

        assertEquals(1, run.sim.world().timeLeft());
        assertTrue(run.events(SimEvent.TimeTick.class).isEmpty());
        assertEquals(5, run.events(SimEvent.Turned.class).size(), "turns at READY steps 0, 4, 8, 12, 16");
    }

    @Test
    void readyEndsInTheFrameOfTheFirstActionAndTheWorldThawsAfterIt() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP).build());
        run.step(new Command.TurnLeft(Z));
        run.step();
        run.step();
        run.step();
        run.step(new Command.TurnRight(Z));
        assertTrue(run.sim.isReady());

        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.MoveStarted(0, 0, pos(0, 0, 0), pos(0, 1, 0))),
                run.step(new Command.Forward(Z)));
        assertFalse(run.sim.isReady());
        assertFalse(run.sim.world().isFrozen());
        assertEquals(0, run.sim.frame());
        run.step();
        assertEquals(1, run.sim.frame());
    }

    @Test
    void aFreezeStartedDuringReadyOutlivesTheReadyThaw() {
        // Counter API directly (I1): a freezer activated during READY (W001898 -2) must not be cancelled by the
        // READY-ending thaw (+1 -> -1, still frozen). The freezer mechanism itself arrives with 2d.
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP).build());
        run.sim.world().freeze();
        assertTrue(run.sim.world().isFrozen());

        run.step(new Command.TurnLeft(Z));
        assertTrue(run.sim.isReady());
        assertTrue(run.sim.world().isFrozen());

        run.step(new Command.Forward(Z));
        assertFalse(run.sim.isReady());
        assertTrue(run.sim.world().isFrozen(),
                "a freeze started during READY outlives the READY thaw: counter -2 + 1 = -1");

        run.sim.world().thaw();
        assertFalse(run.sim.world().isFrozen(), "the freezer's own expiry thaws it: -1 + 1 = 0");
    }

    @Test
    void aPendingExitStopsTheTimeLimitTick() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.GRB).conditions(2, true, 0, 1).build());
        run.step(new Command.Fire(Z));
        run.runUntilFrame(3);
        assertEquals(2, run.sim.world().timeLeft());

        assertTrue(run.sim.addRobot().stream().anyMatch(SimEvent.ExitPending.class::isInstance));
        assertTrue(run.sim.world().isExitPending());

        run.runUntilFrame(7);
        assertTrue(run.events(SimEvent.TimeTick.class).isEmpty(),
                "a pending exit suppresses the time-limit tick (Simulation.java:151)");
        assertEquals(2, run.sim.world().timeLeft());

        run.runUntilFinished();
        assertEquals(new SimEvent.Exited(27, Rules.EXIT_COMPLETED), run.events.get(run.events.size() - 1));
        assertEquals(2, run.sim.world().timeLeft(), "the time limit never ticked while the exit was pending");
    }

    @Test
    void sameFrameClaimsGoToTheEarlierSlot() {
        SimRun run = facingPair();
        run.step(new Command.Forward(Z), new Command.Run(P));
        run.runUntilFrame(31);

        assertTrue(run.events.contains(new SimEvent.MoveStarted(0, 1, pos(2, 0, 0), pos(1, 0, 0))));
        assertTrue(run.events.contains(new SimEvent.MoveClaimed(9, 0, pos(1, 0, 0))));
        assertTrue(run.events.contains(new SimEvent.MoveReversed(9, 1)));
        assertTrue(run.events.contains(new SimEvent.MoveFinished(19, 1, pos(2, 0, 0))));
        assertTrue(run.events.contains(new SimEvent.MoveFinished(31, 0, pos(1, 0, 0))));
    }

    @Test
    void aLaterClaimerFindsTheCellTakenAndReverses() {
        SimRun run = facingPair();
        run.step(new Command.Forward(Z));
        run.step();
        run.step(new Command.Run(P));
        run.runUntilFrame(31);

        assertTrue(run.events.contains(new SimEvent.MoveStarted(2, 1, pos(2, 0, 0), pos(1, 0, 0))));
        assertTrue(run.events.contains(new SimEvent.MoveReversed(11, 1)));
        assertTrue(run.events.contains(new SimEvent.MoveFinished(21, 1, pos(2, 0, 0))));
        assertEquals(pos(1, 0, 0), run.sim.world().spider(Z).cell());
    }

    @Test
    void anExitWaitsItsDelayAndAPendingExitIsNotOverridden() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.GRB).conditions(0, false, 0, 1).build());
        run.step(new Command.Fire(Z));
        run.runUntilFrame(3);
        assertEquals(3, run.sim.world().clockPhase());

        assertEquals(List.of(new SimEvent.CounterChanged(3, CounterKind.ROBOTS, 1),
                new SimEvent.ExitPending(3, Rules.EXIT_COMPLETED, 24)), run.sim.addRobot());
        assertEquals(List.of(new SimEvent.Destroyed(3, 2, pos(1, 0, 0))), run.sim.destroy(2));
        assertEquals(List.of(new SimEvent.Destroyed(3, 0, pos(0, 0, 0))), run.sim.destroy(0),
                "exit 3 would apply, but exit 1 is pending");
        run.runUntilFinished();

        assertEquals(27, run.sim.frame());
        assertEquals(new SimEvent.Exited(27, Rules.EXIT_COMPLETED), run.events.get(run.events.size() - 1));
        assertEquals(Rules.EXIT_COMPLETED, run.sim.exitCode());
    }

    @Test
    void losingTheGrabberWithKlondikesMissingIsExitFour() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.GRB)
                .object(1, 0, 0, ObjectType.KLD).conditions(0, false, 1, 0).build());
        run.step(new Command.Fire(G));

        assertEquals(List.of(new SimEvent.Destroyed(0, 2, pos(0, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_GRABBER_DESTROYED, 27)), run.sim.destroy(2));
        run.runUntilFinished();
        assertEquals(new SimEvent.Exited(27, Rules.EXIT_GRABBER_DESTROYED), run.events.get(run.events.size() - 1));
    }

    @Test
    void losingTheLastSpidersWithTheKlondikesCompleteIsExitThree() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.GRB).conditions(0, false, 0, 1).build());
        run.step(new Command.Fire(Z));

        assertEquals(List.of(new SimEvent.Destroyed(0, 2, pos(2, 0, 0))), run.sim.destroy(2));
        assertEquals(List.of(new SimEvent.Destroyed(0, 0, pos(0, 0, 0)),
                new SimEvent.ExitPending(0, Rules.EXIT_SPIDERS_DESTROYED, 27)), run.sim.destroy(0));
    }

    @Test
    void aDestroyedSpiderKeepsItsCellFor32FramesAndIgnoresInput() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.GRB).build());
        SimObject zap = run.sim.world().spider(Z);
        Occupancy occupancy = run.sim.world().occupancy();
        run.step(new Command.Fire(Z));
        run.sim.destroy(0);

        assertEquals(List.of(), run.step(new Command.Forward(Z)));
        for (int frame = 2; frame <= 31; frame++) {
            run.step();
            assertEquals(ObjectStatus.DESTROYING, zap.status(), "frame " + frame);
            assertEquals(0, occupancy.holder(pos(0, 0, 0)), "frame " + frame);
        }
        assertEquals(List.of(new SimEvent.Removed(32, 0, pos(0, 0, 0))), run.step());
        assertEquals(ObjectStatus.REMOVED, zap.status());
        assertTrue(occupancy.isFree(pos(0, 0, 0)));
    }

    @Test
    void abandonAndRestartFinishAtOnceUnlessAnExitIsPending() {
        TowerDefinition tower = TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(0, false, 0, 1).build();
        Simulation abandoned = Simulation.start(tower, SimOptions.DEFAULT);
        assertEquals(List.of(new SimEvent.Exited(0, Rules.EXIT_ABANDONED)),
                abandoned.step(List.of(new Command.Abandon())));
        assertTrue(abandoned.isFinished());
        assertEquals(Rules.EXIT_ABANDONED, abandoned.exitCode());

        Simulation restarted = Simulation.start(tower, SimOptions.DEFAULT);
        assertEquals(List.of(new SimEvent.Exited(0, Rules.EXIT_RESTART)),
                restarted.step(List.of(new Command.Restart())));

        Simulation pending = Simulation.start(tower, SimOptions.DEFAULT);
        pending.step(List.of(new Command.Fire(Z)));
        pending.addRobot();
        assertTrue(pending.step(List.of(new Command.Restart())).isEmpty());
        assertFalse(pending.isFinished());
        assertEquals(1, pending.frame());
        assertEquals(Rules.EXIT_COMPLETED, pending.exitCode());
    }

    @Test
    void aFinishedSimulationRejectsFurtherSteps() {
        Simulation sim = Simulation.start(TestTowers.tower().build(), SimOptions.DEFAULT);
        sim.step(List.of(new Command.Abandon()));

        assertThrows(IllegalStateException.class, () -> sim.step(List.of()));
    }

    @Test
    void staticObstaclesBlockWalking() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 0, 0, ObjectType.BLK).build());
        run.step(new Command.TurnRight(Z));

        assertEquals(List.of(new SimEvent.ReadyEnded(0)), run.step(new Command.Forward(Z)));
        run.runUntilFrame(40);

        assertTrue(run.events(SimEvent.MoveStarted.class).isEmpty());
        assertEquals(ObjectStatus.ALIVE, run.sim.world().object(3).status());
        assertEquals(pos(1, 0, 0), run.sim.world().object(3).cell());
    }

    @Test
    void anAbandonInTheTimeoutFrameIsRefusedAndTheAttemptEndsWithTheTimeout() {
        // Abandon and the time-limit tick land in the same frame; the original reads Abandon after the tick
        // (L00A130, S:8333-8339), so the already-pending timeout exit refuses it.
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(1, true, 0, 0).build());
        run.step(new Command.Fire(Z));
        for (int frame = 1; frame <= 6; frame++) {
            run.step();
        }

        List<SimEvent> frame7 = run.step(new Command.Abandon());
        assertEquals(List.of(new SimEvent.TimeTick(7, 0), new SimEvent.ExitPending(7, Rules.EXIT_TIMEOUT, 27)),
                frame7, "the abandon is refused, not finished, because the exit became pending in this same frame");
        assertFalse(run.sim.isFinished());

        run.runUntilFinished();
        assertEquals(new SimEvent.Exited(33, Rules.EXIT_TIMEOUT), run.events.get(run.events.size() - 1));
        assertEquals(Rules.EXIT_TIMEOUT, run.sim.exitCode());
    }

    @Test
    void abandonInANormalFrameCarriesTheCurrentFrameNumber() {
        // Abandon is read at input time, after advanceFrame, so the Exited event carries this frame, not the last one.
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP).build());
        run.step(new Command.Fire(Z));
        run.step();
        run.step();
        assertEquals(2, run.sim.frame());

        assertEquals(List.of(new SimEvent.Exited(3, Rules.EXIT_ABANDONED)), run.step(new Command.Abandon()));
        assertEquals(3, run.sim.frame());
        assertTrue(run.sim.isFinished());
    }

    @Test
    void theFrameThatAcceptsAnAbandonStillRunsInputMovementAndHandlers() {
        // Phase 2 carry-over 10: the play loop leaves only after the handlers (S:6944)
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF", "FF").object(0, 0, 0, ObjectType.ZAP)
                .object(1, 1, 0, ObjectType.FZE).build());
        run.step(new Command.Forward(Z));
        run.step();
        int z = run.sim.world().spider(Z).worldZ();

        List<SimEvent> events = run.step(new Command.TurnRight(Z), new Command.Abandon());

        assertEquals(new SimEvent.Turned(2, 0, Direction.EAST), events.get(0), "input");
        assertEquals(z - Walk.UNITS_PER_FRAME, run.sim.world().spider(Z).worldZ(), "movement");
        assertTrue(events.stream().anyMatch(event -> event instanceof SimEvent.RayFired fired
                && fired.slot() == WorldState.SPIDER_SLOTS), "handlers: the fixed zapper (slot 3) fired");
        assertEquals(new SimEvent.Exited(2, Rules.EXIT_ABANDONED), events.get(events.size() - 1));
        assertTrue(run.sim.isFinished());
        assertEquals(Rules.EXIT_ABANDONED, run.sim.exitCode());
    }

    @Test
    void aRestartInTheReadyFrameRunsThatFrameAndEndsWithExitSix() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);

        List<SimEvent> events = sim.step(List.of(new Command.Forward(Z), new Command.Restart()));

        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.MoveStarted(0, 0, pos(0, 0, 0), pos(0, 1, 0)),
                new SimEvent.Exited(0, Rules.EXIT_RESTART)), events);
        assertTrue(sim.isFinished());
        assertEquals(Rules.EXIT_RESTART, sim.exitCode());
    }
}
