package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.SimEvent;

class ExplosionTriggerTest {

    private static final CellPos CELL = new CellPos(5, 6, 1);

    @Test
    void aSecondDestroyedForTheSameObjectStartsNoSecondExplosion() {
        // Rules.blast re-emits Destroyed for a holder that is already being destroyed (Rules.java:177-185)
        ExplosionTrigger trigger = new ExplosionTrigger();

        assertEquals(List.of(new ExplosionTrigger.ObjectBlast(3)), trigger.onStep(List.of(
                new SimEvent.Destroyed(10, 3, CELL), new SimEvent.Exploded(10, 7, List.of(CELL)),
                new SimEvent.Destroyed(10, 3, CELL))));
        assertEquals(List.of(), trigger.onStep(List.of(new SimEvent.Destroyed(20, 3, CELL))), "re-armed later");
        assertEquals(List.of(new ExplosionTrigger.ObjectBlast(4)),
                trigger.onStep(List.of(new SimEvent.Destroyed(21, 4, CELL), new SimEvent.Removed(21, 3, CELL))));
    }

    @Test
    void everyEatenFloorBreaksApart() {
        ExplosionTrigger trigger = new ExplosionTrigger();
        CellPos other = new CellPos(6, 6, 1);

        assertEquals(List.of(new ExplosionTrigger.FloorBlast(CELL), new ExplosionTrigger.FloorBlast(other)),
                trigger.onStep(List.of(new SimEvent.FloorEaten(4, CELL), new SimEvent.ReadyEnded(4),
                        new SimEvent.FloorEaten(4, other))));
    }
}
