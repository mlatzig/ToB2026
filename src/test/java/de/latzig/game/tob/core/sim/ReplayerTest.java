package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.TowerDefinition;

class ReplayerTest {

    @Test
    void stepByStepReproducesTheEventsAndTheFinalStateOfTheRecordedRun() {
        TowerDefinition tower = RecordingTest.liftTower();
        Recorder recorder = new Recorder(Simulation.start(tower, new SimOptions(true)));
        RandomCommands random = new RandomCommands(11);
        List<SimEvent> events = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            events.addAll(recorder.step(random.next()));
        }
        Replayer replayer = new Replayer(recorder.recording(), tower);

        List<SimEvent> replayed = new ArrayList<>();
        while (replayer.hasNext()) {
            replayed.addAll(replayer.step());
        }

        assertEquals(300, replayer.position());
        assertEquals(events, replayed);
        assertEquals(Snapshots.of(recorder.simulation()), Snapshots.of(replayer.simulation()));
    }

    @Test
    void nextCommandsAreTheCommandsOfTheNextStep() {
        Command turn = new Command.TurnLeft(SpiderId.ZAPPER);
        Command back = new Command.Back(SpiderId.PUSHER);
        Command fire = new Command.Fire(SpiderId.GRABBER);
        Recording recording = new Recording("test-000", SimOptions.DEFAULT, 4, List.of(new Recording.Entry(0, turn),
                new Recording.Entry(2, back), new Recording.Entry(2, fire)));
        Replayer replayer = new Replayer(recording, RecordingTest.liftTower());

        assertEquals(List.of(turn), replayer.nextCommands());
        replayer.step();
        assertEquals(List.of(), replayer.nextCommands());
        replayer.step();
        assertEquals(List.of(back, fire), replayer.nextCommands());
        replayer.step();
        replayer.step();
        assertFalse(replayer.hasNext());
        assertEquals(List.of(), replayer.nextCommands());
        assertThrows(IllegalStateException.class, replayer::step);
    }

    @Test
    void aSimulationThatFinishesEarlyStopsTheReplayWithoutAnException() {
        Recording recording = new Recording("test-000", SimOptions.DEFAULT, 10,
                List.of(new Recording.Entry(3, new Command.Abandon())));
        Replayer replayer = new Replayer(recording, RecordingTest.liftTower());

        Simulation simulation = replayer.run(new ArrayList<>());

        assertTrue(simulation.isFinished());
        assertEquals(Rules.EXIT_ABANDONED, simulation.exitCode());
        assertEquals(4, replayer.position(), "stopped after the step that ended the attempt");
        assertFalse(replayer.hasNext());
    }

    @Test
    void aReplayerNeedsTheRecordedTower() {
        Recording recording = new Recording("other-tower", SimOptions.DEFAULT, 0, List.of());

        assertThrows(IllegalArgumentException.class, () -> new Replayer(recording, RecordingTest.liftTower()));
    }
}
