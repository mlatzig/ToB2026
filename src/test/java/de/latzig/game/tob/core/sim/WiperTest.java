package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.WiperBehaviour;

/** Wiper (class $14; grab {@code S:8972-8980}, handler {@code S:9907-9944}; oracle §6.3, Phase 2d spec §3.5). */
class WiperTest {

    private final List<SimEvent> out = new ArrayList<>();
    private WorldState world;
    private SimContext ctx;
    private SimObject wiper;
    private SimObject grabber;

    /** A plus of floor on level 0: row z=3 and column x=3 (0..6 each); wiper (3,3) slot 3; READY over. */
    private void start() {
        world = WorldState.load(TestTowers.tower()
                .level(0, "...F...", "...F...", "...F...", "FFFFFFF", "...F...", "...F...", "...F...")
                .object(3, 3, 0, ObjectType.WIP).build(), SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
        world.thaw();
        wiper = world.object(3);
        grabber = world.spider(SpiderId.GRABBER);
    }

    @Test
    void aGrabSweepsWestSouthEastAndNorthEverySixteenHandlerCalls() {
        start();
        assertTrue(WiperBehaviour.INSTANCE.onGrab(ctx, wiper, grabber));
        assertEquals(WiperBehaviour.TIMER, wiper.classTimer());

        List<String> sweeps = new ArrayList<>();
        for (int call = 1; call <= 64; call++) {
            out.clear();
            WiperBehaviour.INSTANCE.act(ctx, wiper);
            for (SimEvent event : out) {
                if (event instanceof SimEvent.WiperSweep sweep) {
                    sweeps.add(call + " " + sweep.direction());
                }
            }
        }

        assertEquals(List.of("15 WEST", "31 SOUTH", "47 EAST", "63 NORTH"), sweeps);
        assertEquals(0, wiper.classTimer());
        for (int i = 0; i <= 6; i++) {
            assertEquals(i == 3, world.hasFloor(pos(i, 3, 0)), "row cell " + i);
            assertEquals(i == 3, world.hasFloor(pos(3, i, 0)), "column cell " + i);
        }
        assertTrue(wiper.isAlive(), "objects are never touched");
    }

    @Test
    void aGrabNeedsAnIdleWiperAndTheTimerStandsStillWhileFrozen() {
        start();
        assertTrue(WiperBehaviour.INSTANCE.onGrab(ctx, wiper, grabber));
        assertFalse(WiperBehaviour.INSTANCE.onGrab(ctx, wiper, grabber), "running (S:8976-8977)");
        world.freeze();
        for (int call = 0; call < 5; call++) {
            WiperBehaviour.INSTANCE.act(ctx, wiper);
        }
        assertEquals(WiperBehaviour.TIMER, wiper.classTimer());
        world.thaw();

        WiperBehaviour.INSTANCE.act(ctx, wiper);

        assertEquals(WiperBehaviour.TIMER - 1, wiper.classTimer());
    }

    @Test
    void aWiperBeingPushedCannotBeGrabbed() {
        start();
        assertTrue(Push.start(world, wiper, Direction.EAST, out));

        assertFalse(WiperBehaviour.INSTANCE.onGrab(ctx, wiper, grabber), "moving (S:8974-8975)");
        assertEquals(0, wiper.classTimer());
    }
}
