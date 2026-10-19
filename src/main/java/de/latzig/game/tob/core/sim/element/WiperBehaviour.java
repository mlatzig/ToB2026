package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class $14, the wiper: grab ({@code S:8972-8980}) and handler ({@code L00B994}, {@code S:9907-9944}). A grab works
 * only while the wiper stands and its timer +1C is 0; it sets 63. While the world is not frozen and the timer is not
 * 0, the handler counts it down; at 48, 32, 16 and 0 ({@code (timer & $30) == timer}) it sweeps direction
 * {@code (timer >> 4) & 3}: W, S, E, N, one every 16 frames. A grab in frame n sweeps in frames n+14, n+30, n+46 and
 * n+62. A sweep removes floors by the shared rule from the wiper's current cell outward until the grid edge or the
 * first blocked cell; objects are never touched. Pushable and zappable (a robot).
 */
public enum WiperBehaviour implements ElementBehaviour {
    INSTANCE;

    /** The timer after a grab ({@code move.w #$3f,$1c(a2)}, {@code S:8978}). */
    public static final int TIMER = 63;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen() || self.classTimer() == 0) {
            return;
        }
        int timer = self.classTimer() - 1;
        ctx.setClassTimer(self, timer);
        if ((timer & 0x30) == timer) {
            ctx.sweep(self, Direction.values()[(timer >> 4) & 3]);
        }
    }

    @Override
    public boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        if (self.isMoving() || self.classTimer() != 0) {
            return false;
        }
        ctx.setClassTimer(self, TIMER);
        return true;
    }
}
