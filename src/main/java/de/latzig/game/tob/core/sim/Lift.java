package de.latzig.game.tob.core.sim;

import java.util.Objects;

/**
 * A lift shaft paired at load time from the lossless base codes: the lower and the upper cell, the platform position
 * and the rider. The cell flags stay authoritative for walking; this object is the view for clients and later phases.
 */
public final class Lift {

    private final CellPos lower;
    private final CellPos upper;
    private LiftState state;
    private int rider = Occupancy.FREE;

    Lift(CellPos lower, CellPos upper, LiftState state) {
        this.lower = Objects.requireNonNull(lower, "lower");
        this.upper = Objects.requireNonNull(upper, "upper");
        this.state = Objects.requireNonNull(state, "state");
    }

    public CellPos lower() {
        return lower;
    }

    public CellPos upper() {
        return upper;
    }

    public LiftState state() {
        return state;
    }

    /** Slot of the riding spider, or {@link Occupancy#FREE}. */
    public int rider() {
        return rider;
    }

    public boolean contains(CellPos pos) {
        return lower.equals(pos) || upper.equals(pos);
    }

    void update(LiftState newState, int newRider) {
        this.state = Objects.requireNonNull(newState, "newState");
        this.rider = newRider;
    }
}
