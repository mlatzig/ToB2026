package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.TowerDefinition;

/** Replays a {@link Recording}: same tower + same options + same commands = same events and state. */
public final class Replay {

    private Replay() {
    }

    /**
     * Runs all recorded steps on a fresh simulation, appends every event to {@code events} and returns the simulation.
     *
     * @throws IllegalArgumentException if the tower is not the recorded one
     */
    public static Simulation run(Recording recording, TowerDefinition tower, List<SimEvent> events) {
        if (!tower.id().equals(recording.towerId())) {
            throw new IllegalArgumentException(
                    "recording of tower " + recording.towerId() + " cannot replay on " + tower.id());
        }
        Simulation simulation = Simulation.start(tower, recording.options());
        List<Recording.Entry> entries = recording.entries();
        int next = 0;
        for (int step = 0; step < recording.length(); step++) {
            List<Command> commands = new ArrayList<>();
            while (next < entries.size() && entries.get(next).step() == step) {
                commands.add(entries.get(next).command());
                next++;
            }
            events.addAll(simulation.step(commands));
        }
        return simulation;
    }
}
