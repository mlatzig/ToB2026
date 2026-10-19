package de.latzig.game.tob.client.logic.game;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.RecordingMeta.Outcome;
import de.latzig.game.tob.core.sim.Rules;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;

/**
 * The developer recording of one attempt in the client (Phase 3c, reworked after the author's decision of
 * 2026-10-09: recordings exist only as a development tool for regression tests).
 * <ul>
 * <li>The attempt is recorded ({@link RecordingSession}) only if the {@link RecordingSwitch} is on when it starts: a
 * recording must begin at the attempt's first step.</li>
 * <li>{@link #finished} saves the recording only for a completed attempt; failed, timed-out, abandoned and restarted
 * attempts are dropped, and so is an attempt that is left before its end (nothing is called).</li>
 * <li>{@link #crashed} saves a {@code crash} recording, for the developer's analysis, while the attempt is recorded.
 * </li>
 * <li>{@link #toggle} (F12): switching off drops this attempt's recording at once; switching on takes effect at the
 * next attempt start (the caption shows {@code REC NEXT} until then).</li>
 * </ul>
 * A write error is logged and the game goes on. Plain Java, no engine.
 */
public final class AttemptRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(AttemptRecorder.class);

    private final RecordingSwitch recordingSwitch;
    private final RecordingStore store;
    private final String towerId;
    /** This attempt's recording; null while it is not recorded or once it has ended. */
    private RecordingSession session;
    /** Whether this attempt is recorded from its start (also after it has been saved). */
    private boolean recorded;

    /** @param clock the time written as {@code recordedAt} */
    public AttemptRecorder(RecordingSwitch recordingSwitch, RecordingStore store, TowerDefinition tower,
            SimOptions options, Clock clock) {
        this.recordingSwitch = Objects.requireNonNull(recordingSwitch, "recordingSwitch");
        this.store = Objects.requireNonNull(store, "store");
        this.towerId = tower.id();
        this.recorded = recordingSwitch.isOn();
        this.session = recorded ? new RecordingSession(tower, options, clock) : null;
    }

    /**
     * {@code source}, recording every answer as the command of the next step while the attempt is recorded; its
     * {@link CommandSource#peek} is passed through unrecorded (Task 16).
     */
    public CommandSource recording(CommandSource source) {
        Objects.requireNonNull(source, "source");
        return new CommandSource() {
            @Override
            public Optional<Command> next(Simulation simulation) {
                Optional<Command> command = source.next(simulation);
                if (session != null) {
                    session.onStep(command);
                }
                return command;
            }

            @Override
            public Optional<Command> peek(Simulation simulation) {
                return source.peek(simulation);
            }
        };
    }

    /** Whether the running attempt is being recorded. */
    public boolean isRecording() {
        return session != null;
    }

    /** F12: toggles the switch; off drops this attempt's recording, on applies from the next attempt start. */
    public void toggle() {
        recordingSwitch.toggle();
        if (!recordingSwitch.isOn()) {
            session = null;
            recorded = false;
        }
    }

    /**
     * The attempt ended with {@code exitCode} ({@link Rules}): saves the recording if the attempt is completed.
     *
     * @param frames the simulation's frame number at the end
     * @return the saved file, if any
     */
    public Optional<Path> finished(int exitCode, int frames) {
        RecordingSession ended = take();
        if (ended == null || exitCode != Rules.EXIT_COMPLETED) {
            return Optional.empty();
        }
        return save(ended, Outcome.COMPLETED, frames);
    }

    /**
     * A frame threw: saves a {@code crash} recording while the attempt is recorded.
     *
     * @param frames the simulation's frame number at the crash
     * @return the saved file, if any
     */
    public Optional<Path> crashed(int frames) {
        RecordingSession ended = take();
        return ended == null ? Optional.empty() : save(ended, Outcome.CRASH, frames);
    }

    /**
     * The caption strip: {@code "REC  "} before it while this attempt is recorded, {@code "REC NEXT  "} while only the
     * next one will be, else unchanged.
     */
    public String caption(String caption) {
        if (recorded) {
            return "REC  " + caption;
        }
        return recordingSwitch.isOn() ? "REC NEXT  " + caption : caption;
    }

    private RecordingSession take() {
        RecordingSession ended = session;
        session = null;
        return ended == null || ended.length() == 0 ? null : ended;
    }

    private Optional<Path> save(RecordingSession ended, Outcome outcome, int frames) {
        try {
            Path file = store.save(ended.close(outcome, frames));
            LOG.info("Tower {}: {} recording saved to {}", towerId, outcome.fileName(), file);
            return Optional.of(file);
        } catch (IOException | RuntimeException e) {
            LOG.error("Tower {}: the {} recording cannot be saved: {}", towerId, outcome.fileName(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}
