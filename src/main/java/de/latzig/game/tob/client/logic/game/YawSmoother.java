package de.latzig.game.tob.client.logic.game;

import de.latzig.game.tob.client.logic.Motion;

/**
 * The spider camera's smooth turn (spec §2): the simulation turns at once, the camera follows along the shorter arc
 * at a quarter turn per {@value #QUARTER_TURN_SECONDS} s. Switching to another spider snaps. An option to switch the
 * smoothing off comes in Phase 5.
 */
public final class YawSmoother {

    public static final float QUARTER_TURN_SECONDS = 0.2f;
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float SPEED = Motion.HALF_PI / QUARTER_TURN_SECONDS;

    private float current;

    /** Jumps to {@code heading} (radians, {@link Motion#heading}). */
    public void snap(float heading) {
        current = normalize(heading);
    }

    /** Turns towards {@code target} for {@code seconds} and returns the new heading in [0, 2π). */
    public float update(float target, float seconds) {
        float delta = normalize(target - current);
        if (delta > Math.PI) {
            delta -= TWO_PI;
        }
        float step = SPEED * Math.max(0f, seconds);
        boolean arrives = Math.abs(delta) <= step + 1e-5f;
        current = arrives ? normalize(target) : normalize(current + Math.signum(delta) * step);
        return current;
    }

    public float current() {
        return current;
    }

    private static float normalize(float angle) {
        float value = angle % TWO_PI;
        return value < 0 ? value + TWO_PI : value;
    }
}
