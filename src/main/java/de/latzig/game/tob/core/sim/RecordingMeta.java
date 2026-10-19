package de.latzig.game.tob.core.sim;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * The optional metadata of a {@link Recording} (format version 2, Phase 3c): how the attempt ended, when it was
 * recorded (UTC), the simulation frame at its end, the SHA-256 of the tower's canonical JSON (hex, lower case) and
 * the game that recorded it ({@code "ToB 2026 <version>"}). Metadata never changes a replay.
 */
public record RecordingMeta(Outcome outcome, Instant recordedAt, int frames, String towerChecksum, String game) {

    /** How a recorded attempt ended; {@link #fileName()} is the lower-case name used in files and file names. */
    public enum Outcome {
        COMPLETED,
        FAILED,
        TIMEUP,
        ABANDONED,
        RESTART,
        CRASH;

        public String fileName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** @throws IllegalArgumentException for an unknown name */
        public static Outcome fromFileName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }

        /**
         * The outcome of an attempt that ended with {@code exitCode} ({@link Rules}): completed, time up, failed
         * (spiders or grabber destroyed), restart; abandoned for an abandon and for 0 (left before the end).
         */
        public static Outcome ofExit(int exitCode) {
            return switch (exitCode) {
                case Rules.EXIT_COMPLETED -> COMPLETED;
                case Rules.EXIT_TIMEOUT -> TIMEUP;
                case Rules.EXIT_SPIDERS_DESTROYED, Rules.EXIT_GRABBER_DESTROYED -> FAILED;
                case Rules.EXIT_RESTART -> RESTART;
                default -> ABANDONED;
            };
        }
    }

    public RecordingMeta {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(towerChecksum, "towerChecksum");
        Objects.requireNonNull(game, "game");
        if (frames < 0) {
            throw new IllegalArgumentException("negative frames " + frames);
        }
    }
}
