package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.io.RecordingReader;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

/**
 * The developer recording of one attempt (Phase 3c rework, author's decision of 2026-10-09): off by default, on with
 * {@code tob.record=true} or F12; only completed attempts are saved, plus a crash while recording is on.
 */
class AttemptRecorderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T18:30:05Z"), ZoneOffset.UTC);
    private static final Command TURN = new Command.TurnLeft(SpiderId.ZAPPER);
    private static final String CAPTION = "KLONDIKES 0/3";

    private static TowerDefinition tower() {
        return TestTowers.tower().level(0, "FFF", "FFF").object(1, 0, 0, ObjectType.ZAP).build();
    }

    private static AttemptRecorder recorder(RecordingSwitch recordingSwitch, Path userDir) {
        return new AttemptRecorder(recordingSwitch, new RecordingStore(userDir, ZoneOffset.UTC), tower(),
                SimOptions.DEFAULT, CLOCK);
    }

    /** Asks the recorder's source for {@code steps} steps: a turn first, then steps without input. */
    private static void play(AttemptRecorder recorder, int steps) {
        Simulation simulation = Simulation.start(tower(), SimOptions.DEFAULT);
        CommandSource source = recorder.recording(new CommandSource() {
            private boolean sent;

            @Override
            public Optional<Command> next(Simulation sim) {
                Optional<Command> command = sent ? Optional.empty() : Optional.of(TURN);
                sent = true;
                return command;
            }
        });
        for (int i = 0; i < steps; i++) {
            source.next(simulation);
        }
    }

    private static List<Path> files(Path userDir) throws IOException {
        return new RecordingStore(userDir, ZoneOffset.UTC).files(tower().id());
    }

    @Test
    void recordingIsOffByDefaultAndACompletedAttemptLeavesNoFile(@TempDir Path userDir) throws IOException {
        AttemptRecorder recorder = recorder(new RecordingSwitch(false), userDir);
        play(recorder, 3);

        assertFalse(recorder.isRecording());
        assertEquals(CAPTION, recorder.caption(CAPTION), "no REC in the caption");
        assertEquals(Optional.empty(), recorder.finished(Rules.EXIT_COMPLETED, 3));
        assertFalse(Files.exists(userDir.resolve("recordings")), "nothing is written at all");
    }

    @Test
    void whileRecordingIsOnACompletedAttemptIsSaved(@TempDir Path userDir) throws IOException {
        AttemptRecorder recorder = recorder(new RecordingSwitch(true), userDir);
        play(recorder, 3);

        assertTrue(recorder.isRecording());
        assertEquals("REC  " + CAPTION, recorder.caption(CAPTION));
        Path file = recorder.finished(Rules.EXIT_COMPLETED, 3).orElseThrow();

        assertEquals(List.of(file), files(userDir));
        assertEquals("20261009-183005-completed.json", file.getFileName().toString());
        Recording recording = new RecordingReader().read(file);
        assertEquals(3, recording.length());
        assertEquals(List.of(new Recording.Entry(0, TURN)), recording.entries());
        assertEquals(Outcome.COMPLETED, recording.meta().orElseThrow().outcome());
        assertEquals("REC  " + CAPTION, recorder.caption(CAPTION), "the saved attempt was recorded");
        assertEquals(Optional.empty(), recorder.crashed(3), "saved once");
    }

    @Test
    void failedTimeUpAbandonedAndRestartedAttemptsAreNotSaved(@TempDir Path userDir) throws IOException {
        for (int exit : new int[] {Rules.EXIT_SPIDERS_DESTROYED, Rules.EXIT_GRABBER_DESTROYED, Rules.EXIT_TIMEOUT,
                Rules.EXIT_ABANDONED, Rules.EXIT_RESTART}) {
            AttemptRecorder recorder = recorder(new RecordingSwitch(true), userDir);
            play(recorder, 2);
            assertEquals(Optional.empty(), recorder.finished(exit, 2), "exit " + exit);
            assertEquals(Optional.empty(), recorder.crashed(2), "the attempt is over: exit " + exit);
        }
        AttemptRecorder left = recorder(new RecordingSwitch(true), userDir);
        play(left, 2);
        // an attempt left before its end (F9, the picker, closing the game) is simply dropped: no call at all

        assertEquals(List.of(), files(userDir));
    }

    @Test
    void aCrashIsSavedOnlyWhileRecordingIsOn(@TempDir Path userDir) throws IOException {
        AttemptRecorder off = recorder(new RecordingSwitch(false), userDir);
        play(off, 2);
        assertEquals(Optional.empty(), off.crashed(2));
        assertEquals(List.of(), files(userDir));

        AttemptRecorder on = recorder(new RecordingSwitch(true), userDir);
        play(on, 2);
        Path file = on.crashed(2).orElseThrow();

        assertEquals("20261009-183005-crash.json", file.getFileName().toString());
        assertEquals(Outcome.CRASH, new RecordingReader().read(file).meta().orElseThrow().outcome());
        assertEquals(List.of(file), files(userDir));
    }

    @Test
    void f12TurnsRecordingOffAtOnceAndOnFromTheNextAttempt(@TempDir Path userDir) throws IOException {
        RecordingSwitch recordingSwitch = new RecordingSwitch(true);
        AttemptRecorder first = recorder(recordingSwitch, userDir);
        play(first, 2);

        first.toggle();

        assertFalse(recordingSwitch.isOn());
        assertFalse(first.isRecording(), "off: the attempt's recording is dropped at once");
        assertEquals(CAPTION, first.caption(CAPTION));
        first.toggle();
        assertTrue(recordingSwitch.isOn());
        assertFalse(first.isRecording(), "on mid-attempt: this attempt has not been recorded from its start");
        assertEquals("REC NEXT  " + CAPTION, first.caption(CAPTION));
        assertEquals(Optional.empty(), first.finished(Rules.EXIT_COMPLETED, 2));
        assertEquals(List.of(), files(userDir));

        AttemptRecorder second = recorder(recordingSwitch, userDir);
        play(second, 2);
        assertTrue(second.isRecording(), "the next attempt is recorded from its start");
        assertEquals("REC  " + CAPTION, second.caption(CAPTION));
        assertTrue(second.finished(Rules.EXIT_COMPLETED, 2).isPresent());
        assertEquals(1, files(userDir).size());
    }

    /** Phase 7c: in a package the switch is locked - F12 never turns the recording on, nothing is saved. */
    @Test
    void aLockedSwitchStaysOffWhateverF12Does(@TempDir Path userDir) throws IOException {
        RecordingSwitch locked = RecordingSwitch.locked();
        AttemptRecorder recorder = recorder(locked, userDir);
        play(recorder, 2);

        recorder.toggle();

        assertFalse(locked.isOn());
        assertEquals(CAPTION, recorder.caption(CAPTION), "no REC NEXT");
        AttemptRecorder next = recorder(locked, userDir);
        play(next, 2);
        assertFalse(next.isRecording());
        assertEquals(Optional.empty(), next.finished(Rules.EXIT_COMPLETED, 2));
        assertEquals(List.of(), files(userDir));
    }
}
