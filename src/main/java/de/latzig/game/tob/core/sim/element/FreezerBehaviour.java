package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class $13, the freezer: grab ({@code S:8961-8971}) and handler ({@code L00B93E}, {@code S:9882-9906}). A grab works
 * only while the timer +1C is 0: it sets 128 and lowers the freeze counter {@code W001898} by one, which freezes the
 * world globally. The handler also runs while the world is frozen: while the timer is not 0 it counts down, and at 64
 * it raises the counter again ({@code S:9892-9895}). A grab in frame n therefore thaws in the freezer's handler of
 * frame n + 63 (64 frames), and the freezer can be grabbed again from frame n + 128. Two active freezers stack. A zap in
 * the freeze phase thaws; a zap in the recharge phase only destroys it (deviation 4, {@link Rules}). Zappable (a
 * robot); not pushable.
 */
public enum FreezerBehaviour implements ElementBehaviour {
    INSTANCE;

    @Override
    public void act(SimContext ctx, SimObject self) {
        int timer = self.classTimer();
        if (timer == 0) {
            return;
        }
        ctx.setClassTimer(self, timer - 1);
        if (timer - 1 == Rules.FREEZER_THAW) {
            ctx.thaw();
            ctx.emit(new SimEvent.Thawed(ctx.frame(), self.slot()));
        }
    }

    @Override
    public boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        if (self.classTimer() != 0) {
            return false;
        }
        ctx.setClassTimer(self, Rules.FREEZER_TIMER);
        ctx.freeze();
        ctx.emit(new SimEvent.Frozen(ctx.frame(), self.slot()));
        return true;
    }
}
