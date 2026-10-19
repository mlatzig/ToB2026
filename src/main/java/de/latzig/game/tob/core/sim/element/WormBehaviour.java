package de.latzig.game.tob.core.sim.element;

import java.util.Set;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.MoveKind;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class 5, the worm ({@code L00B2CC}, {@code S:9463-9486}). It steers by +0E and walks continuously: whenever it
 * stands it tries a step in its direction; if that is blocked it reverses its direction and stands for this frame, so
 * a worm blocked on both sides flips every frame. It does nothing while the world is frozen or after it was pushed:
 * a push makes it permanently inert ({@code +0E >= 4}). A zap reverses it while it walks ({@code \00A930},
 * {@code S:8840-8853}), except at its claim (30), half-way (16) and release (2) counters; a standing worm absorbs the
 * zap, and so does a worm being pushed (deliberate deviation 3 of the Phase 2c spec: the original reverses the push
 * and reads past the push ease table).
 */
public enum WormBehaviour implements ElementBehaviour {
    INSTANCE;

    /** Walk counters at which a zap is absorbed ({@code S:8842-8847}). */
    public static final Set<Integer> ZAP_ABSORBED = Set.of(2, 16, 30);

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen() || self.isDeactivated() || self.isMoving()) {
            return;
        }
        Direction direction = self.moveDirection();
        if (!Steps.walk(ctx, self, direction)) {
            ctx.setHeading(self, direction.opposite());
        }
    }

    @Override
    public boolean onZap(SimContext ctx, SimObject self, SimObject shooter) {
        if (self.moveKind() != MoveKind.WALK || !self.isMoving() || ZAP_ABSORBED.contains(self.moveCounter())) {
            return false;
        }
        ctx.reverseWalk(self);
        ctx.setFacing(self, self.moveDirection());
        return true;
    }

    @Override
    public void onPush(SimContext ctx, SimObject self, SimObject shooter) {
        ctx.deactivate(self);
    }
}
