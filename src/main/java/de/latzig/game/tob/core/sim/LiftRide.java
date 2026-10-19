package de.latzig.game.tob.core.sim;

import java.util.List;

/**
 * A spider's lift ride: start ({@code L00A638} lift branch, {@code S:8658-8692}), one frame ({@code L00BAAC} lift
 * branch, {@code S:10067-10093}) and reversal ({@code L00A70A}, {@code S:8706-8721}). 32 frames with the eased
 * vertical table {@code L00BC7A}. Up: the cell above gets floor and is claimed at once; at the end the lower cell
 * becomes blocked. Down: the lower cell is unblocked and claimed at once; at the end the upper cell loses its floor.
 * The occupancy of the start cell is freed at the end. The original never checks the far cell for an occupant
 * (normally it has no floor before the ride). ToB 2026 refuses the ride if another slot holds it (deviation 2, see
 * {@link #start}).
 * <p>
 * <b>Deviation 2</b> ({@code S:8664-8686}, {@code S:8981-9022}): an exchange during a ride (
 * {@code ExchangerBehaviour.onGrab} &rarr; {@code SimContext.swap}/{@code stop}) stops the ride mid-shaft and leaves
 * the cell flags half-switched: the far cell keeps the floor (or lost block) it got at ride start, and the near cell
 * never gets its end-of-ride flags, so both shaft cells stay walkable without the rider holding either of them. The
 * original always overwrites the far cell's occupancy at the next ride start ({@code S:8664-8686} sets
 * {@code $190(a2)} unconditionally), which can silently steal the cell from whatever walked onto it in between. ToB
 * 2026 instead refuses a later {@link #start} when the far cell is held or claimed by another slot; the rider's own
 * claim is not a conflict. Everything else about the half-switched shaft (the flags, the stuck {@code Lift} view
 * state) stays faithful to the original.
 */
public final class LiftRide {

    public static final int FRAMES = 32;

    /** Table {@code L00BC7A} ({@code S:10101-10104}), indexed by counter − 1; sum 512 = one level. */
    private static final int[] EASE = {3, 7, 13, 17, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18,
            18, 18, 18, 18, 18, 18, 18, 18, 17, 13, 7, 3};

    private LiftRide() {
    }

    /** Vertical world units of the frame that starts at {@code counter} (32..1). */
    public static int ease(int counter) {
        return EASE[counter - 1];
    }

    /**
     * Starts a ride from the rider's lift cell: up if the cell's {@link CellFlags#LIFT_DOWN} bit is clear, else down.
     * False if the rider moves, the cell is no lift, or the far shaft cell is held or claimed by another slot
     * (deviation 2).
     *
     * @throws IllegalStateException on a cell without floor or a shaft leaving the grid (the original hangs,
     *         {@code S:8687-8688})
     */
    static boolean start(WorldState world, SimObject rider, List<SimEvent> out) {
        if (rider.isMoving()) {
            return false;
        }
        CellPos cell = rider.cell();
        if (!world.hasFloor(cell)) {
            throw new IllegalStateException("lift ride from a cell without floor: " + cell);
        }
        if (!world.isLift(cell)) {
            return false;
        }
        boolean down = world.liftRidesDown(cell);
        CellPos other = down ? cell.below() : cell.above();
        if (!other.inGrid()) {
            throw new IllegalStateException("lift shaft at " + cell + " leaves the grid");
        }
        int otherHolder = world.occupancy().holder(other);
        if (otherHolder != Occupancy.FREE && otherHolder != rider.slot()) {
            // Deviation 2: a half-switched shaft (an exchange stopped a previous ride mid-shaft) must not let a
            // later ride overwrite whatever now holds or claims the far cell.
            return false;
        }
        if (down) {
            world.clearFlags(other, CellFlags.BLOCKED);
        } else {
            world.setFlags(other, CellFlags.FLOOR);
        }
        world.occupancy().set(other, rider.slot());
        rider.setSecondCell(other);
        rider.setMoveKind(down ? MoveKind.LIFT_DOWN : MoveKind.LIFT_UP);
        rider.setMoveCounter(FRAMES);
        world.liftAt(cell).ifPresent(
                lift -> lift.update(down ? LiftState.MOVING_DOWN : LiftState.MOVING_UP, rider.slot()));
        out.add(new SimEvent.LiftRideStarted(world.frame(), rider.slot(), cell, other));
        return true;
    }

    /** One ride frame. */
    static void advance(WorldState world, SimObject rider, List<SimEvent> out) {
        boolean down = rider.moveKind() == MoveKind.LIFT_DOWN;
        int rise = ease(rider.moveCounter());
        rider.moveBy(0, down ? -rise : rise, 0);
        int counter = rider.moveCounter() - 1;
        rider.setMoveCounter(counter);
        if (counter != 0) {
            return;
        }
        CellPos left = rider.cell();
        if (down) {
            world.clearFlags(left, CellFlags.FLOOR);
        } else {
            world.setFlags(left, CellFlags.BLOCKED);
        }
        world.occupancy().release(left);
        rider.setCell(rider.secondCell());
        rider.setSecondCell(null);
        world.liftAt(left).ifPresent(lift -> lift.update(down ? LiftState.BOTTOM : LiftState.TOP, Occupancy.FREE));
        out.add(new SimEvent.CellChanged(world.frame(), rider.slot(), left, rider.cell()));
        out.add(new SimEvent.LiftRideFinished(world.frame(), rider.slot(), rider.cell()));
    }

    /**
     * Back while riding: reverses at any counter below 32 (the refusal counters apply to walks only). A ride at
     * counter 32 has not moved yet; the original would set +10 := 32 − 32 = 0 ({@code S:8706-8717}) and leave the
     * rider standing with both shaft cells held, so ToB 2026 refuses (2a carry-over 7). Every ride advances in the
     * pipeline step that started it, so the guard is defensive.
     */
    static boolean reverse(WorldState world, SimObject rider, List<SimEvent> out) {
        if (!rider.isRiding() || rider.moveCounter() == FRAMES) {
            return false;
        }
        CellPos cell = rider.cell();
        rider.setCell(rider.secondCell());
        rider.setSecondCell(cell);
        boolean nowDown = rider.moveKind() == MoveKind.LIFT_UP;
        rider.setMoveKind(nowDown ? MoveKind.LIFT_DOWN : MoveKind.LIFT_UP);
        rider.setMoveCounter(FRAMES - rider.moveCounter());
        world.liftAt(cell).ifPresent(
                lift -> lift.update(nowDown ? LiftState.MOVING_DOWN : LiftState.MOVING_UP, rider.slot()));
        out.add(new SimEvent.MoveReversed(world.frame(), rider.slot()));
        return true;
    }
}
