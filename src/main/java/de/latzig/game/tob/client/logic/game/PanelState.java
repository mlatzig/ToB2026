package de.latzig.game.tob.client.logic.game;

import java.util.EnumMap;
import java.util.Map;

import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.SpiderProgram;
import de.latzig.game.tob.core.sim.Viewpoint;

/**
 * The look of the panel buttons (Phase 3b-2 spec §3.2, ToB 2009 {@code GamePanelWindowNormal.java:302-583}):
 * <ul>
 * <li>the shown spider or camera is pressed; a spider that is not alive (destroyed or absent), and every camera of a
 * tower with cameras disabled, is grey while not shown;</li>
 * <li>steering, Stop/Run/Run All and Write/Delete/Clear flash pressed for {@value #FLASH_SECONDS} s after their command
 * is sent - Run and Run All only once the simulation has accepted them (acceptance fix 3) - and the steering button
 * of a step that the shown spider's running program executes flashes too;</li>
 * <li>Run stays pressed while the shown spider's program runs; instead Run All stays pressed while the shown spider is
 * still the one that was shown when the simulation accepted a Run All and its program still runs (final review M4,
 * fix 2; see {@link #runAllAt}); Write stays pressed while the shown viewpoint's program is in write mode, Pause while
 * the game is paused;</li>
 * <li>each program slot shows the icon of its step; the slot at the cursor is pressed in every state, not only write
 * mode (final review M4: {@code tob-rewrite.s} {@code L009E7A}, {@code S:8141-8154}, highlights the cursor slot
 * unconditionally; ToB 2009 {@code GamePanelWindowNormal.updateProgramButtons} does too, {@code position == i}), the
 * executing step while running.</li>
 * </ul>
 */
public final class PanelState {

    public static final float FLASH_SECONDS = 0.25f;

    /** How a button is drawn: its {@code 0} region, its {@code 1} region, or its {@code 0} region tinted grey. */
    public enum Look {
        NORMAL, PRESSED, GREY
    }

    private final Map<PanelButton, Float> flashes = new EnumMap<>(PanelButton.class);
    /**
     * The spider shown when the simulation last accepted a Run All, while Run All is still drawn pressed; null
     * otherwise (final review M4, fix 2). The original ({@code tob-rewrite.s}) has one highlight slot {@code W001C14}
     * for Stop..Clear, not a per-spider flag: Run All ({@code S:8525-8538}) sets {@code W001C14 := 2} but writes state
     * 1, the value of Run ({@code S:8518-8519}), into all three spider programs ({@code S:8530-8533}). So Run All is
     * pressed only for the spider shown at the click and is dropped for good by a viewpoint switch ({@code S:8329}:
     * {@code W001C14 := $4(a5)}, i.e. 1, Run), the end of that program ({@code S:10115-10118}: -1), and Stop, Write,
     * Delete, Clear ({@code S:8496}, {@code S:8546}, {@code S:8570}, {@code S:8578}: 0, 3, 4, 5, each also stopping
     * the program) or a Run ({@code S:8519}: 1). A click refused by the program throttle ({@code tst.w W001C06},
     * {@code S:8515-8516}, {@code S:8525-8526}) changes nothing. From a camera Run All only flashes (the camera's own
     * {@code $4(a5)} is 2, so the 8-frame timer clears it, {@code S:8233-8242}).
     */
    private SpiderId runAllAt;
    /** The Run or Run All sent for the coming step, judged in {@link #onStep} once the step shows its effect. */
    private Command pendingRun;
    /** Each spider program's state and cursor after the previous step: whether a Run/Run All restarted it. */
    private final Map<SpiderId, ProgramState> stateBefore = new EnumMap<>(SpiderId.class);
    private final Map<SpiderId, Integer> cursorBefore = new EnumMap<>(SpiderId.class);
    private Viewpoint stepViewpoint;
    private int stepCursor;

