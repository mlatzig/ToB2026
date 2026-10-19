package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.ObjectFlags;
import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class $E, the klondike: grab ({@code S:8945-8954}) and handler ({@code L00B82E}, {@code S:9814-9835}). A grab sets
 * the "grabbed" bit and counts the klondike; the next handler (in the same frame) starts the 7-frame vanish count in
 * +1A; after 7 more handlers it is removed and its cell is freed. The handler does not check the freeze. Deliberate
 * deviation 1 of the Phase 2b spec: the original clears the bit in the handler and keeps the other flags, so a
 * second grab or a zap would still count it; here the bit stays set until removal and makes the klondike
 * transparent to every ray ({@code SimObject.isVanishing()}).
 */
public enum KlondikeBehaviour implements ElementBehaviour {
    INSTANCE;

    /** Handler calls from the start of the count to the removal ({@code move.w #$7,$1a(a0)}, {@code S:9834}). */
    public static final int VANISH_FRAMES = 7;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (self.destroyCounter() != 0) {
            ctx.setDestroyCounter(self, self.destroyCounter() - 1);
            if (self.destroyCounter() == 0) {
                ctx.remove(self);
            }
        } else if ((self.objectFlags() & ObjectFlags.KLONDIKE_GRABBED) != 0) {
            ctx.setDestroyCounter(self, VANISH_FRAMES);
        }
    }

    /** Collected: the grabbed bit, the event, then klondikes +1 (saturating) with the victory check. */
    @Override
    public boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        ctx.setObjectFlags(self, self.objectFlags() | ObjectFlags.KLONDIKE_GRABBED);
        ctx.emit(new SimEvent.Collected(ctx.frame(), self.slot(), grabber.slot()));
        ctx.addKlondike();
        return true;
    }
}
