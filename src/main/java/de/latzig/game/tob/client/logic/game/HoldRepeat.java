package de.latzig.game.tob.client.logic.game;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;

/**
 * Hold-to-repeat (Phase 3b-2 spec §4.2). The first step of a press goes through the queue at once; {@link #due} then
 * names the held input again in every frame whose queue is empty (no backlog), until it is released.
 * <ul>
 * <li><b>Panel buttons held with the mouse</b> behave as in the original, which reads the held left button and hits
 * the panel in every frame ({@code S:5862-5869}, {@code S:8247-8281}): every queued action repeats from the next
 * frame on, on every viewpoint and in write mode, paced only by the simulation's throttles. (The Pause button is a
 * client action and never held: the original leaves the play loop and waits for the released mouse button,
 * {@code S:8333-8339}, {@code S:6962}.)</li>
 * <li><b>Steering keys on a spider</b> (Forward, Back, Turn left, Turn right, Fire) behave like held panel buttons
 * (report 18 §4, fix (a), the author's decision of 2026-10-12): the original steered only with the held mouse button,
 * so a held key repeats in every frame from the next frame on, without a delay, and the simulation's throttles alone
 * decide which repeat it accepts (turn 4 frames, fire 8 after a hit and 1 after a miss, move 8, a walk 32). A turn key
 * held through a swap therefore turns in frame +8, when the hit throttle runs out. A key repeats only once it has been
 * seen held at a frame boundary after its last command ({@link #advance} is called once per render frame, after the
 * key events): a tap gives exactly one command even when a stalled render frame runs several simulation frames
 * before its release is seen, as a click seen in one original frame gave one.</li>
 * <li><b>Other keys</b> repeat after {@value #DELAY_SECONDS} s: on a camera the moves (arrows), the zoom
 * (PgUp/PgDn) and Stop/Run (S/R, which zoom on a camera).</li>
 * <li>No key repeats in write mode, where the steering keys write program steps.</li>
 * </ul>
 * The last press of a holdable input is the one held; releasing another input does not stop it.
 */
public final class HoldRepeat {

    public static final double DELAY_SECONDS = 0.3;

    /** The keys that repeat on a camera after {@link #DELAY_SECONDS}. */
    public static final Set<GameAction> CAMERA_REPEATS = EnumSet.of(GameAction.FORWARD, GameAction.BACK,
            GameAction.TURN_LEFT, GameAction.TURN_RIGHT, GameAction.ZOOM_IN, GameAction.ZOOM_OUT,
            GameAction.PROGRAM_STOP, GameAction.PROGRAM_RUN);

    /** The keys that repeat on a spider in every frame, like held panel buttons (report 18 §4, fix (a)). */
    public static final Set<GameAction> SPIDER_REPEATS = EnumSet.of(GameAction.FORWARD, GameAction.BACK,
            GameAction.TURN_LEFT, GameAction.TURN_RIGHT, GameAction.FIRE);

    private GameAction held;
    private boolean panel;
    private double heldFor;
    /** Whether the held key has been seen held at a frame boundary since its last command was sent. */
    private boolean seen;

    /** A key: starts holding {@code action} if it can repeat on some viewpoint; other keys leave the hold alone. */
    public void press(GameAction action) {
        if (CAMERA_REPEATS.contains(action) || SPIDER_REPEATS.contains(action)) {
            held = action;
            panel = false;
            heldFor = 0.0;
            seen = false;
        }
    }

    /** A panel button pressed with the mouse: every queued action is held (original rule). */
    public void pressPanel(GameAction action) {
        if (action.isQueued()) {
            held = action;
            panel = true;
            heldFor = 0.0;
        }
    }

    /** Stops the repeat at once if {@code action} is the held input. */
    public void release(GameAction action) {
        if (action == held) {
            held = null;
        }
    }

    public void clear() {
        held = null;
    }

    /**
     * Counts the holding time; called once per render frame, after the key events, with the elapsed wall-clock (or
     * fast-forward) seconds. The held input counts as seen held at this frame boundary.
     */
    public void advance(double seconds) {
        if (held != null) {
            heldFor += Math.max(0.0, seconds);
            seen = true;
        }
    }

    /**
     * A command was sent in this simulation frame (a queued press or a repeat): a held spider steering key repeats
     * again only after the next {@link #advance} has seen it still held.
     */
    public void sent() {
        seen = false;
    }

    /** The held input, if any (tests). */
    public Optional<GameAction> held() {
        return Optional.ofNullable(held);
    }

    /**
     * The input to repeat in this frame: a held panel button always; a held key if it repeats on the selected
     * viewpoint and that viewpoint's program is not in write mode – on a spider once it has been seen held since its
     * last command, on a camera once it has been held for {@link #DELAY_SECONDS}. The caller asks only when its queue
     * is empty (no backlog) and reports every command it sends with {@link #sent}.
     */
    public Optional<GameAction> due(Viewpoint selected, ProgramState selectedProgram) {
        return due(selected, selectedProgram, false);
    }

    /**
     * Task 16: the input {@link #due} will name in the next frame if it stays held, without waiting for it to be seen
     * held (by then the next {@link #advance} has seen it); no side effect.
     */
    public Optional<GameAction> peek(Viewpoint selected, ProgramState selectedProgram) {
        return due(selected, selectedProgram, true);
    }

    private Optional<GameAction> due(Viewpoint selected, ProgramState selectedProgram, boolean assumeSeen) {
        if (held == null) {
            return Optional.empty();
        }
        if (panel) {
            return Optional.of(held);
        }
        if (selectedProgram == ProgramState.WRITING || !repeats(held, selected)) {
            return Optional.empty();
        }
        boolean ready = switch (selected) {
            case CameraId camera -> heldFor >= DELAY_SECONDS;
            case SpiderId spider -> seen || assumeSeen;
        };
        return ready ? Optional.of(held) : Optional.empty();
    }

    /**
     * Whether the key of {@code action} repeats while held with {@code selected} (spec §4.2 table; on a spider
     * report 18 §4, fix (a)).
     */
    public static boolean repeats(GameAction action, Viewpoint selected) {
        return switch (selected) {
            case CameraId camera -> CAMERA_REPEATS.contains(action);
            case SpiderId spider -> SPIDER_REPEATS.contains(action);
        };
    }
}
