package de.latzig.game.tob.client.logic.game;

import de.latzig.game.tob.client.logic.Pose;

/**
 * The external camera's glide (spec §2 "visual smoothing"): the simulation steps the camera at once, the eye follows in
 * a straight line within {@value #SECONDS} s. A new target during a glide starts a new glide from where the eye is.
 * Switching the viewpoint snaps.
 */
public final class PoseGlide {

    public static final float SECONDS = 0.2f;

    private Pose from;
    private Pose to;
    private Pose current;
    private float elapsed;

    /** Jumps to {@code pose}. */
    public void snap(Pose pose) {
        from = pose;
        to = pose;
        current = pose;
        elapsed = SECONDS;
    }

    /** Moves towards {@code target} for {@code seconds} and returns the eye position; the first call snaps. */
    public Pose update(Pose target, float seconds) {
        if (current == null) {
            snap(target);
            return current;
        }
        if (!target.equals(to)) {
            from = current;
            to = target;
            elapsed = 0f;
        }
        elapsed = Math.min(SECONDS, elapsed + Math.max(0f, seconds));
        float t = elapsed / SECONDS;
        current = new Pose(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t,
                from.z() + (to.z() - from.z()) * t);
        return current;
    }

    public Pose current() {
        return current;
    }
}
