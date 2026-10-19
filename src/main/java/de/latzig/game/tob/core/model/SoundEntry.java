package de.latzig.game.tob.core.model;

import java.util.Objects;

/**
 * One sound: file relative to the assets root, loop flag, positional (3D) flag with min/max distance in fields
 * (both 0 for non-positional sounds), ToB 2005 detail level and a trigger description with its source reference.
 */
public record SoundEntry(String file, boolean loop, boolean positional, double minDistance, double maxDistance,
        SoundDetail detail, String trigger) {

    public SoundEntry {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(trigger, "trigger");
        if (positional && !(minDistance > 0 && maxDistance >= minDistance)) {
            throw new IllegalArgumentException("positional sound needs 0 < minDistance <= maxDistance: "
                    + minDistance + ".." + maxDistance);
        }
        if (!positional && (minDistance != 0 || maxDistance != 0)) {
            throw new IllegalArgumentException("non-positional sound must have distances 0: " + minDistance + ".."
                    + maxDistance);
        }
    }
}
