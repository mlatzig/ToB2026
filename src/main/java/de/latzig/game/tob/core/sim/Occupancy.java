package de.latzig.game.tob.core.sim;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live map cell → slot, the original occupancy byte {@code W002502} ($FF = free). A cell has at most one holder:
 * the object standing in it, the mover that claimed it, or a moving forcer lift ({@link #FORCER_LIFT}). A negative
 * holder is never an object slot.
 */
public final class Occupancy {

    public static final int FREE = -1;
    /**
     * The pseudo slot of a lift moved by a forcer ({@code $19}-{@code $1B} in the original, {@code S:9043},
     * {@code S:9082}): it holds both cells of the shaft while the lift moves. It is no object: a zap ray passes it, a
     * push or grab ray ends at it, and a mine is not triggered by it. An explosion does not destroy it; in the
     * original it marks the pseudo record as class $1A, after which push/grab rays would pass forcer-lift cells —
     * not reproduced (oracle-uncertain area, see the Phase 2 summary's open points).
     */
    public static final int FORCER_LIFT = -2;

    private final int[] holders = new int[CellPos.COUNT];

    Occupancy() {
        Arrays.fill(holders, FREE);
    }

    /** The holding slot, or {@link #FREE}; cells outside the grid report {@link #FREE}. */
    public int holder(CellPos pos) {
        return pos.inGrid() ? holders[pos.index()] : FREE;
    }

    /** True for an unheld cell inside the grid; cells outside the grid are never free (border). */
    public boolean isFree(CellPos pos) {
        return pos.inGrid() && holders[pos.index()] == FREE;
    }

    /** Every held cell with its holder, in grid index order. */
    public Map<CellPos, Integer> heldCells() {
        Map<CellPos, Integer> held = new LinkedHashMap<>();
        for (int index = 0; index < holders.length; index++) {
            if (holders[index] != FREE) {
                held.put(CellPos.ofIndex(index), holders[index]);
            }
        }
        return held;
    }

    void set(CellPos pos, int slot) {
        holders[pos.index()] = slot;
    }

    void release(CellPos pos) {
        holders[pos.index()] = FREE;
    }
}
