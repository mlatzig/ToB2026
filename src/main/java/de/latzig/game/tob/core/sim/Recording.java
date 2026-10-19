package de.latzig.game.tob.core.sim;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A replayable input recording: tower id, options, the number of recorded steps and every command with the index of
 * the {@link Simulation#step} call that received it. Step indexes are used instead of frame numbers because all
 * READY frames share frame number 0. Since Phase 3c a recording may carry {@link RecordingMeta} (outcome, time,
 * frames, tower checksum, game); the metadata never changes a replay.
 */
public record Recording(String towerId, SimOptions options, int length, List<Entry> entries,
        Optional<RecordingMeta> meta) {

    public record Entry(int step, Command command) {
        public Entry {
            if (step < 0) {
                throw new IllegalArgumentException("negative step " + step);
            }
            Objects.requireNonNull(command, "command");
        }
    }

    public Recording {
        Objects.requireNonNull(towerId, "towerId");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(meta, "meta");
        if (length < 0) {
            throw new IllegalArgumentException("negative length " + length);
        }
        entries = List.copyOf(entries);
        int previous = 0;
        for (Entry entry : entries) {
            if (entry.step() < previous) {
                throw new IllegalArgumentException("entries must be ordered by step");
            }
            if (entry.step() >= length) {
                throw new IllegalArgumentException("entry step " + entry.step() + " outside length " + length);
            }
            previous = entry.step();
        }
    }

    /** A recording without metadata (format version 1, {@link Recorder}). */
    public Recording(String towerId, SimOptions options, int length, List<Entry> entries) {
        this(towerId, options, length, entries, Optional.empty());
    }

    /** This recording with {@code newMeta}. */
    public Recording withMeta(RecordingMeta newMeta) {
        return new Recording(towerId, options, length, entries, Optional.of(newMeta));
    }
}
