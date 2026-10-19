package de.latzig.game.tob.core.sim;

import java.util.List;

/**
 * Being pushed one cell: start by a push ray ({@code S:8890-8911}) and one movement frame ({@code L00BAAC} push
 * branch, {@code S:10039-10066}). The cell beyond is claimed at once, when the ray hits. The counter counts down from
 * 16: the current cell switches at 8 (half-way) and the old cell is freed at 0. The motion follows the ease table
 * {@code \00BC6A} ({@code S:10099-10100}), whose last three steps are 0 (13 frames of visible motion).
 */
public final class Push {

    public static final int FRAMES = 16;
    public static final int HALF_WAY = 8;

    /** Table {@code \00BC6A} ({@code S:10099-10100}), indexed by counter − 1; sum 512 = one cell. */
    private static final int[] EASE = {0, 0, 0, 4, 8, 14, 22, 30, 38, 46, 54, 62, 64, 64, 64, 42};

    private Push() {
    }

    /** World units of the frame that starts at {@code counter} (16..1). */
    public static int ease(int counter) {
        return EASE[counter - 1];
    }

    /**
     * The push test of the ray ({@code S:8890-8900}): the target stands, and the cell beyond it in {@code direction}
     * has floor, is inside the grid (not border), is not blocked and is neither occupied nor claimed.
     */
    static boolean canStart(WorldState world, SimObject target, Direction direction) {
        CellPos beyond = target.cell().step(direction);
        return !target.isMoving() && world.hasFloor(beyond) && !world.isBlocked(beyond)
                && world.occupancy().isFree(beyond);
    }

    /**
     * Starts the push if the target is alive and {@link #canStart} allows it: claims the cell beyond at once
     * ({@code S:8901-8907}). The alive guard (2b carry-over M5) protects behaviours that call
     * {@code SimContext.startPush} directly; the ray already skips objects that are being destroyed.
     */
    static boolean start(WorldState world, SimObject target, Direction direction, List<SimEvent> out) {
        if (!target.isAlive() || !canStart(world, target, direction)) {
            return false;
        }
        CellPos beyond = target.cell().step(direction);
        target.setMoveKind(MoveKind.PUSH);
        target.setMoveDirection(direction);
        target.setMoveCounter(FRAMES);
        target.setSecondCell(beyond);
        world.occupancy().set(beyond, target.slot());
        out.add(new SimEvent.PushStarted(world.frame(), target.slot(), target.cell(), beyond));
        return true;
    }

    /** One push frame. */
    static void advance(WorldState world, SimObject object, List<SimEvent> out) {
        Direction direction = object.moveDirection();
        int units = ease(object.moveCounter());
        object.moveBy(direction.dx() * units, 0, direction.dy() * units);
        int counter = object.moveCounter() - 1;
        object.setMoveCounter(counter);
        if (counter == 0) {
            world.occupancy().release(object.secondCell());
            object.setSecondCell(null);
            out.add(new SimEvent.PushFinished(world.frame(), object.slot(), object.cell()));
        } else if (counter == HALF_WAY) {
            CellPos left = object.cell();
            object.setCell(object.secondCell());
            object.setSecondCell(left);
            out.add(new SimEvent.CellChanged(world.frame(), object.slot(), left, object.cell()));
        }
    }
}
