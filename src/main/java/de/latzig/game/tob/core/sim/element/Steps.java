package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/** The step start of the movers that steer by +0E (worm, watcher, hopper). */
final class Steps {

    private Steps() {
    }

    /**
     * Tries a step ({@link SimContext#tryStep}, {@code L00A638}); if it starts, the facing follows the walking direction
     * ({@code Turned}, only if it changes).
     */
    static boolean walk(SimContext ctx, SimObject mover, Direction direction) {
        if (!ctx.tryStep(mover, direction)) {
            return false;
        }
        if (mover.facing() != direction) {
            ctx.setFacing(mover, direction);
        }
        return true;
    }
}
