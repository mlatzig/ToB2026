package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Oracle §10 T-15, T-16, T-17 and the flag; frame 0 is the READY-ending frame. */
class GrabTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId G = SpiderId.GRABBER;

    /** Turns the spider east in READY and waits out the 4-frame turn throttle (still READY, frame 0). */
    private static SimRun facingEast(TestTowers.Builder tower, SpiderId spider) {
        SimRun run = new SimRun(tower.build());
        run.step(new Command.TurnRight(spider));
        run.step();
        run.step();
        run.step();
        assertTrue(run.sim.isReady());
        return run;
    }

    @Test
    void t15TheExchangerSwapsTheGrabberAndCountsAsARobot() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.GRB)
                .object(3, 0, 0, ObjectType.EXC).conditions(0, false, 0, 1), G);
        WorldState world = run.sim.world();
        SimObject grabber = world.spider(G);
        SimObject exchanger = world.object(3);

        List<SimEvent> frame0 = run.step(new Command.Fire(G));

        assertEquals(pos(3, 0, 0), grabber.cell());
        assertEquals(Direction.WEST, grabber.facing());
        assertEquals(7 * SimObject.UNITS_PER_CELL, grabber.worldX());
        assertEquals(pos(0, 0, 0), exchanger.cell());
        assertEquals(4 * SimObject.UNITS_PER_CELL, exchanger.worldX());
        assertEquals(ObjectStatus.DESTROYING, exchanger.status());
        assertEquals(2, world.occupancy().holder(pos(3, 0, 0)));
        assertEquals(3, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(1, world.counters().robotsDestroyed());
        assertEquals(Rules.EXIT_COMPLETED, world.exitCode());
        assertEquals(List.of(new SimEvent.Exchanged(0, 2, 3), new SimEvent.Turned(0, 2, Direction.WEST),
                new SimEvent.Destroyed(0, 3, pos(0, 0, 0)), new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1),
                new SimEvent.ExitPending(0, Rules.EXIT_COMPLETED, 23),
                new SimEvent.RayHit(0, 2, 3, RayOutcome.GRABBED)),
                frame0.subList(3, frame0.size()));
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void t15TheExchangerStopsAWalkingGrabber() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.GRB)
                .object(3, 0, 0, ObjectType.EXC), G);
        WorldState world = run.sim.world();
        SimObject grabber = world.spider(G);
        run.step(new Command.Forward(G));
        run.runUntilFrame(19);
        assertEquals(pos(1, 0, 0), grabber.cell(), "past half-way, the old cell (0,0) not yet released");
        assertEquals(pos(0, 0, 0), grabber.secondCell());

        run.step(new Command.Fire(G));

        assertFalse(grabber.isMoving());
        assertNull(grabber.secondCell());
        assertEquals(pos(3, 0, 0), grabber.cell());
        assertEquals(pos(1, 0, 0), world.object(3).cell());
        assertTrue(world.occupancy().isFree(pos(0, 0, 0)));
        assertEquals(List.of(), world.occupancyProblems());
        run.runUntilFrame(25);
        assertEquals(pos(3, 0, 0), grabber.cell(), "stopped for good");
    }

    @Test
    void anExchangerReflectsAZapAndSurvivesIt() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(3, 0, 0, ObjectType.EXC), Z);

        run.step(new Command.Fire(Z));

        assertEquals(ObjectStatus.ALIVE, run.sim.world().object(3).status());
        assertEquals(ObjectStatus.DESTROYING, run.sim.world().spider(Z).status(), "its own reflected ray");
        assertTrue(run.events.contains(new SimEvent.RayHit(0, 0, 3, RayOutcome.REFLECTED)));
    }

    @Test
    void t16AGrabbedKlondikeIsCountedAndGoneSevenFramesLater() {
        // a fixed zapper at (2,1) facing S shoots across the vanishing klondike from frame 1 on (deviation 1)
        SimRun run = facingEast(TestTowers.tower().level(0, "..F", "FFF").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.KLD).object(2, 1, 0, ObjectType.FZS).conditions(0, false, 2, 1), G);
        WorldState world = run.sim.world();
        SimObject klondike = world.object(3);

        run.step(new Command.Fire(G));
        assertEquals(1, world.counters().klondikesCollected());
        assertTrue(run.events.contains(new SimEvent.Collected(0, 3, 2)));
        assertTrue(klondike.isVanishing());
        for (int frame = 1; frame <= 6; frame++) {
            run.step();
            assertEquals(3, world.occupancy().holder(pos(2, 0, 0)), "frame " + frame);
            assertEquals(7 - frame, klondike.destroyCounter(), "frame " + frame);
        }
        assertEquals(List.of(new SimEvent.Removed(7, 3, pos(2, 0, 0))),
                run.step().stream().filter(SimEvent.Removed.class::isInstance).toList());

        assertTrue(world.occupancy().isFree(pos(2, 0, 0)));
        assertEquals(ObjectStatus.REMOVED, klondike.status());
        assertEquals(0, world.counters().robotsDestroyed(), "the enemy zaps passed the vanishing klondike");
        assertTrue(run.events(SimEvent.RayHit.class).stream().noneMatch(hit -> hit.slot() == 4));
    }

    @Test
    void t16AProgramFireFireCollectsTheKlondikeOnlyOnce() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.KLD).conditions(0, false, 2, 0), G);
        run.writeProgram(G, ProgramStep.FIRE, ProgramStep.FIRE);

        run.step(new Command.Run(G));
        run.step();

        assertEquals(1, run.sim.world().counters().klondikesCollected(), "deviation 1; the original counts 2");
        assertEquals(2, run.events(SimEvent.SpiderFired.class).size());
        assertEquals(List.of(new SimEvent.RayHit(0, 2, 3, RayOutcome.GRABBED)), run.events(SimEvent.RayHit.class));
    }

    @Test
    void t17AZappedKlondikeCountsAsARobot() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.KLD).conditions(0, false, 0, 1), Z);

        run.step(new Command.Fire(Z));

        assertEquals(ObjectStatus.DESTROYING, run.sim.world().object(3).status());
        assertEquals(1, run.sim.world().counters().robotsDestroyed());
        assertEquals(Rules.EXIT_COMPLETED, run.sim.exitCode());
    }

    @Test
    void aFlagIsZappableAndCountsAsARobot() {
        SimRun run = facingEast(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.BFL).conditions(0, false, 0, 2), Z);

        run.step(new Command.Fire(Z));
        run.runUntilFrame(32);

        assertEquals(1, run.sim.world().counters().robotsDestroyed());
        assertEquals(List.of(new SimEvent.Removed(31, 3, pos(2, 0, 0))), run.events(SimEvent.Removed.class));
    }
}
