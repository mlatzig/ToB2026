package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Every element whose class handler has no game logic (animation only, or not modelled before its phase): it holds
 * its cell and does nothing. Its ray reactions come from its flag word.
 */
public enum StaticObstacleBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        // no game logic in the class handler
    }
}
