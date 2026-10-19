package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared floor-removal rule {@code L00BA52} ({@code S:9956-9982}): the lizard (Phase 2c) and later the wiper and the
 * mine (Phase 2d). A cell's floor goes only if the cell has floor, is not a lift cell, is neither held nor claimed, and
 * is on level 0 or above a cell that is not blocked, so a floor on top of a supporting block survives. Removal clears
 * the whole cell base ({@code clr.b (a4)}, {@code S:9969}). The original then moves the render-only shadow bit 2 down
 * to the next cell with a base ({@code S:9970-9981}); ToB 2026 keeps no shadow bit.
 */
public final class FloorRule {

    private static final int BASE = CellFlags.FLOOR | CellFlags.LIFT | CellFlags.LIFT_DOWN | CellFlags.BLOCKED;

    private FloorRule() {
    }

    /** Whether the rule lets the floor of {@code cell} go now. */
    public static boolean canRemove(WorldState world, CellPos cell) {
        return world.hasFloor(cell) && !world.isLift(cell) && world.occupancy().isFree(cell)
                && (cell.level() == 0 || !world.isBlocked(cell.below()));
    }

    /**
     * A wiper sweep ({@code S:9918-9931}): from the wiper's current cell outward in {@code direction}, every cell until
     * the grid edge (the original border, bit 0) or the first blocked cell (bit 3), which ends the sweep untouched.
     * Emits {@link SimEvent.WiperSweep} with the cells passed, then applies the rule to each of them in order. Objects
     * are never touched.
     */
    static void sweep(WorldState world, SimObject wiper, Direction direction, List<SimEvent> out) {
        List<CellPos> cells = new ArrayList<>();
        for (CellPos cell = wiper.cell().step(direction); cell.inGrid() && !world.isBlocked(cell);
                cell = cell.step(direction)) {
            cells.add(cell);
        }
        out.add(new SimEvent.WiperSweep(world.frame(), wiper.slot(), direction, cells));
        for (CellPos cell : cells) {
            remove(world, cell, out);
        }
    }

    /** Removes the floor if {@link #canRemove} allows it and emits {@link SimEvent.FloorEaten}. */
    static boolean remove(WorldState world, CellPos cell, List<SimEvent> out) {
        if (!canRemove(world, cell)) {
            return false;
        }
        world.clearFlags(cell, BASE);
        out.add(new SimEvent.FloorEaten(world.frame(), cell));
        return true;
    }
}
