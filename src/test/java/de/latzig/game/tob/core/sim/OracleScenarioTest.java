package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Oracle §10 scenarios in scope of Phase 2a. Frame 0 is the READY-ending frame; frame k is the k-th normal frame. */
class OracleScenarioTest {

    private static final SpiderId Z = SpiderId.ZAPPER;

    @Test
    void t01WalkTiming() {
        // F at (0,0),(0,1) plus (0,2), so that the FORWARD accepted in frame 32 can also start; zapper at (0,0) facing N
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.ZAP).build());
        SimObject zap = run.sim.world().spider(Z);
        Occupancy occupancy = run.sim.world().occupancy();

        run.step(new Command.Forward(Z));
        for (int frame = 0; frame <= 30; frame++) {
            if (frame > 0) {
                run.step();
            }
            assertEquals(frame, run.sim.frame());
            assertEquals(frame <= 14 ? pos(0, 0, 0) : pos(0, 1, 0), zap.cell(), "cell in frame " + frame);
            assertEquals(frame >= 9 ? 0 : Occupancy.FREE, occupancy.holder(pos(0, 1, 0)), "claim in frame " + frame);
            assertEquals(frame <= 20 ? 0 : Occupancy.FREE, occupancy.holder(pos(0, 0, 0)), "origin in frame " + frame);
            assertTrue(zap.isMoving(), "moving in frame " + frame);
        }

        List<SimEvent> frame31 = run.step(new Command.Forward(Z));
        assertEquals(List.of(new SimEvent.MoveFinished(31, 0, pos(0, 1, 0))), frame31, "FORWARD refused while moving");
        List<SimEvent> frame32 = run.step(new Command.Forward(Z));
        assertEquals(List.of(new SimEvent.MoveStarted(32, 0, pos(0, 1, 0), pos(0, 2, 0))), frame32);
    }

    @Test
    void t26LiftRide() {
        // ride part only (immunity is Phase 2b): up-lift at (2,0), zapper on it, fixed zapper E at (0,0), F (0..3,0)
        SimRun run = new SimRun(TestTowers.tower().level(0, "FFUF").level(1, "..u.")
                .object(2, 0, 0, ObjectType.ZAP).object(0, 0, 0, ObjectType.FZE).build());
        WorldState world = run.sim.world();
        SimObject zap = world.spider(Z);

        assertTrue(run.step(new Command.Back(Z))
                .contains(new SimEvent.LiftRideStarted(0, 0, pos(2, 0, 0), pos(2, 0, 1))));
        for (int frame = 1; frame <= 30; frame++) {
            run.step();
            assertTrue(zap.isRiding(), "riding in frame " + frame);
            assertEquals(pos(2, 0, 0), zap.cell());
            assertTrue(world.hasFloor(pos(2, 0, 1)));
            assertEquals(0, world.occupancy().holder(pos(2, 0, 1)));
            assertFalse(world.isBlocked(pos(2, 0, 0)));
        }

        assertTrue(run.step().contains(new SimEvent.LiftRideFinished(31, 0, pos(2, 0, 1))));
        assertEquals(pos(2, 0, 1), zap.cell());
        assertEquals(SimObject.UNITS_PER_CELL, zap.worldY());
        assertTrue(world.isBlocked(pos(2, 0, 0)));
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(2, 0, 0)));
        assertEquals(ObjectStatus.ALIVE, world.object(3).status());
    }

    @Test
    void t28TimeLimitAfterOneReadyFrame() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(2, true, 0, 0).build());
        run.step(new Command.Fire(Z));
        assertEquals(6, run.sim.world().clockPhase());

        run.runUntilFinished();

        assertEquals(List.of(new SimEvent.TimeTick(7, 1), new SimEvent.TimeTick(14, 0)),
                run.events(SimEvent.TimeTick.class));
        assertEquals(List.of(new SimEvent.ExitPending(14, Rules.EXIT_TIMEOUT, 27)),
                run.events(SimEvent.ExitPending.class));
        assertEquals(List.of(new SimEvent.Exited(40, Rules.EXIT_TIMEOUT)), run.events(SimEvent.Exited.class));
    }

    @Test
    void t28TimeLimitAfterFiveReadyFrames() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .conditions(2, true, 0, 0).build());
        run.step(new Command.TurnLeft(Z));
        run.step();
        run.step();
        run.step();
        run.step(new Command.Fire(Z));
        assertEquals(2, run.sim.world().clockPhase());

        run.runUntilFinished();

        assertEquals(List.of(new SimEvent.TimeTick(3, 1), new SimEvent.TimeTick(10, 0)),
                run.events(SimEvent.TimeTick.class));
        assertEquals(List.of(new SimEvent.ExitPending(10, Rules.EXIT_TIMEOUT, 27)),
                run.events(SimEvent.ExitPending.class));
        assertEquals(List.of(new SimEvent.Exited(36, Rules.EXIT_TIMEOUT)), run.events(SimEvent.Exited.class));
    }

    @Test
    void t29Program() {
        // zapper at (1,0) facing N; F at (1,0),(1,1),(0,1); program F, L, F, Fire; Run at frame 0
        SimRun run = new SimRun(TestTowers.tower().level(0, "FF", ".F").object(1, 0, 0, ObjectType.ZAP).build());
        run.writeProgram(Z, ProgramStep.FORWARD, ProgramStep.LEFT, ProgramStep.FORWARD, ProgramStep.FIRE);
        run.step(new Command.Run(Z));
        assertEquals(0, run.sim.frame());

        run.runUntilFrame(69);
        assertEquals(ProgramState.RUNNING, run.sim.program(Z).state());
        assertEquals(7, run.sim.program(Z).cursor());
        run.step();
        assertEquals(ProgramState.IDLE, run.sim.program(Z).state(), "stopped from frame 70");

        assertEquals(List.of(0, 33), run.events(SimEvent.MoveStarted.class).stream().map(SimEvent::frame).toList());
        assertEquals(List.of(31, 64), run.events(SimEvent.MoveFinished.class).stream().map(SimEvent::frame).toList());
        assertEquals(List.of(new SimEvent.Turned(32, 0, Direction.WEST)), run.events(SimEvent.Turned.class));
        assertEquals(List.of(new SimEvent.SpiderFired(65, 0, pos(0, 1, 0), Direction.WEST)),
                run.events(SimEvent.SpiderFired.class));
        assertEquals(pos(0, 1, 0), run.sim.world().spider(Z).cell());
    }

    @Test
    void t30TowerWithoutTasksNeverCompletes() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP).build());
        run.step(new Command.Fire(Z));

        assertEquals(List.of(), run.sim.addRobot());
        assertEquals(List.of(), run.sim.addKlondike());
        run.runUntilFrame(5000);

        assertFalse(run.sim.isFinished());
        assertEquals(0, run.sim.exitCode());
        assertTrue(run.events(SimEvent.ExitPending.class).isEmpty());
    }
}
