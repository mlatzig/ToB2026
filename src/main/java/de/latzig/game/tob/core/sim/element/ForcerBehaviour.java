package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Classes $11/$12, forcer up and forcer down (grab {@code S:8955-8960}, {@code S:9023-9101}). Their class handlers only
 * animate ({@code L00B8DE}, {@code L00B90E}, {@code S:9858-9881}). A grab starts the forcer-lift motion in the forcer's
 * direction unless a motion runs ({@link SimContext#startForcerLifts}). Zappable (a robot); not pushable.
 */
public enum ForcerBehaviour implements ElementBehaviour {
    UP(true), DOWN(false);

    private final boolean up;

    ForcerBehaviour(boolean up) {
        this.up = up;
    }

    @Override
    public void act(SimContext ctx, SimObject self) {
        // animation only
    }

    @Override
    public boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        return ctx.startForcerLifts(up);
    }
}
