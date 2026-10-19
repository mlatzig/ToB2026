package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.MoveKind;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Class 8, the proximity mine ({@code L00B59A}, {@code S:9651-9723}). Only while the world is not frozen and the mine
 * itself stands ({@code S:9651-9654}), it tests its four N/E/S/W neighbour cells: if the holder of one of them
 * (standing in it, having claimed it, or not yet having released it) walks or is being pushed (+10 ≠ 0 and +0E < 8,
 * {@code S:9669-9672}), the mine explodes. Standing, turning, firing, lift rides and a hopper's wait do not trigger it,
 * and diagonal cells are never tested. The explosion ({@code S:9682-9720}) destroys the holders of the four neighbour
 * cells, sparing lift riders, then the mine; in a tower whose mines destroy the floor, the mine's own floor goes when
 * its destruction ends. Nothing is counted. Pushable; not zappable.
 */
public enum MineBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        WorldState world = ctx.world();
        if (world.isFrozen() || self.isMoving()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            int holder = world.occupancy().holder(self.cell().step(direction));
            if (holder >= 0 && triggers(world.object(holder))) {
                ctx.explode(self, world.tower().behaviour().minesDestroyFloor());
                return;
            }
        }
    }

    /** Walking or being pushed: +10 ≠ 0 and +0E < 8 ({@code S:9669-9672}). A wait or a lift ride does not count. */
    public static boolean triggers(SimObject neighbour) {
        return neighbour.isMoving()
                && (neighbour.moveKind() == MoveKind.WALK || neighbour.moveKind() == MoveKind.PUSH);
    }
}
