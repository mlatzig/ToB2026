package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.FreezerBehaviour;

/** Freezer (class $13; grab {@code S:8961-8971}, handler {@code S:9882-9906}; oracle §6.1, Phase 2d spec §3.3). */
class FreezerTest {

    private final List<SimEvent> out = new ArrayList<>();
    private WorldState world;
    private SimContext ctx;
    private SimObject grabber;

    /** F (0..2,0): grabber (0,0) slot 2, freezer A (1,0) slot 3, freezer B (2,0) slot 4; 3 robots; READY over. */
    private void start() {
        world = WorldState.load(TestTowers.tower().level(0, "FFF").object(0, 0, 0, ObjectType.GRB)
                .object(1, 0, 0, ObjectType.FRZ).object(2, 0, 0, ObjectType.FRZ).conditions(0, false, 0, 3).build(),
                SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
        world.thaw();
        grabber = world.spider(SpiderId.GRABBER);
    }

    private void act(SimObject freezer, int calls) {
        for (int call = 0; call < calls; call++) {
            FreezerBehaviour.INSTANCE.act(ctx, freezer);
        }
    }

    @Test
    void aGrabFreezesForSixtyFourHandlerCallsAndRechargesForAnotherSixtyFour() {
        start();
        SimObject freezer = world.object(3);

        assertTrue(FreezerBehaviour.INSTANCE.onGrab(ctx, freezer, grabber));
        assertEquals(Rules.FREEZER_TIMER, freezer.classTimer());
        assertEquals(-1, world.freezeCounter());
        assertFalse(FreezerBehaviour.INSTANCE.onGrab(ctx, freezer, grabber), "only at timer 0 (S:8963-8964)");
        act(freezer, 63);
        assertTrue(world.isFrozen());
        assertEquals(65, freezer.classTimer());
        act(freezer, 1);
        assertFalse(world.isFrozen());
        assertEquals(Rules.FREEZER_THAW, freezer.classTimer());
        act(freezer, 63);
        assertFalse(FreezerBehaviour.INSTANCE.onGrab(ctx, freezer, grabber), "timer 1: still recharging");
        act(freezer, 1);
        assertEquals(0, freezer.classTimer());
        act(freezer, 5);
        assertEquals(0, freezer.classTimer(), "a timer of 0 stays");
        assertTrue(FreezerBehaviour.INSTANCE.onGrab(ctx, freezer, grabber));

        assertEquals(List.of(new SimEvent.Frozen(0, 3), new SimEvent.Thawed(0, 3), new SimEvent.Frozen(0, 3)), out);
    }

    @Test
    void twoActiveFreezersStack() {
        start();
        SimObject a = world.object(3);
        SimObject b = world.object(4);

        FreezerBehaviour.INSTANCE.onGrab(ctx, a, grabber);
        act(a, 30);
        FreezerBehaviour.INSTANCE.onGrab(ctx, b, grabber);
        assertEquals(-2, world.freezeCounter());
        act(a, 34);
        assertEquals(-1, world.freezeCounter(), "A thawed, B still freezes");
        assertTrue(world.isFrozen());
        act(b, 63);
        assertTrue(world.isFrozen());
        act(b, 1);

        assertFalse(world.isFrozen());
        assertEquals(0, world.freezeCounter());
    }

    @Test
    void aZapInTheFreezePhaseThawsButAZapInTheRechargePhaseOnlyDestroys() {
        start();
        SimObject a = world.object(3);
        SimObject b = world.object(4);
        FreezerBehaviour.INSTANCE.onGrab(ctx, a, grabber);
        act(a, 10);
        FreezerBehaviour.INSTANCE.onGrab(ctx, b, grabber);
        act(b, 64);
        assertEquals(-1, world.freezeCounter(), "B is recharging, A freezes");
        out.clear();

        Rules.zap(world, b, out);
        assertEquals(-1, world.freezeCounter(), "deviation 4: the original would thaw here");
        Rules.zap(world, a, out);

        assertEquals(0, world.freezeCounter());
        assertEquals(0, a.classTimer());
        assertEquals(0, b.classTimer());
        assertEquals(List.of(new SimEvent.Destroyed(0, 4, pos(2, 0, 0)),
                new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1), new SimEvent.Destroyed(0, 3, pos(1, 0, 0)),
                new SimEvent.Thawed(0, 3), new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 2)), out);
    }

    /**
     * Fix round 1 (review finding, Important): a freezer grabbed as the very first action, in READY (oracle §6.1:
     * "Grabbing a freezer as the very first action (in READY): the world stays frozen until the freezer reaches
     * 64."). Drives the real {@link Simulation#step} pipeline (no manual {@code thaw()}), unlike the fixtures above.
     * <p>
     * F (0,0) grabber facing N (default); F (0,1) freezer, directly north of it, so the very first {@code Fire}
     * both ends READY and grabs it; an isolated worm E/W at (2,0) (slot 3, blocked on both sides, so it flips
     * direction every unfrozen frame) sits at a lower slot than the freezer (slot 4), so it is tested for frozen
     * state <i>before</i> the freezer's own handler call in the same frame, the "all other handlers" case of
     * §6.1, which resumes only in frame n+64, not the "handlers after the freezer in the list" case of n+63.
     */
    @Test
    void aFreezerGrabbedInReadyKeepsTheWorldFrozenUntilItsHandlerReachesSixtyFour() {
        SimRun run = new SimRun(TestTowers.tower().level(0, "F", "F.F").object(0, 0, 0, ObjectType.GRB)
                .object(2, 0, 0, ObjectType.WOE).object(0, 1, 0, ObjectType.FRZ).conditions(0, false, 0, 0).build());
        WorldState world = run.sim.world();
        int wormSlot = 3;
        int freezerSlot = 4;

        List<SimEvent> grabFrame = run.step(new Command.Fire(SpiderId.GRABBER));

        assertEquals(0, run.sim.frame(), "still the READY-ending frame, oracle frame 0");
        assertEquals(Rules.FREEZER_TIMER - 1, world.object(freezerSlot).classTimer(),
                "128 right after the grab, but the freezer's own handler already runs once in this same frame "
                        + "(it is processed in runHandlers after the grab, within the same step)");
        assertTrue(grabFrame.contains(new SimEvent.Frozen(0, freezerSlot)));
        assertEquals(-1, world.freezeCounter(),
                "READY's own -1 and the grab's -1 (-2 together) minus the single READY-end thaw's +1: still -1, "
                        + "not 0, so the world stays frozen past READY's own end");
        assertTrue(world.isFrozen());

        run.runUntilFrame(62);
        assertEquals(-1, world.freezeCounter(), "frame 62: the freezer's timer is still 65, one call short of 64");
        assertTrue(world.isFrozen());
        assertTrue(run.eventsOf(wormSlot).isEmpty(), "blocked worm stays still the whole time it is frozen");

        List<SimEvent> frame63 = run.step();
        assertEquals(63, run.sim.frame(), "the freezer's handler reaches 64 in frame n+63 (n = 0)");
        assertTrue(frame63.contains(new SimEvent.Thawed(63, freezerSlot)));
        assertEquals(0, world.freezeCounter());
        assertFalse(world.isFrozen());
        assertTrue(run.eventsOf(wormSlot).isEmpty(),
                "the worm's own handler runs before the freezer's in the same frame 63 and still saw it frozen");

        List<SimEvent> frame64 = run.step();
        assertEquals(64, run.sim.frame(), "movement (here: the worm's own handler) resumes only in frame n+64");
        assertEquals(List.of(new SimEvent.Turned(64, wormSlot, Direction.WEST)),
                frame64.stream().filter(SimEvent.Turned.class::isInstance).toList());

        run.runUntilFrame(70);
        assertEquals(0, world.freezeCounter(), "stays exactly 0, never positive, with no second freezer and no zap");
    }
}
