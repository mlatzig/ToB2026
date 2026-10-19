package de.latzig.game.tob.client.logic.game;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.core.io.TowerChecksum;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.SimOptions;

/**
 * The recording of one attempt in the client (Phase 3c spec §2): exactly the commands the {@link SimDriver} passes to
 * {@code Simulation.step}, each with the index of its step, from the attempt's first step to its exit. Wrap the
 * driver's {@link CommandSource} with {@link #recording}: every call of the wrapped source is one step, because the
 * driver steps the simulation right after asking (a step that throws is still counted, so a crash recording replays
 * up to and including the failing step). Pause, the panel and the other client-only actions never reach the source
 * and are not recorded. {@link #close} ends the session once, with the outcome and the metadata.
 */
public final class RecordingSession {

    /** The game written into every recording: "ToB 2026" and the project version of the POM. */
    public static final String GAME = "ToB 2026 0.1.0-SNAPSHOT";

    private final String towerId;
    private final SimOptions options;
    private final String towerChecksum;
    private final Clock clock;
    private final List<Recording.Entry> entries = new ArrayList<>();
    private int length;
    private boolean closed;

    /** @param clock the time of {@link #close} ({@code recordedAt}, whole seconds) */
    public RecordingSession(TowerDefinition tower, SimOptions options, Clock clock) {
        this.towerId = tower.id();
        this.options = Objects.requireNonNull(options, "options");
        this.towerChecksum = TowerChecksum.of(tower);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code source}, recording every answer as the command of the next step. */
    public CommandSource recording(CommandSource source) {
        Objects.requireNonNull(source, "source");
        return simulation -> {
            Optional<Command> command = source.next(simulation);
            onStep(command);
            return command;
        };
    }

    /**
     * The next step receives {@code command} (empty: a step without input).
     *
     * @throws IllegalStateException after {@link #close}
     */
    public void onStep(Optional<Command> command) {
        if (closed) {
            throw new IllegalStateException("the recording of " + towerId + " is closed");
        }
        command.ifPresent(c -> entries.add(new Recording.Entry(length, c)));
        length++;
    }

    /** Steps recorded so far. */
    public int length() {
        return length;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Ends the recording.
     *
     * @param frames the simulation's frame number at the end ({@code Simulation.frame()})
     * @throws IllegalStateException if already closed
     */
    public Recording close(RecordingMeta.Outcome outcome, int frames) {
        if (closed) {
            throw new IllegalStateException("the recording of " + towerId + " is already closed");
        }
        closed = true;
        RecordingMeta meta = new RecordingMeta(outcome, clock.instant().truncatedTo(ChronoUnit.SECONDS), frames,
                towerChecksum, GAME);
        return new Recording(towerId, options, length, entries).withMeta(meta);
    }
}
