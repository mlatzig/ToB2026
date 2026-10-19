package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimObject;

/**
 * Class 9, the time bomb ({@code L00B6BC}, {@code S:9724-9781}). In every frame the world is not frozen, its fuse in
 * the class timer +1C grows by the tower's time bomb speed + 1 ({@code S:9726-9728}), also while it is being pushed.
 * At 800 ({@code cmp.w #$320,d1}, {@code S:9729-9731}) it explodes: in frame 800, 400, 267 or 200 for speed 0..3. The
 * explosion destroys the holders of its four neighbour cells, sparing lift riders, and then the bomb itself; it never
 * destroys the floor and never counts ({@code S:9742-9778}). Each tenth of the fuse emits {@link SimEvent.FuseTick}.
 * Pushable; not zappable.
 */
public enum TimeBombBehaviour implements ElementBehaviour {
    INSTANCE;

    /** The fuse length ({@code #$320}). */
    public static final int FUSE = 800;

    @Override
    public void act(SimContext ctx, SimObject self) {
        if (ctx.world().isFrozen()) {
            return;
        }
        int before = self.classTimer();
        int fuse = before + ctx.world().tower().behaviour().timeBombSpeed() + 1;
        ctx.setClassTimer(self, fuse);
        if (fuse >= FUSE) {
            ctx.explode(self, false);
            return;
        }
        int tenths = fuse * 10 / FUSE;
        if (tenths != before * 10 / FUSE) {
            ctx.emit(new SimEvent.FuseTick(ctx.frame(), self.slot(), tenths * 10));
        }
    }
}
