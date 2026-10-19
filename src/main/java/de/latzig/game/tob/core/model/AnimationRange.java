package de.latzig.game.tob.core.model;

import java.util.Objects;

/** Named animation range in frames (16 frames = 1 simulation unit). */
public record AnimationRange(String name, int fromFrame, int toFrame) {

    public AnimationRange {
        Objects.requireNonNull(name, "name");
        Checks.range("fromFrame", fromFrame, 0, Integer.MAX_VALUE);
        if (toFrame < fromFrame) {
            throw new IllegalArgumentException(name + ": toFrame " + toFrame + " < fromFrame " + fromFrame);
        }
    }
}
