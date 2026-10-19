package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class 0 handler. The original one ({@code L00B0A0}, {@code S:9309-9375}) only picks animation frames and walking
 * sounds; spider input, programs and movement run in pipeline steps 2 and 3a.
 */
public enum SpiderBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        // no game logic in the class handler
    }
}
