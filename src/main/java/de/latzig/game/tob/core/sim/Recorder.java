package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

/** Steps a simulation and records every command for a later {@link Replay}. */
public final class Recorder {

    private final Simulation simulation;
    private final List<Recording.Entry> entries = new ArrayList<>();
    private int steps;

    public Recorder(Simulation simulation) {
        this.simulation = simulation;
    }

    public Simulation simulation() {
        return simulation;
    }

    public List<SimEvent> step(List<Command> commands) {
        List<SimEvent> events = simulation.step(commands);
        for (Command command : commands) {
            entries.add(new Recording.Entry(steps, command));
        }
        steps++;
        return events;
    }

    public Recording recording() {
        WorldState world = simulation.world();
        return new Recording(world.tower().id(), world.options(), steps, entries);
    }
}
