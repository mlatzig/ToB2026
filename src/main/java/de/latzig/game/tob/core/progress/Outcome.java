package de.latzig.game.tob.core.progress;

/**
 * How an attempt ended, for the profile: completed (exit 1), restarted (exit 6) or failed (exits 2–5: out of time,
 * all spiders destroyed, grabber destroyed, abandoned), the three cases of {@code S:6439-6441} and {@code S:6741}.
 */
public enum Outcome {
    COMPLETED, RESTARTED, FAILED
}
