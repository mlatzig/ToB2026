package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.MineBehaviour;

/** Proximity mine (class 8, {@code L00B59A}, {@code S:9651-9723}; oracle §5.1, Phase 2d spec §3.1). */
class MineTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;

    @Test
    void aPushedNeighbourTriggersAtOnceThroughTheCellItsPushClaimed() {
        // z=1 "..F", z=0 "FFF": pusher (0,0), block (1,0) slot 3, mine (2,1) slot 4; (1,0) is diagonal to the mine
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "..F", "FFF").object(0, 0, 0, ObjectType.PSH)
                .object(1, 0, 0, ObjectType.BLK).object(2, 1, 0, ObjectType.PRM));
        run.step(new Command.TurnRight(P));
        run.runUntilFrame(4);
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        List<SimEvent> frame5 = run.step(new Command.Fire(P));

        assertTrue(frame5.contains(new SimEvent.PushStarted(5, 3, pos(1, 0, 0), pos(2, 0, 0))));
        int exploded = frame5.indexOf(new SimEvent.Exploded(5, 4,
                List.of(pos(1, 1, 0), pos(2, 0, 0), pos(3, 1, 0), pos(2, 2, 0))));
        assertEquals(List.of(new SimEvent.Destroyed(5, 3, pos(1, 0, 0)), new SimEvent.Destroyed(5, 4, pos(2, 1, 0))),
                frame5.subList(exploded + 1, exploded + 3));
        assertTrue(run.sim.world().occupancy().isFree(pos(2, 0, 0)), "the push claim is released");
        assertTrue(run.sim.world().spider(P).isAlive(), "(0,0) is no neighbour");
    }

    @Test
    void aMineBeingPushedDoesNotTestUntilItStandsAgain() {
        // z=2 "..F", z=1 "..F", z=0 "FFFF": pusher (0,0) turned E, mine (1,0) slot 3, zapper (2,1) facing N;
        // the push starts in the READY-ending frame 0, the zapper walks N from frame 2
        SimRun run = new SimRun(TestTowers.tower().level(0, "..F", "..F", "FFFF").object(0, 0, 0, ObjectType.PSH)
                .object(1, 0, 0, ObjectType.PRM).object(2, 1, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnRight(P));
        run.step(new Command.Fire(P));
        run.step();
        run.step(new Command.Forward(Z));
        run.runUntilFrame(15);
        SimObject mine = run.sim.world().object(3);
        assertEquals(pos(2, 0, 0), mine.cell(), "half-way since frame 8, next to the walking zapper");
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        run.runUntilFrame(16);

        assertTrue(run.events.contains(new SimEvent.PushFinished(16, 3, pos(2, 0, 0))));
        assertEquals(List.of(new SimEvent.Exploded(16, 3,
                List.of(pos(1, 0, 0), pos(2, -1, 0), pos(3, 0, 0), pos(2, 1, 0)))), run.events(SimEvent.Exploded.class));
        assertTrue(run.events.contains(new SimEvent.Destroyed(16, 0, pos(2, 1, 0))));
        assertTrue(run.sim.world().spider(P).isAlive(), "(0,0) is no neighbour of (2,0)");
    }

    @Test
    void aWalkerInADiagonalCellDoesNotTrigger() {
        // z=1 ".F", z=0 "..FF": mine (1,1) slot 3; zapper (2,0) walks E to (3,0)
        SimRun run = new SimRun(TestTowers.tower().level(0, ".F", "..FF").object(1, 1, 0, ObjectType.PRM)
                .object(2, 0, 0, ObjectType.ZAP).build());
        run.turnInReady(new Command.TurnRight(Z));
        run.step(new Command.Forward(Z));
        run.runUntilFrame(40);

        assertTrue(run.events.contains(new SimEvent.MoveFinished(31, 0, pos(3, 0, 0))));
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());
        assertTrue(run.sim.world().object(3).isAlive());
    }

    @Test
    void aWaitingHopperDoesNotTrigger() {
        // F (0..1,0): hopper (0,0) slot 3, mine (1,0) slot 4; the hopper's only neighbour is the mine's cell
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.HOP)
                .object(1, 0, 0, ObjectType.PRM));
        run.runUntilFrame(100);

        assertTrue(run.events(SimEvent.Idle.class).size() >= 6, "it waits 16 frames at a time");
        assertEquals(MoveKind.IDLE, run.sim.world().object(3).moveKind());
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());
        assertFalse(MineBehaviour.triggers(run.sim.world().object(3)));
    }
}
