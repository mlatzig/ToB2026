package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class 7, the hopper ({@code L00B4B4}, {@code S:9594-9650}). It steers by +0E and does nothing while the world is
 * frozen or while it walks or waits. Standing, it tries right, straight, left and back in this order; the first free
 * one starts the step at once, without turn time. If all four are blocked it waits 16 frames. Contested cells are
 * decided by the claim frame (12) and list order, like every walker. Not pushable; zappable (a robot).
 */
public enum HopperBehaviour implements ElementBehaviour {
    INSTANCE;

    /** Frames of the wait when all four directions are blocked ({@code move.w #$10,$10(a0)}, {@code S:9649}). */
    public static final int WAIT_FRAMES = 16;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen() || self.isMoving()) {
            return;
        }
        Direction direction = self.moveDirection();
        for (Direction next : new Direction[] {direction.right(), direction, direction.left(), direction.opposite()}) {
            if (Steps.walk(ctx, self, next)) {
                return;
            }
        }
        ctx.startIdle(self, WAIT_FRAMES);
    }
}
