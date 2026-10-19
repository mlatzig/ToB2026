package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Runs a {@link Recording} against a fresh {@link Simulation} step by step (Phase 3c; the headless helper of the
 * solution replay test and the importer). Unlike {@link Replay#run} it stops without an exception when the simulation
 * finishes before the recorded length, so a caller can tell "completed at the last step" from "ended early".
 */
public final class Replayer {

    private final Recording recording;
    private final Simulation simulation;
    private int position;
    private int nextEntry;

    /** @throws IllegalArgumentException if {@code tower} is not the recorded tower or cannot be loaded */
    public Replayer(Recording recording, TowerDefinition tower) {
        if (!tower.id().equals(recording.towerId())) {
            throw new IllegalArgumentException(
                    "recording of tower " + recording.towerId() + " cannot replay on " + tower.id());
        }
        this.recording = recording;
        this.simulation = Simulation.start(tower, recording.options());
    }

    public Recording recording() {
        return recording;
    }

    public Simulation simulation() {
        return simulation;
    }

    /** Steps run so far. */
    public int position() {
        return position;
    }

    /** Whether a recorded step is left and the simulation has not finished. */
    public boolean hasNext() {
        return position < recording.length() && !simulation.isFinished();
    }

    /** The commands recorded for the next step (empty for a step without input or after the last step). */
    public List<Command> nextCommands() {
        List<Command> commands = new ArrayList<>();
        List<Recording.Entry> entries = recording.entries();
        for (int i = nextEntry; i < entries.size() && entries.get(i).step() == position; i++) {
            commands.add(entries.get(i).command());
        }
        return commands;
    }

    /**
     * Runs the next recorded step.
     *
     * @throws IllegalStateException if there is no next step ({@link #hasNext()})
     */
    public List<SimEvent> step() {
        if (!hasNext()) {
            throw new IllegalStateException("no step left at " + position + " of " + recording.length());
        }
        List<Command> commands = nextCommands();
        List<SimEvent> events = simulation.step(commands);
        nextEntry += commands.size();
        position++;
        return events;
    }

    /** Runs every remaining step, appending the events to {@code events}; returns the simulation. */
    public Simulation run(List<SimEvent> events) {
        while (hasNext()) {
            events.addAll(step());
        }
        return simulation;
    }
}
