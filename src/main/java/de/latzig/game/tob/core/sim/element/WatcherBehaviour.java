package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class 6, the watcher ({@code L00B32E}, {@code S:9487-9576}). It steers by +0E and does nothing while the world is
 * frozen, while it walks, or after it was pushed (permanently inert). Standing and not turning, it prefers left (a
 * 4-frame turn), then straight (a step), then right (a 4-frame turn), then back (an 8-frame half turn,
 * counter-clockwise), else it idles. The turn counter is the class timer +1C; in the frame it reaches 0 the watcher
 * re-tests straight, right and back, but not left ({@code S:9530-9531} jumps to {@code \00B3F6}). Not zappable;
 * pushable.
 */
public enum WatcherBehaviour implements ElementBehaviour {
    INSTANCE;

    /** Frames of a quarter turn left or right ({@code move.w #$4,$1c(a0)}, {@code S:9538}, {@code S:9549}). */
    public static final int QUARTER_TURN = 4;
    /** Frames of the half turn back ({@code move.w #$8,$1c(a0)}, {@code S:9556}). */
    public static final int HALF_TURN = 8;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen() || self.isDeactivated() || self.isMoving()) {
            return;
        }
        if (self.classTimer() != 0) {
            ctx.setClassTimer(self, self.classTimer() - 1);
            if (self.classTimer() != 0) {
                return;
            }
            ctx.emit(new SimEvent.TurnFinished(ctx.frame(), self.slot()));
        } else {
            Direction left = self.moveDirection().left();
            if (ctx.isFree(self.cell().step(left))) {
                ctx.startTurn(self, left, QUARTER_TURN);
                return;
            }
        }
        Direction ahead = self.moveDirection();
        if (Steps.walk(ctx, self, ahead)) {
            return;
        }
        Direction right = ahead.right();
        if (ctx.isFree(self.cell().step(right))) {
            ctx.startTurn(self, right, QUARTER_TURN);
            return;
        }
        Direction back = ahead.opposite();
        if (ctx.isFree(self.cell().step(back))) {
            ctx.startTurn(self, back, HALF_TURN);
            return;
        }
        ctx.emit(new SimEvent.Idle(ctx.frame(), self.slot()));
    }

    @Override
    public void onPush(SimContext ctx, SimObject self, SimObject shooter) {
        ctx.deactivate(self);
    }
}
