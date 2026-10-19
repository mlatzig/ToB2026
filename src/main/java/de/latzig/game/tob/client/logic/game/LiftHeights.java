package de.latzig.game.tob.client.logic.game;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.core.sim.ForcerLifts;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.LiftRide;
import de.latzig.game.tob.core.sim.LiftState;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Platform height of a lift shaft as the fraction 0 (bottom) to 1 (top) of one level above the lower cell. A spider
 * ride follows the rider's interpolated height; a forcer-driven motion follows {@link ForcerLifts#timer()} with the
 * lift ease table (Phase 2 carry-over 1), not a rider.
 */
public final class LiftHeights {

    private LiftHeights() {
    }

    public static float ratio(Lift lift, WorldState world, float alpha) {
        return ratio(lift, world, alpha, MotionCarry.NONE);
    }

    /** The ratio with the driver's {@link MotionCarry}: the platform follows the drawn rider. */
    public static float ratio(Lift lift, WorldState world, float alpha, MotionCarry carry) {
        return switch (lift.state()) {
            case BOTTOM -> 0f;
            case TOP -> 1f;
            case MOVING_UP, MOVING_DOWN -> moving(lift, world, alpha, carry);
        };
    }

    private static float moving(Lift lift, WorldState world, float alpha, MotionCarry carry) {
        boolean frozen = world.isFrozen();
        if (lift.rider() >= 0) {
            SimObject rider = world.object(lift.rider());
            if (rider.isRiding()) {
                float height = Motion.position(rider, frozen, alpha, carry).y() - lift.lower().level();
                return Math.clamp(height, 0f, 1f);
            }
        }
        ForcerLifts forcer = world.forcerLifts();
        if (forcer.isActive() && forcer.platforms().contains(forcer.isUp() ? lift.lower() : lift.upper())) {
            return forcerRatio(forcer.isUp(), forcer.timer(), frozen ? 0f : alpha);
        }
        // a ride stopped mid-shaft by an exchange (deviation 2) keeps the platform where the ride started
        return lift.state() == LiftState.MOVING_UP ? 0f : 1f;
    }

    /**
     * Height of a forcer-driven platform at {@code timer} (32 at the grab, counting down) plus {@code advance} of the
     * next frame: the ease units of the frames done, over 512.
     */
    public static float forcerRatio(boolean up, int timer, float advance) {
        int units = 0;
        for (int counter = ForcerLifts.FRAMES; counter > timer; counter--) {
            units += LiftRide.ease(counter);
        }
        float done = (units + (timer > 0 ? LiftRide.ease(timer) * advance : 0f)) / Motion.UNITS_PER_FIELD;
        return up ? done : 1f - done;
    }
}
