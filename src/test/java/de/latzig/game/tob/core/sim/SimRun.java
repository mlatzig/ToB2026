package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

/** Test driver: steps a simulation and keeps every event. */
public final class SimRun {

    public final Simulation sim;
    public final List<SimEvent> events = new ArrayList<>();

    public SimRun(TowerDefinition tower) {
        this(tower, SimOptions.DEFAULT);
    }

    public SimRun(TowerDefinition tower, SimOptions options) {
        this.sim = Simulation.start(tower, options);
    }

    /**
     * Oracle §10 "start time": adds a zapper at (7,7) facing N (slot 0), which fires into the void in the READY-ending
     * frame 0, so that every other object first acts in frame 1.
     */
    public static SimRun startTime(TestTowers.Builder tower) {
        SimRun run = new SimRun(tower.object(7, 7, 0, ObjectType.ZAP).build());
        run.step(new Command.Fire(SpiderId.ZAPPER));
        return run;
    }

    public List<SimEvent> step(Command... commands) {
        List<SimEvent> stepEvents = sim.step(List.of(commands));
        events.addAll(stepEvents);
        return stepEvents;
    }

    /** Steps without input until the given normal frame has run; READY must have ended. */
    public void runUntilFrame(int frame) {
        if (sim.isReady()) {
            throw new IllegalStateException("still READY: end it with an action first");
        }
        while (sim.frame() < frame) {
            step();
        }
    }

    public void runUntilFinished() {
        while (!sim.isFinished()) {
            step();
        }
    }

    public <T extends SimEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The events of one slot in order: walk, push, turn, idle, deactivation and destruction events. */
    public List<SimEvent> eventsOf(int slot) {
        return events.stream().filter(event -> slotOf(event) == slot).toList();
    }

    /** Turns a spider in READY and waits out the shared 4-frame turn/fire throttle. */
    public void turnInReady(Command turn) {
        step(turn);
        step();
        step();
        step();
    }

    /** Writes a program in READY frames; three idle frames after each write satisfy the 4-frame write throttle. */
    public void writeProgram(SpiderId spider, ProgramStep... steps) {
        step(new Command.EnterWriteMode(spider));
        for (ProgramStep programStep : steps) {
            step(new Command.Write(spider, programStep));
            step();
            step();
            step();
        }
    }

    private static int slotOf(SimEvent event) {
        return switch (event) {
            case SimEvent.MoveStarted e -> e.slot();
            case SimEvent.MoveClaimed e -> e.slot();
            case SimEvent.MoveReversed e -> e.slot();
            case SimEvent.CellChanged e -> e.slot();
            case SimEvent.MoveFinished e -> e.slot();
            case SimEvent.Turned e -> e.slot();
            case SimEvent.PushStarted e -> e.slot();
            case SimEvent.PushFinished e -> e.slot();
            case SimEvent.TurnStarted e -> e.slot();
            case SimEvent.TurnFinished e -> e.slot();
            case SimEvent.Idle e -> e.slot();
            case SimEvent.Deactivated e -> e.slot();
            case SimEvent.Destroyed e -> e.slot();
            case SimEvent.Removed e -> e.slot();
            default -> -1;
        };
    }
}
