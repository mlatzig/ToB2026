package de.latzig.game.tob.core.progress;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One tower of a profile: the attempts started (ToB 2009's "[n]"), the original's retry counter ({@link TowerScore}),
 * whether it is completed, the score it gave, when it was first completed and the frames of that completion.
 */
public record TowerProgress(int attempts, int counter, boolean completed, int score, Optional<Instant> firstCompleted,
        OptionalInt bestFrames) {

    public static final TowerProgress NONE = new TowerProgress(0, 0, false, 0, Optional.empty(), OptionalInt.empty());

    public TowerProgress {
        if (attempts < 0 || counter < 0 || score < 0) {
            throw new IllegalArgumentException("negative tower progress");
        }
        Objects.requireNonNull(firstCompleted, "firstCompleted");
        Objects.requireNonNull(bestFrames, "bestFrames");
    }
}
