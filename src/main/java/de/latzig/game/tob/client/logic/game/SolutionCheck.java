package de.latzig.game.tob.client.logic.game;

import java.util.ArrayList;
import java.util.Optional;

import de.latzig.game.tob.core.io.TowerChecksum;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.Replayer;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.Simulation;

/**
 * Whether a recording is a solution of a tower (Phase 3c: the solution replay test and the importer): it carries
 * metadata whose tower checksum matches the tower, and a headless replay ({@link Replayer}) ends with COMPLETED
 * exactly at its last recorded step.
 */
public final class SolutionCheck {

    private SolutionCheck() {
    }

    /** Why {@code recording} is not a solution of {@code tower}, or empty if it is one. */
    public static Optional<String> problem(Recording recording, TowerDefinition tower) {
        if (!tower.id().equals(recording.towerId())) {
            return Optional.of("recorded on " + recording.towerId() + ", not on " + tower.id());
        }
        if (recording.meta().isEmpty()) {
            return Optional.of("no metadata, so no tower checksum to compare");
        }
        RecordingMeta meta = recording.meta().get();
        String checksum = TowerChecksum.of(tower);
        if (!checksum.equals(meta.towerChecksum())) {
            return Optional.of("tower changed: recorded on checksum " + meta.towerChecksum() + ", the tower now has "
                    + checksum);
        }
        Replayer replayer = new Replayer(recording, tower);
        try {
            replayer.run(new ArrayList<>());
        } catch (RuntimeException e) {
            return Optional.of("simulation error at step " + replayer.position() + ": " + e);
        }
        Simulation simulation = replayer.simulation();
        if (!simulation.isFinished()) {
            return Optional.of("not finished after all " + recording.length() + " steps");
        }
        if (simulation.exitCode() != Rules.EXIT_COMPLETED) {
            return Optional.of("ends with exit " + simulation.exitCode() + " ("
                    + RecordingMeta.Outcome.ofExit(simulation.exitCode()).fileName() + ") at step "
                    + replayer.position());
        }
        if (replayer.position() != recording.length()) {
            return Optional.of("completed at step " + replayer.position() + " of " + recording.length());
        }
        return Optional.empty();
    }
}
