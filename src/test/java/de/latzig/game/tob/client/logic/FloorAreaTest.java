package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.TestTowers;

class FloorAreaTest {

    @Test
    void theFloorAreaDropsAndClipsEventsOnTheEmptyRing() {
        FloorArea area = FloorArea.of(TestTowers.tower().level(0, "FFF", "FFF").build());
        assertEquals(new FloorArea(4, 10, 6, 11), area);
        SimEvent.WiperSweep sweep = new SimEvent.WiperSweep(9, 3, Direction.WEST,
                List.of(new CellPos(4, 11, 0), new CellPos(3, 11, 0), new CellPos(2, 11, 0)));

        List<SimEvent> kept = area.filter(List.of(sweep, new SimEvent.FloorEaten(9, new CellPos(4, 11, 0)),
                new SimEvent.FloorEaten(9, new CellPos(3, 11, 0)), new SimEvent.ReadyEnded(9)));

        assertEquals(List.of(new SimEvent.WiperSweep(9, 3, Direction.WEST, List.of(new CellPos(4, 11, 0))),
                new SimEvent.FloorEaten(9, new CellPos(4, 11, 0)), new SimEvent.ReadyEnded(9)), kept);
    }
}
