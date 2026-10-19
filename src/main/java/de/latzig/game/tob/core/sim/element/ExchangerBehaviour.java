package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class $15, the exchanger ({@code S:8981-9022}). Its class handler has no logic ({@code L00B07E}); it reflects zap
 * rays through its flag word and is not destroyed by them. A grab exchanges the grabber with it: both swap cells,
 * occupancy and positions, the grabber turns round and stops (even mid-walk), and the exchanger is destroyed for 32
 * frames and counts as a robot (saturating, with the victory check).
 */
public enum ExchangerBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        // no game logic in the class handler
    }

    @Override
    public boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        ctx.swap(grabber, self);
        ctx.emit(new SimEvent.Exchanged(ctx.frame(), grabber.slot(), self.slot()));
        ctx.setFacing(grabber, grabber.facing().opposite());
        ctx.stop(grabber);
        ctx.destroy(self);
        ctx.addRobot();
        return true;
    }
}
