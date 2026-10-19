package de.latzig.game.tob.client.logic.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Turns key presses into simulation commands. Steering, selection and panel presses wait in a queue; the
 * {@link SimDriver} takes one per frame (Phase 2 carry-over 5) and the press is mapped then, against the selection of
 * that moment, so "select pusher, forward" moves the pusher even when both keys arrive in one frame. A command the
 * simulation refuses (a throttle, a blocked step) is not retried, as with the original panel; only a held input is
 * sent again by its repeat (on a spider the steering keys in every frame, so the first frame the throttle allows
 * accepts it, as the original's held mouse button, report 18 §4).
 * <p>
 * Every panel action addresses the selected viewpoint, as in the original ({@code S:8548-8584}): on a spider the
 * spider commands, on a camera the camera commands, where Stop and Run (and PgUp/PgDn) zoom in and out. In write
 * mode the simulation turns plain steering into program steps itself; Ctrl+arrows and Ctrl+Space always send a write.
 * <p>
 * Held inputs repeat by the {@link HoldRepeat} rule (Phase 3b-2): a repeat is only sent in a frame whose queue is
 * empty. A command submitted by the pause menu (Abandon, Restart) replaces everything waiting and goes first.
 */
public final class InputMapper implements CommandSource {

    private final Deque<GameAction> queue = new ArrayDeque<>();
    private final HoldRepeat hold = new HoldRepeat();
    private Command submitted;
    private boolean paused;

    /**
     * Queues every steering, selection and panel action and starts holding it. While paused (review finding M2), a
     * press is dropped instead of queued, so it cannot be replayed once the game resumes.
     */
    public void press(GameAction action) {
        if (paused) {
            return;
        }
        if (action.isQueued()) {
            queue.add(action);
            hold.press(action);
        }
    }

    /**
     * A panel button pressed with the mouse: queued like {@link #press}, and held by the original's rule (it repeats
     * in every frame until released, whatever the action, {@link HoldRepeat#pressPanel}).
     */
    public void pressPanel(GameAction action) {
        if (paused) {
            return;
        }
        if (action.isQueued()) {
            queue.add(action);
            hold.pressPanel(action);
        }
    }

    /** The key or the panel button of {@code action} was released: its repeat stops at once. */
    public void release(GameAction action) {
        hold.release(action);
    }

    /**
     * Drops the held key or panel button without touching the queue or a submitted command (Task 9 review, addition
     * B): a release missed while the raw listener that would report it is detached, e.g. in
     * {@code GameState.onDisable}, must not keep repeating once a listener is attached again.
     */
    public void releaseHeld() {
        hold.clear();
    }

    /** Counts the time the held input has been held; called once per render frame, not while paused. */
    public void advance(double seconds) {
        hold.advance(seconds);
    }

    /**
     * Sends {@code command} in the next frame, before any queued press (the pause menu's Abandon or Restart); the
     * queue and the held input are dropped.
     */
    public void submit(Command command) {
        queue.clear();
        hold.clear();
        submitted = command;
    }

    /**
     * Tracks the pause state so {@link #press} can drop presses made while paused (review finding M2). Unpausing
     * also clears any press still waiting, so nothing queued during the pause is replayed after resume. Pausing and
     * unpausing both stop a held input.
     */
    public void setPaused(boolean paused) {
        if (this.paused && !paused) {
            queue.clear();
        }
        if (this.paused != paused) {
            hold.clear();
        }
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    @Override
    public Optional<Command> next(Simulation simulation) {
        if (submitted != null) {
            Optional<Command> command = Optional.of(submitted);
            submitted = null;
            return command;
        }
        Viewpoint selected = simulation.selected();
        while (!queue.isEmpty()) {
            Optional<Command> command = toCommand(queue.poll(), selected);
            if (command.isPresent()) {
                hold.sent();
                return command;
            }
        }
        ProgramState state = switch (selected) {
            case SpiderId spider -> simulation.program(spider).state();
            case CameraId camera -> simulation.cameraProgram(camera).state();
        };
        Optional<Command> repeat = hold.due(selected, state).flatMap(action -> toCommand(action, selected));
        if (repeat.isPresent()) {
            hold.sent();
        }
        return repeat;
    }

    /**
     * Task 16: the command {@link #next} would send in the next frame if no key changed until then: a submitted
     * command, the first waiting press that maps to a command, else the held input (assumed seen held by then).
     * Nothing is taken from the queue.
     */
    @Override
    public Optional<Command> peek(Simulation simulation) {
        if (submitted != null) {
            return Optional.of(submitted);
        }
        Viewpoint selected = simulation.selected();
        for (GameAction action : queue) {
            Optional<Command> command = toCommand(action, selected);
            if (command.isPresent()) {
                return command;
            }
        }
        ProgramState state = switch (selected) {
            case SpiderId spider -> simulation.program(spider).state();
            case CameraId camera -> simulation.cameraProgram(camera).state();
        };
        return hold.peek(selected, state).flatMap(action -> toCommand(action, selected));
    }

    /** Presses waiting for a frame. */
    public int pending() {
        return queue.size();
    }

    /** Drops every waiting press, the held input and a submitted command. */
    public void clear() {
        queue.clear();
        hold.clear();
        submitted = null;
    }

    /**
     * The selection to send at the start of an attempt: the simulation starts with the zapper selected, so a tower
     * without a zapper (e.g. the first tutorial tower) gets the first spider it has; empty if the zapper is there or
     * the tower has no spider.
     */
    public static Optional<GameAction> initialSelection(WorldState world) {
        return switch (initialSpider(world)) {
            case ZAPPER -> Optional.empty();
            case PUSHER -> Optional.of(GameAction.SELECT_PUSHER);
            case GRABBER -> Optional.of(GameAction.SELECT_GRABBER);
        };
    }

    /**
     * The spider to show as the camera's and the HUD's viewpoint before the first step has applied the queued
     * {@link #initialSelection} command (review finding M1): the same first-present rule as {@link #initialSelection},
     * but naming the spider itself rather than a queued action, so a zapperless tower's start (and every restart)
     * displays its first present spider instead of the absent zapper's noise. {@link SpiderId#ZAPPER} both when the
     * zapper is present and, harmlessly, when the tower has no spider at all (nothing to show either way).
     */
    public static SpiderId initialSpider(WorldState world) {
        if (world.spider(SpiderId.ZAPPER).cell() != null) {
            return SpiderId.ZAPPER;
        }
        if (world.spider(SpiderId.PUSHER).cell() != null) {
            return SpiderId.PUSHER;
        }
        if (world.spider(SpiderId.GRABBER).cell() != null) {
            return SpiderId.GRABBER;
        }
        return SpiderId.ZAPPER;
    }

    /** The command of a press for the selected viewpoint; empty for keys that do nothing there. */
    public static Optional<Command> toCommand(GameAction action, Viewpoint selected) {
        Optional<Command> selection = selection(action);
        if (selection.isPresent()) {
            return selection;
        }
        if (action == GameAction.PROGRAM_RUN_ALL) {
            return Optional.of(new Command.RunAll());
        }
        Optional<Integer> slot = slot(action);
        Optional<ProgramStep> step = programStep(action);
        return switch (selected) {
            case SpiderId spider -> slot.isPresent() ? Optional.of(new Command.SetCursor(spider, slot.get()))
                    : step.isPresent() ? Optional.of(new Command.Write(spider, step.get()))
                    : spiderCommand(action, spider);
            case CameraId camera -> slot.isPresent() ? Optional.of(new Command.CameraSetCursor(camera, slot.get()))
                    : step.isPresent() ? Optional.of(new Command.CameraWrite(camera, step.get()))
                    : cameraCommand(action, camera);
        };
    }

    private static Optional<Command> selection(GameAction action) {
        return Optional.ofNullable(switch (action) {
            case SELECT_ZAPPER -> new Command.SelectSpider(SpiderId.ZAPPER);
            case SELECT_PUSHER -> new Command.SelectSpider(SpiderId.PUSHER);
            case SELECT_GRABBER -> new Command.SelectSpider(SpiderId.GRABBER);
            case CAMERA_NORTH -> new Command.SelectCamera(CameraId.NORTH);
            case CAMERA_EAST -> new Command.SelectCamera(CameraId.EAST);
            case CAMERA_SOUTH -> new Command.SelectCamera(CameraId.SOUTH);
            case CAMERA_WEST -> new Command.SelectCamera(CameraId.WEST);
            default -> null;
        });
    }

    /** Keys 1–8: the program slots 0–7. */
    private static Optional<Integer> slot(GameAction action) {
        return Optional.ofNullable(switch (action) {
            case PROGRAM_SLOT_1 -> 0;
            case PROGRAM_SLOT_2 -> 1;
            case PROGRAM_SLOT_3 -> 2;
            case PROGRAM_SLOT_4 -> 3;
            case PROGRAM_SLOT_5 -> 4;
            case PROGRAM_SLOT_6 -> 5;
            case PROGRAM_SLOT_7 -> 6;
            case PROGRAM_SLOT_8 -> 7;
            default -> null;
        });
    }

    /** Ctrl+arrows and Ctrl+Space (ToB 2009 {@code prg_*}): write one step. */
    private static Optional<ProgramStep> programStep(GameAction action) {
        return Optional.ofNullable(switch (action) {
            case PROGRAM_UP -> ProgramStep.FORWARD;
            case PROGRAM_DOWN -> ProgramStep.BACK;
            case PROGRAM_LEFT -> ProgramStep.LEFT;
            case PROGRAM_RIGHT -> ProgramStep.RIGHT;
            case PROGRAM_FIRE -> ProgramStep.FIRE;
            default -> null;
        });
    }

    private static Optional<Command> spiderCommand(GameAction action, SpiderId spider) {
        return Optional.ofNullable(switch (action) {
            case FORWARD -> new Command.Forward(spider);
            case BACK -> new Command.Back(spider);
            case TURN_LEFT -> new Command.TurnLeft(spider);
            case TURN_RIGHT -> new Command.TurnRight(spider);
            case FIRE -> new Command.Fire(spider);
            case PROGRAM_WRITE -> new Command.EnterWriteMode(spider);
            case PROGRAM_DELETE -> new Command.Delete(spider);
            case PROGRAM_CLEAR -> new Command.Clear(spider);
            case PROGRAM_STOP -> new Command.Stop(spider);
            case PROGRAM_RUN -> new Command.Run(spider);
            default -> null;
        });
    }

    private static Optional<Command> cameraCommand(GameAction action, CameraId camera) {
        return Optional.ofNullable(switch (action) {
            case FORWARD -> new Command.CameraForward(camera);
            case BACK -> new Command.CameraBack(camera);
            case TURN_LEFT -> new Command.CameraTurnLeft(camera);
            case TURN_RIGHT -> new Command.CameraTurnRight(camera);
            case FIRE -> new Command.CameraFire(camera);
            case PROGRAM_WRITE -> new Command.CameraEnterWriteMode(camera);
            case PROGRAM_DELETE -> new Command.CameraDelete(camera);
            case PROGRAM_CLEAR -> new Command.CameraClear(camera);
            case PROGRAM_STOP, ZOOM_IN -> new Command.CameraZoomIn(camera);
            case PROGRAM_RUN, ZOOM_OUT -> new Command.CameraZoomOut(camera);
            default -> null;
        });
    }
}
