package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Phase 6b spec decision 6: "Freeze enemies" as the original designer's "Enemy Movement" off ({@code S:12823-12825}):
 * the freeze counter starts at −2, so the end of READY leaves the world frozen; the spiders still move.
 */
class FrozenEnemiesTest {

    private static final SpiderId Z = SpiderId.ZAPPER;

    /** A floor column x = 0, z = 5..7 with the zapper at (0,5) facing north; a worm E/W at (0,0) on F (0..2,0). */
    private static TowerDefinition tower() {
        return TestTowers.tower().level(0, "F", "F", "F", ".", ".", ".", ".", "FFF")
                .object(0, 5, 0, ObjectType.ZAP).object(0, 0, 0, ObjectType.WOE).build();
    }

    private static SimRun run(SimOptions options) {
        SimRun run = new SimRun(tower(), options);
        run.step(new Command.Fire(Z));
        for (int i = 0; i < 40; i++) {
            run.step(new Command.Forward(Z));
        }
        return run;
    }

    private static boolean moved(SimRun run, int slot) {
        return run.eventsOf(slot).stream().anyMatch(SimEvent.MoveStarted.class::isInstance);
    }

    @Test
    void frozenEnemiesNeverMoveWhileTheSpiderWalks() {
        SimRun run = run(new SimOptions(true, true));

        assertFalse(run.sim.isReady());
        assertTrue(run.sim.world().isFrozen());
        assertEquals(-1, run.sim.world().freezeCounter(), "−2 at the start, +1 at the end of READY");
        assertFalse(moved(run, 3), "the worm stands still");
        assertTrue(moved(run, 0), "the zapper walks");
    }

    @Test
    void withoutTheOptionTheWormWalksAsAlways() {
        SimRun run = run(new SimOptions(true));

        assertFalse(run.sim.world().isFrozen());
        assertEquals(0, run.sim.world().freezeCounter());
        assertTrue(moved(run, 3));
        assertTrue(moved(run, 0));
        assertFalse(SimOptions.DEFAULT.enemiesFrozen(), "a normal game never freezes the enemies");
    }

    @Test
    void theFreezeCounterStartsOneLowerWithTheEnemiesFrozen() {
        assertEquals(-1, WorldState.load(tower(), SimOptions.DEFAULT).freezeCounter());
        assertEquals(WorldState.FROZEN_FOR_GOOD, WorldState.load(tower(), new SimOptions(false, true)).freezeCounter());
    }
}