    /**
     * Flashes the button of a command sent to the simulation (accepted or not, as ToB 2009) - except Run and Run All
     * (acceptance fix 3): in the original a Run or Run All refused by the program throttle ({@code S:8515-8516},
     * {@code S:8525-8526}) or for a dead selected spider shows nothing, so they are kept for {@link #onStep}, which
     * flashes them only once the step's effect shows that the simulation accepted them.
     */
    public void onCommand(Command command) {
        if (command instanceof Command.Run || command instanceof Command.RunAll) {
            pendingRun = command;
            return;
        }
        PanelButton button = switch (command) {
            case Command.Forward c -> PanelButton.UP;
            case Command.CameraForward c -> PanelButton.UP;
            case Command.Back c -> PanelButton.DOWN;
            case Command.CameraBack c -> PanelButton.DOWN;
            case Command.TurnLeft c -> PanelButton.LEFT;
            case Command.CameraTurnLeft c -> PanelButton.LEFT;
            case Command.TurnRight c -> PanelButton.RIGHT;
            case Command.CameraTurnRight c -> PanelButton.RIGHT;
            case Command.Fire c -> PanelButton.FIRE;
            case Command.CameraFire c -> PanelButton.FIRE;
            case Command.Write c -> stepButton(c.step());
            case Command.CameraWrite c -> stepButton(c.step());
            case Command.Stop c -> PanelButton.STOP;
            case Command.CameraZoomIn c -> PanelButton.STOP;
            case Command.CameraZoomOut c -> PanelButton.RUN;
            case Command.Delete c -> PanelButton.DELETE;
            case Command.CameraDelete c -> PanelButton.DELETE;
            case Command.Clear c -> PanelButton.CLEAR;
            case Command.CameraClear c -> PanelButton.CLEAR;
            default -> null;
        };
        if (button != null) {
            flash(button);
        }
    }

    /**
     * After every simulation step: a step that the shown spider's running program has just executed flashes its
     * steering button. Switching the viewpoint never flashes. Also keeps {@link #runAllAt} as the original does.
     */
    public void onStep(Simulation simulation, Viewpoint shown) {
        followRunAll(simulation, shown);
        SpiderProgram program = program(simulation, shown);
        int cursor = program.cursor();
        boolean sameViewpoint = shown.equals(stepViewpoint);
        stepViewpoint = shown;
        int previous = stepCursor;
        stepCursor = cursor;
        if (!sameViewpoint || !(shown instanceof SpiderId) || program.state() != ProgramState.RUNNING
                || cursor == previous || cursor < 0 || cursor >= SpiderProgram.SLOTS) {
            return;
        }
        PanelButton button = stepButton(program.slot(cursor));
        if (button != null) {
            flash(button);
        }
    }

    private void followRunAll(Simulation simulation, Viewpoint shown) {
        Command sent = pendingRun;
        pendingRun = null;
        if (sent instanceof Command.Run run && restarted(simulation, run.spider())) {
            runAllAt = null;
            flash(PanelButton.RUN);
        } else if (sent instanceof Command.RunAll && anyRestarted(simulation)) {
            runAllAt = shown instanceof SpiderId spider ? spider : null;
            flash(PanelButton.RUN_ALL);
        }
        if (runAllAt != null && (!runAllAt.equals(shown)
                || simulation.program(runAllAt).state() != ProgramState.RUNNING)) {
            runAllAt = null;
        }
        for (SpiderId spider : SpiderId.values()) {
            SpiderProgram program = simulation.program(spider);
            stateBefore.put(spider, program.state());
            cursorBefore.put(spider, program.cursor());
        }
    }

    /**
     * The pause has ended (acceptance fix 4): leaving the original's options menu redraws the panel with Run
     * highlighted ({@code S:8089-8090}), so a Run All highlight is dropped for good and Run shows while the program
     * still runs.
     */
    public void onPauseEnd() {
        runAllAt = null;
    }

