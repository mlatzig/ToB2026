package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;

class RecordingTest {

    /** Three spiders, the pusher on an up-lift, a block; no task and no time limit, so it never ends. */
    static TowerDefinition liftTower() {
        return TestTowers.tower()
                .level(0, "FFFF", "FUFF", "FFFF")
                .level(1, ".u..", "....")
                .object(0, 0, 0, ObjectType.ZAP)
                .object(1, 1, 0, ObjectType.PSH)
                .object(3, 2, 0, ObjectType.GRB)
                .object(3, 0, 0, ObjectType.BLK)
                .build();
    }

    @Test
    void theRecorderKeepsTheStepIndexOfEveryCommand() {
        Recorder recorder = new Recorder(Simulation.start(liftTower(), new SimOptions(true)));
        recorder.step(List.of(new Command.TurnLeft(SpiderId.ZAPPER)));
        recorder.step(List.of());
        recorder.step(List.of(new Command.Back(SpiderId.PUSHER), new Command.Fire(SpiderId.GRABBER)));

        Recording recording = recorder.recording();

        assertEquals("test-000", recording.towerId());
        assertEquals(new SimOptions(true), recording.options());
        assertEquals(3, recording.length());
        assertEquals(List.of(new Recording.Entry(0, new Command.TurnLeft(SpiderId.ZAPPER)),
                new Recording.Entry(2, new Command.Back(SpiderId.PUSHER)),
                new Recording.Entry(2, new Command.Fire(SpiderId.GRABBER))), recording.entries());
    }

    @Test
    void aReplayReproducesTheEventStreamAndTheFinalState() {
        TowerDefinition tower = liftTower();
        Recorder recorder = new Recorder(Simulation.start(tower, new SimOptions(true)));
        RandomCommands random = new RandomCommands(42);
        List<SimEvent> events = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            events.addAll(recorder.step(random.next()));
        }

        List<SimEvent> replayed = new ArrayList<>();
        Simulation replay = Replay.run(recorder.recording(), tower, replayed);

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(SimEvent.MoveStarted.class::isInstance));
        assertEquals(events, replayed);
        assertEquals(Snapshots.of(recorder.simulation()), Snapshots.of(replay));
    }

    @Test
    void entriesMustBeOrderedAndInsideTheLength() {
        Command fire = new Command.Fire(SpiderId.ZAPPER);

        assertThrows(IllegalArgumentException.class, () -> new Recording("t", SimOptions.DEFAULT, 5,
                List.of(new Recording.Entry(3, fire), new Recording.Entry(1, fire))));
        assertThrows(IllegalArgumentException.class,
                () -> new Recording("t", SimOptions.DEFAULT, 2, List.of(new Recording.Entry(2, fire))));
        assertThrows(IllegalArgumentException.class, () -> new Recording.Entry(-1, fire));
    }

    @Test
    void aReplayNeedsTheRecordedTower() {
        Recording recording = new Recording("other-tower", SimOptions.DEFAULT, 0, List.of());

        assertThrows(IllegalArgumentException.class, () -> Replay.run(recording, liftTower(), new ArrayList<>()));
    }
}
