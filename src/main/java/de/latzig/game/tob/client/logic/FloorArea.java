package de.latzig.game.tob.client.logic;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.SimEvent;

/**
 * The tower's floor area: the smallest x/y rectangle holding every non-empty cell of the start grid. Many original
 * towers fill the whole 8 × 8 area at 4..11, but 55 of 117 do not (see {@code CameraPlacement}, which therefore does
 * not use this area for the camera origin). Floor-loss events and wiper sweep cells outside it, the empty ring the
 * simulation reports up to the grid edge, are dropped before they reach the views (spec §4.4, Phase 2 carry-over 9).
 */
public record FloorArea(int minX, int minY, int maxX, int maxY) {

    public static FloorArea of(TowerDefinition tower) {
        int minX = TowerGrid.WIDTH;
        int minY = TowerGrid.DEPTH;
        int maxX = -1;
        int maxY = -1;
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    if (!Cell.EMPTY.equals(tower.grid().cell(x, y, level))) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
        }
        return new FloorArea(minX, minY, maxX, maxY);
    }

    public boolean contains(CellPos pos) {
        return pos.x() >= minX && pos.x() <= maxX && pos.y() >= minY && pos.y() <= maxY;
    }

    /** The events with floor losses outside the area dropped and wiper sweeps clipped to it. */
    public List<SimEvent> filter(List<SimEvent> events) {
        List<SimEvent> kept = new ArrayList<>(events.size());
        for (SimEvent event : events) {
            switch (event) {
                case SimEvent.FloorEaten eaten -> {
                    if (contains(eaten.cell())) {
                        kept.add(eaten);
                    }
                }
                case SimEvent.WiperSweep sweep -> kept.add(new SimEvent.WiperSweep(sweep.frame(), sweep.slot(),
                        sweep.direction(), sweep.cells().stream().filter(this::contains).toList()));
                default -> kept.add(event);
            }
        }
        return kept;
    }
}