    private boolean anyRestarted(Simulation simulation) {
        for (SpiderId spider : SpiderId.values()) {
            if (restarted(simulation, spider)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the step just taken (re)started the spider's program: it runs now, and it did not run before or its
     * cursor went back (a running program only moves its cursor forward). {@code core.sim} keeps the program throttle
     * to itself, so a restart of a program that was itself started only a step or two ago (still at cursor -1 or 0)
     * looks like no restart and counts as refused, which is what the 8-step throttle does to such a click anyway.
     */
    private boolean restarted(Simulation simulation, SpiderId spider) {
        SpiderProgram program = simulation.program(spider);
        if (program.state() != ProgramState.RUNNING) {
            return false;
        }
        ProgramState before = stateBefore.getOrDefault(spider, ProgramState.IDLE);
        return before != ProgramState.RUNNING || program.cursor() < cursorBefore.getOrDefault(spider, 0);
    }

    private boolean runAllHeld(Simulation simulation, Viewpoint shown) {
        return runAllAt != null && runAllAt.equals(shown)
                && simulation.program(runAllAt).state() == ProgramState.RUNNING;
    }

    public void flash(PanelButton button) {
        flashes.put(button, FLASH_SECONDS);
    }

    /** Lets the flashes run down by {@code seconds} of wall-clock time. */
    public void advance(float seconds) {
        flashes.replaceAll((button, left) -> left - seconds);
        flashes.values().removeIf(left -> left <= 0f);
    }

    public Look look(PanelButton button, Simulation simulation, Viewpoint shown, boolean paused) {
        SpiderProgram program = program(simulation, shown);
        boolean pressed = switch (button) {
            case ZAPPER, PUSHER, GRABBER, NORTH, EAST, SOUTH, WEST -> viewpoint(button).equals(shown);
            case PAUSE -> paused;
            case RUN -> flashes.containsKey(button) || shown instanceof SpiderId
                    && program.state() == ProgramState.RUNNING && !runAllHeld(simulation, shown);
            case RUN_ALL -> flashes.containsKey(button) || runAllHeld(simulation, shown);
            case WRITE -> program.state() == ProgramState.WRITING;
            // M4: the slot at the cursor is pressed in every state (tob-rewrite.s L009E7A, S:8141-8154: the original
            // highlights it unconditionally; so does ToB 2009's position == i), not only write mode/running.
            case SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7, SLOT_8 -> program.cursor() == button.slot();
            default -> flashes.containsKey(button);
        };
        if (pressed) {
            return Look.PRESSED;
        }
        boolean grey = switch (button) {
            case ZAPPER, PUSHER, GRABBER -> !simulation.world().spider((SpiderId) viewpoint(button)).isAlive();
            case NORTH, EAST, SOUTH, WEST -> !simulation.world().tower().behaviour().camerasEnabled();
            default -> false;
        };
        return grey ? Look.GREY : Look.NORMAL;
    }

    /** The atlas region of a button, e.g. {@code panel.run_all1}; a slot shows the icon of its step. */
    public String region(PanelButton button, Look look, Simulation simulation, Viewpoint shown) {
        String name = button.slot() >= 0 ? slotRegion(program(simulation, shown).slot(button.slot()))
                : button.region();
        return "panel." + name + (look == Look.PRESSED ? "1" : "0");
    }

    /** The slot icon of a program step, without prefix and suffix (ToB 2009 {@code setProgramImageButton}). */
    public static String slotRegion(ProgramStep step) {
        return switch (step) {
            case EMPTY -> "prg_spc";
            case FORWARD -> "prg_up";
            case BACK -> "prg_down";
            case LEFT -> "prg_left";
            case RIGHT -> "prg_right";
            case FIRE -> "prg_fire";
        };
    }

    /** The viewpoint a selection button selects. */
    public static Viewpoint viewpoint(PanelButton button) {
        return switch (button) {
            case ZAPPER -> SpiderId.ZAPPER;
            case PUSHER -> SpiderId.PUSHER;
            case GRABBER -> SpiderId.GRABBER;
            case NORTH -> CameraId.NORTH;
            case EAST -> CameraId.EAST;
            case SOUTH -> CameraId.SOUTH;
            case WEST -> CameraId.WEST;
            default -> throw new IllegalArgumentException(button + " selects no viewpoint");
        };
    }

    private static SpiderProgram program(Simulation simulation, Viewpoint viewpoint) {
        return switch (viewpoint) {
            case SpiderId spider -> simulation.program(spider);
            case CameraId camera -> simulation.cameraProgram(camera);
        };
    }

    private static PanelButton stepButton(ProgramStep step) {
        return switch (step) {
            case EMPTY -> null;
            case FORWARD -> PanelButton.UP;
            case BACK -> PanelButton.DOWN;
            case LEFT -> PanelButton.LEFT;
            case RIGHT -> PanelButton.RIGHT;
            case FIRE -> PanelButton.FIRE;
        };
    }
}
