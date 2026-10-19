package de.latzig.game.tob.core.sim.element;

import de.latzig.game.tob.core.sim.SimContext;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Class handler of one element type; runs once per frame in list order for every alive object (pipeline step 4).
 * {@code act} also runs while the world is frozen (READY, or later under the freezer): the original's class
 * handlers have no frozen check of their own ({@code L00B016}), so each behaviour that must stand still while frozen
 * has to check {@link WorldState#isFrozen()} itself, as the original class handler does.
 * <p>
 * The ray hooks run inside a ray trace ({@code core.sim.Ray}); the ray logic itself (reflect, prism, converter,
 * destroy, push) stays in the core, as in the original fire routine {@code L00A796}.
 */
public interface ElementBehaviour {

    void act(SimContext ctx, SimObject self);

    /**
     * A zap ray reached an object with the {@code $0800} bit and none of the bits before it ({@code S:8782},
     * {@code S:8840-8853}). Returns true if the zap affected the object (a hit for the fire throttle). Default: the
     * ray is absorbed.
     */
    default boolean onZap(SimContext ctx, SimObject self, SimObject shooter) {
        return false;
    }

    /** A push ray has just started pushing this object ({@code S:8890-8911}). Default: nothing more happens. */
    default void onPush(SimContext ctx, SimObject self, SimObject shooter) {
    }

    /**
     * A grab ray reached an object with the klondike, forcer or tool bit ({@code S:8937-8942}). Returns true if the
     * grab took effect (a hit for the fire throttle). Default: nothing happens.
     */
    default boolean onGrab(SimContext ctx, SimObject self, SimObject grabber) {
        return false;
    }
}
