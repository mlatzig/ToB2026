package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class $D, the lizard ({@code L00B7A6}, {@code S:9782-9813}). It steers by its facing (+0C) and does nothing while
 * the world is frozen or while it walks. Standing, it first eats its marked cell if nobody holds or claims it (the mark
 * is cleared, then the shared floor rule decides), then tries a step in its facing; if blocked, it reverses its facing
 * and marks its current cell, overwriting any older mark, whose floor then survives. A lizard blocked on both sides
 * oscillates and never eats its own cell. Not pushable, not zappable, never deactivated.
 */
public enum LizardBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen() || self.isMoving()) {
            return;
        }
        CellPos mark = self.markedCell();
        if (mark != null && ctx.world().occupancy().isFree(mark)) {
            ctx.setMark(self, null);
            ctx.removeFloor(mark);
        }
        Direction facing = self.facing();
        if (ctx.tryStep(self, facing)) {
            return;
        }
        ctx.setFacing(self, facing.opposite());
        ctx.setMark(self, self.cell());
    }
}
