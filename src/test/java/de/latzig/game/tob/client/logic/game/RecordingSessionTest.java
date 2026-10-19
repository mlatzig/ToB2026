package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.TowerChecksum;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.Replayer;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.Snapshots;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class RecordingSessionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T18:30:05.750Z"), ZoneOffset.UTC);
    private static final Command TURN = new Command.TurnLeft(SpiderId.ZAPPER);
    private static final Command FORWARD = new Command.Forward(SpiderId.ZAPPER);

    private static TowerDefinition tower() {
        return TestTowers.tower().level(0, "FFF", "FFF").object(1, 0, 0, ObjectType.ZAP).build();
    }

    @Test
    void recordsEveryCommandTheDriverPassesWithItsStepIndex() {
        TowerDefinition tower = tower();
        Simulation simulation = Simulation.start(tower, SimOptions.DEFAULT);
        Deque<Optional<Command>> answers = new ArrayDeque<>(List.of(Optional.of(TURN), Optional.empty(),
                Optional.empty(), Optional.of(FORWARD), Optional.empty()));
        RecordingSession session = new RecordingSession(tower, SimOptions.DEFAULT, CLOCK);
        SimDriver driver = new SimDriver(simulation, session.recording(sim -> answers.poll()), (events, tick) -> {
        });

        driver.update(4 * SimDriver.FRAME_SECONDS + 0.001);
        driver.update(SimDriver.FRAME_SECONDS);

        assertEquals(5, driver.ticks());
        assertEquals(5, session.length());
        Recording recording = session.close(Outcome.ABANDONED, simulation.frame());
        assertEquals(List.of(new Recording.Entry(0, TURN), new Recording.Entry(3, FORWARD)), recording.entries());
        assertEquals(5, recording.length());
        assertEquals(tower.id(), recording.towerId());
        Replayer replayer = new Replayer(recording, tower);
        replayer.run(new ArrayList<>());
        assertEquals(Snapshots.of(simulation), Snapshots.of(replayer.simulation()), "the recording replays exactly");
    }

    @Test
    void closingAddsTheOutcomeTheTimeTheFramesTheChecksumAndTheGame() {
        TowerDefinition tower = tower();
        RecordingSession session = new RecordingSession(tower, SimOptions.DEFAULT, CLOCK);
        session.onStep(Optional.of(TURN));

        Recording recording = session.close(Outcome.COMPLETED, 42);

        assertEquals(Optional.of(new RecordingMeta(Outcome.COMPLETED, Instant.parse("2026-10-09T18:30:05Z"), 42,
                TowerChecksum.of(tower), RecordingSession.GAME)), recording.meta());
        assertEquals(1, recording.length());
        assertTrue(RecordingSession.GAME.startsWith("ToB 2026 "));
    }

    @Test
    void aSessionClosesOnceAndRecordsNothingAfterwards() {
        RecordingSession session = new RecordingSession(tower(), SimOptions.DEFAULT, CLOCK);
        assertFalse(session.isClosed());

        session.close(Outcome.CRASH, 0);

        assertTrue(session.isClosed());
        assertThrows(IllegalStateException.class, () -> session.close(Outcome.ABANDONED, 0));
        assertThrows(IllegalStateException.class, () -> session.onStep(Optional.empty()));
    }
}
