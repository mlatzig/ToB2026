package de.latzig.game.tob.core.sim;

import java.util.List;
import java.util.Set;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Walking one cell: start ({@code L00A638}, {@code S:8639-8657}), one movement frame ({@code L00BAAC},
 * {@code S:9983-10038}) and back while moving ({@code L00A70A}, {@code S:8695-8721}). The counter counts down from
 * 32. With the claim value c: claim (or reverse) at counter 32 − c, half-way at 16, release of the old cell at c,
 * standing at 0.
 */
public final class Walk {

    public static final int FRAMES = 32;
    public static final int HALF_WAY = 16;
    public static final int UNITS_PER_FRAME = 16;

    /** Back while moving is refused at these counters (half-way, release, claim; {@code S:8699-8704}). */
    static final Set<Integer> REFUSED_REVERSE = Set.of(16, 10, 22);

    private Walk() {
    }

    /** Claim value c from table {@code \00BC56} ({@code S:10096-10098}). */
    public static int claimValue(ObjectType type) {
        return switch (type.family()) {
            case ZAPPER, PUSHER, GRABBER -> 10;
            case WATCHER -> 11;
            case HOPPER -> 12;
            case WORM, LIZARD -> 2;
            default -> throw new IllegalArgumentException(type.code() + " does not walk");
        };
    }

    /** Starts a step if the mover stands and the target is floor, not blocked and unheld. Does not claim. */
    static boolean start(WorldState world, SimObject mover, Direction direction, List<SimEvent> out) {
        if (mover.isMoving()) {
            return false;
        }
        CellPos target = mover.cell().step(direction);
        if (!world.isWalkable(target) || !world.occupancy().isFree(target)) {
            return false;
        }
        mover.setMoveKind(MoveKind.WALK);
        mover.setMoveDirection(direction);
        mover.setMoveCounter(FRAMES);
        out.add(new SimEvent.MoveStarted(world.frame(), mover.slot(), mover.cell(), target));
        return true;
    }

    /** One movement frame. */
    static void advance(WorldState world, SimObject mover, List<SimEvent> out) {
        Direction direction = mover.moveDirection();
        mover.moveBy(direction.dx() * UNITS_PER_FRAME, 0, direction.dy() * UNITS_PER_FRAME);
        int counter = mover.moveCounter() - 1;
        mover.setMoveCounter(counter);
        if (counter == 0) {
            out.add(new SimEvent.MoveFinished(world.frame(), mover.slot(), mover.cell()));
            return;
        }
        if (counter == HALF_WAY) {
            CellPos left = mover.cell();
            if (mover.secondCell() == null) {
                throw new IllegalStateException("half-way without a claimed cell: slot " + mover.slot());
            }
            mover.setCell(mover.secondCell());
            mover.setSecondCell(left);
            out.add(new SimEvent.CellChanged(world.frame(), mover.slot(), left, mover.cell()));
            return;
        }
        int claim = claimValue(mover.type());
        if (counter == claim) {
            if (mover.secondCell() != null) {
                world.occupancy().release(mover.secondCell());
                mover.setSecondCell(null);
            }
            return;
        }
        if (counter == FRAMES - claim) {
            CellPos target = mover.cell().step(direction);
            if (world.flags(target) != 0 && world.occupancy().isFree(target)) {
                world.occupancy().set(target, mover.slot());
                mover.setSecondCell(target);
                out.add(new SimEvent.MoveClaimed(world.frame(), mover.slot(), target));
            } else {
                mover.setMoveDirection(direction.opposite());
                mover.setMoveCounter(FRAMES - counter);
                out.add(new SimEvent.MoveReversed(world.frame(), mover.slot()));
            }
        }
    }

    /** Back while walking: reverses unless the counter is 16, 10 or 22. */
    static boolean reverse(WorldState world, SimObject mover, List<SimEvent> out) {
        if (REFUSED_REVERSE.contains(mover.moveCounter())) {
            return false;
        }
        return turnBack(world, mover, out);
    }

    /**
     * Reverses a walk at any counter: {@code dir ^= 2}, {@code +10 := 32 − +10} (the arithmetic of {@code S:8848-8851}
     * and {@code S:10023-10027}). Refused only if the mover is not walking.
     */
    static boolean turnBack(WorldState world, SimObject mover, List<SimEvent> out) {
        if (!mover.isMoving() || mover.moveKind() != MoveKind.WALK) {
            return false;
        }
        mover.setMoveDirection(mover.moveDirection().opposite());
        mover.setMoveCounter(FRAMES - mover.moveCounter());
        out.add(new SimEvent.MoveReversed(world.frame(), mover.slot()));
        return true;
    }
}
