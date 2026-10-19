package de.latzig.game.tob.core.sim;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Panel input (panel routine {@code L009F22}, {@code S:8204-8585}) and program execution ({@code L00BC9A},
 * {@code S:10105-10178}). Every viewpoint (three spiders, four cameras) owns a program; only spider programs run.
 * Every camera also owns a {@link CameraPose}. The throttles are global UI counters as in the original, counted down at
 * the start of every input phase ({@code S:8222-8242}): select ({@code W001C00}), turn/fire ({@code W001C02}), move
 * ({@code W001C04}) and program ({@code W001C06}). Program steps are not throttled. Direct control stays active while
 * a program runs.
 */
public final class SpiderController {

    public static final int SELECT_THROTTLE = 4;
    public static final int TURN_THROTTLE = 4;
    /** After a fire whose ray affected its target ({@link Ray#fire}). */
    public static final int FIRE_HIT_THROTTLE = 8;
    public static final int FIRE_MISS_THROTTLE = 1;
    public static final int MOVE_THROTTLE = 8;
    public static final int MOVE_FAILED_THROTTLE = 1;
    public static final int WRITE_THROTTLE = 4;
    public static final int PROGRAM_THROTTLE = 8;
    /**
     * A camera height step sets the move throttle, an offset step the turn/fire throttle, to this value; camera steps
     * check no throttle themselves ({@code S:8408-8453}).
     */
    public static final int CAMERA_STEP_THROTTLE = 1;

    private final Map<SpiderId, SpiderProgram> programs = new EnumMap<>(SpiderId.class);
    private final Map<CameraId, SpiderProgram> cameraPrograms = new EnumMap<>(CameraId.class);
    private final Map<CameraId, CameraPose> cameras = new EnumMap<>(CameraId.class);
    private Viewpoint selected = SpiderId.ZAPPER;
    private int selectThrottle;
    private int turnFireThrottle;
    private int moveThrottle;
    private int programThrottle;

    /** The controller of a tower within the original 8 × 8 area. */
    public SpiderController() {
        this(CameraLimits.ORIGINAL);
    }

    /** The controller of a tower whose cameras start and stop at {@code limits} (Phase 6a). */
    public SpiderController(CameraLimits limits) {
        for (SpiderId id : SpiderId.values()) {
            programs.put(id, new SpiderProgram());
        }
        for (CameraId id : CameraId.values()) {
            cameraPrograms.put(id, new SpiderProgram());
            cameras.put(id, new CameraPose(id, limits));
        }
    }

    public SpiderProgram program(SpiderId id) {
        return programs.get(id);
    }

    public SpiderProgram cameraProgram(CameraId id) {
        return cameraPrograms.get(id);
    }

    public CameraPose camera(CameraId id) {
        return cameras.get(id);
    }

    /** The selected viewpoint; it starts as the zapper. */
    public Viewpoint selected() {
        return selected;
    }

    /** Pipeline step 2: throttle countdown, then every command in order. */
    void applyInput(SimContext ctx, List<Command> commands) {
        countDownThrottles();
        for (Command command : commands) {
            apply(ctx, command);
        }
    }

    /** Pipeline step 3a for a standing, alive spider: one step of a running program. */
    void programStep(SimContext ctx, SimObject spider) {
        WorldState world = ctx.world();
        List<SimEvent> out = ctx.out();
        SpiderProgram program = programs.get(SpiderId.ofSlot(spider.slot()));
        if (program.state() != ProgramState.RUNNING) {
            return;
        }
        Optional<ProgramStep> step = program.next();
        if (step.isEmpty()) {
            return;
        }
        switch (step.get()) {
            case EMPTY -> {
            }
            case FORWARD -> Walk.start(world, spider, spider.facing(), out);
            case BACK -> back(world, spider, out);
            case LEFT -> turn(world, spider, spider.facing().left(), out);
            case RIGHT -> turn(world, spider, spider.facing().right(), out);
            case FIRE -> fire(ctx, spider);
        }
    }

    private void countDownThrottles() {
        if (selectThrottle > 0) {
            selectThrottle--;
        }
        if (turnFireThrottle > 0) {
            turnFireThrottle--;
        }
        if (moveThrottle > 0) {
            moveThrottle--;
        }
        if (programThrottle > 0) {
            programThrottle--;
        }
    }

    private void apply(SimContext ctx, Command command) {
        WorldState world = ctx.world();
        List<SimEvent> out = ctx.out();
        switch (command) {
            case Command.SelectSpider select -> select(world, select.spider(), out);
            case Command.SelectCamera select -> select(world, select.camera(), out);
            case Command.RunAll runAll -> {
                if (selectionMayAct(world) && programThrottle == 0) {
                    endReady(world, out);
                    if (selected instanceof CameraId camera) {
                        cameraPrograms.get(camera).stop();
                    }
                    for (SpiderProgram program : programs.values()) {
                        program.run();
                    }
                    programThrottle = PROGRAM_THROTTLE;
                }
            }
            case Command.Abandon abandon -> {
                // read by Simulation
            }
            case Command.Restart restart -> {
                // read by Simulation
            }
            case Command.SpiderCommand spiderCommand -> {
                SimObject spider = world.spider(spiderCommand.spider());
                if (spider.isAlive()) {
                    spiderCommand(ctx, spider, spiderCommand);
                }
            }
            case Command.CameraCommand cameraCommand -> {
                if (camerasEnabled(world)) {
                    cameraCommand(cameraCommand);
                }
            }
        }
    }

    private void spiderCommand(SimContext ctx, SimObject spider, Command.SpiderCommand command) {
        WorldState world = ctx.world();
        List<SimEvent> out = ctx.out();
        SpiderProgram program = programs.get(command.spider());
        if (program.state() == ProgramState.WRITING) {
            Optional<ProgramStep> written = actionStep(command);
            if (written.isPresent()) {
                write(program, written.get());
                return;
            }
        }
        switch (command) {
            case Command.Forward forward -> {
                if (moveThrottle == 0) {
                    endReady(world, out);
                    moveThrottle = Walk.start(world, spider, spider.facing(), out) ? MOVE_THROTTLE
                            : MOVE_FAILED_THROTTLE;
                }
            }
            case Command.Back back -> {
                if (moveThrottle == 0) {
                    endReady(world, out);
                    moveThrottle = back(world, spider, out) ? MOVE_THROTTLE : MOVE_FAILED_THROTTLE;
                }
            }
            case Command.TurnLeft left -> {
                if (turnFireThrottle == 0) {
                    turn(world, spider, spider.facing().left(), out);
                    turnFireThrottle = TURN_THROTTLE;
                }
            }
            case Command.TurnRight right -> {
                if (turnFireThrottle == 0) {
                    turn(world, spider, spider.facing().right(), out);
                    turnFireThrottle = TURN_THROTTLE;
                }
            }
            case Command.Fire fire -> {
                if (turnFireThrottle == 0) {
                    endReady(world, out);
                    turnFireThrottle = fire(ctx, spider) ? FIRE_HIT_THROTTLE : FIRE_MISS_THROTTLE;
                }
            }
            case Command.EnterWriteMode enter -> program.enterWriteMode();
            case Command.Write write -> {
                if (program.state() == ProgramState.WRITING) {
                    write(program, write.step());
                }
            }
            case Command.SetCursor setCursor -> program.setCursor(setCursor.cursor());
            case Command.Delete delete -> delete(program);
            case Command.Clear clear -> clear(program);
            case Command.Run run -> {
                if (programThrottle == 0) {
                    endReady(world, out);
                    program.run();
                    programThrottle = PROGRAM_THROTTLE;
                }
            }
            case Command.Stop stop -> {
                program.stop();
                programThrottle = PROGRAM_THROTTLE;
            }
        }
    }

    /**
     * A camera command ({@code S:8350-8352}, {@code S:8408-8584}): in write mode the five action buttons write their
     * step; otherwise Forward/Back move the camera up/down and Left/Right sideways, Stop and Run zoom in and out. None
     * of them ends READY. A move or zoom that would leave its range is ignored completely, including its side effects.
     */
    private void cameraCommand(Command.CameraCommand command) {
        SpiderProgram program = cameraPrograms.get(command.camera());
        CameraPose pose = cameras.get(command.camera());
        if (program.state() == ProgramState.WRITING) {
            Optional<ProgramStep> written = cameraActionStep(command);
            if (written.isPresent()) {
                write(program, written.get());
                return;
            }
        }
        switch (command) {
            case Command.CameraForward forward -> {
                if (pose.raise()) {
                    moveThrottle = CAMERA_STEP_THROTTLE;
                }
            }
            case Command.CameraBack back -> {
                if (pose.lower()) {
                    moveThrottle = CAMERA_STEP_THROTTLE;
                }
            }
            case Command.CameraTurnLeft left -> {
                if (pose.shiftLeft()) {
                    turnFireThrottle = CAMERA_STEP_THROTTLE;
                }
            }
            case Command.CameraTurnRight right -> {
                if (pose.shiftRight()) {
                    turnFireThrottle = CAMERA_STEP_THROTTLE;
                }
            }
            case Command.CameraFire fire -> {
                // Fire does nothing on a camera outside write mode
            }
            case Command.CameraEnterWriteMode enter -> program.enterWriteMode();
            case Command.CameraWrite write -> {
                if (program.state() == ProgramState.WRITING) {
                    write(program, write.step());
                }
            }
            case Command.CameraSetCursor setCursor -> program.setCursor(setCursor.cursor());
            case Command.CameraDelete delete -> delete(program);
            case Command.CameraClear clear -> clear(program);
            case Command.CameraZoomIn in -> {
                if (pose.zoomIn()) {
                    zoomed(program);
                }
            }
            case Command.CameraZoomOut zoomOut -> {
                if (pose.zoomOut()) {
                    zoomed(program);
                }
            }
        }
    }

    /** In write mode the five action buttons write their step instead ({@code S:8454}). */
    private static Optional<ProgramStep> actionStep(Command.SpiderCommand command) {
        return switch (command) {
            case Command.Forward forward -> Optional.of(ProgramStep.FORWARD);
            case Command.Back back -> Optional.of(ProgramStep.BACK);
            case Command.TurnLeft left -> Optional.of(ProgramStep.LEFT);
            case Command.TurnRight right -> Optional.of(ProgramStep.RIGHT);
            case Command.Fire fire -> Optional.of(ProgramStep.FIRE);
            default -> Optional.empty();
        };
    }

    /** The same for a camera: the write-mode check comes before the camera split ({@code S:8350-8352}). */
    private static Optional<ProgramStep> cameraActionStep(Command.CameraCommand command) {
        return switch (command) {
            case Command.CameraForward forward -> Optional.of(ProgramStep.FORWARD);
            case Command.CameraBack back -> Optional.of(ProgramStep.BACK);
            case Command.CameraTurnLeft left -> Optional.of(ProgramStep.LEFT);
            case Command.CameraTurnRight right -> Optional.of(ProgramStep.RIGHT);
            case Command.CameraFire fire -> Optional.of(ProgramStep.FIRE);
            default -> Optional.empty();
        };
    }

    private void write(SpiderProgram program, ProgramStep step) {
        if (programThrottle == 0) {
            program.insert(step);
            programThrottle = WRITE_THROTTLE;
        }
    }

    /** Delete ({@code S:8548-8573}): throttled; sets the throttle only if a step was removed. */
    private void delete(SpiderProgram program) {
        if (programThrottle == 0 && program.delete()) {
            programThrottle = PROGRAM_THROTTLE;
        }
    }

    /** Clear ({@code S:8574-8581}): not throttled. */
    private void clear(SpiderProgram program) {
        program.clear();
        programThrottle = PROGRAM_THROTTLE;
    }

    /** After a zoom step: state −1, which leaves write mode, and program throttle 8 ({@code S:8478-8514}). */
    private void zoomed(SpiderProgram program) {
        program.stop();
        programThrottle = PROGRAM_THROTTLE;
    }

    /**
     * Selection ({@code S:8284-8331}). In write mode a program is copied only between a spider and a camera, and the
     * selection stays: a writing spider copies its program to the chosen camera; a writing camera gives its heading
     * and its program to the chosen spider and is cleared. Both leave write mode. Every other selection, including
     * spider to spider in write mode, just changes the selection.
     */
    private void select(WorldState world, Viewpoint target, List<SimEvent> out) {
        if (target == selected || selectThrottle != 0) {
            return;
        }
        SpiderProgram current = programOf(selected);
        boolean writing = current.state() == ProgramState.WRITING;
        if (writing && selected instanceof SpiderId && target instanceof CameraId camera) {
            cameraPrograms.get(camera).copySlotsFrom(current);
            current.stop();
        } else if (writing && selected instanceof CameraId camera && target instanceof SpiderId spiderId) {
            SimObject spider = world.spider(spiderId);
            spider.setFacing(camera.heading());
            if (spider.isAlive()) {
                out.add(new SimEvent.Turned(world.frame(), spider.slot(), camera.heading()));
            }
            programs.get(spiderId).copySlotsFrom(current);
            current.clear();
        } else {
            selected = target;
        }
        selectThrottle = SELECT_THROTTLE;
    }

    private SpiderProgram programOf(Viewpoint viewpoint) {
        return switch (viewpoint) {
            case SpiderId spider -> programs.get(spider);
            case CameraId camera -> cameraPrograms.get(camera);
        };
    }

    /** Buttons from 7 on need an alive selected spider, or a tower with cameras for a camera ({@code S:8340-8346}). */
    private boolean selectionMayAct(WorldState world) {
        return switch (selected) {
            case SpiderId spider -> world.spider(spider).isAlive();
            case CameraId camera -> camerasEnabled(world);
        };
    }

    private static boolean camerasEnabled(WorldState world) {
        return world.tower().behaviour().camerasEnabled();
    }

    private static void endReady(WorldState world, List<SimEvent> out) {
        if (world.isReady()) {
            world.endReady();
            out.add(new SimEvent.ReadyEnded(world.frame()));
        }
    }

    /**
     * {@code L00A5DC} ({@code S:8616-8638}): reverse while moving, else lift ride, else backwards walk (option). A
     * pushed spider cannot reverse ({@code L00A70A} refuses +0E 4..7, {@code S:8697-8698}).
     */
    private static boolean back(WorldState world, SimObject spider, List<SimEvent> out) {
        if (spider.isMoving()) {
            return switch (spider.moveKind()) {
                case WALK -> Walk.reverse(world, spider, out);
                case LIFT_UP, LIFT_DOWN -> LiftRide.reverse(world, spider, out);
                case PUSH, IDLE, NONE -> false;
            };
        }
        if (world.isLift(spider.cell())) {
            return LiftRide.start(world, spider, out);
        }
        if (world.options().backwardsAllowed()) {
            return Walk.start(world, spider, spider.facing().opposite(), out);
        }
        return false;
    }

    /** Turns at once, also while walking; the walk keeps its direction ({@code S:8722-8729}). */
    private static void turn(WorldState world, SimObject spider, Direction facing, List<SimEvent> out) {
        spider.setFacing(facing);
        out.add(new SimEvent.Turned(world.frame(), spider.slot(), facing));
    }

    /**
     * Fires the spider's ray from its current cell in its facing ({@code L00A796}, {@code S:8730}), also while it
     * walks or is pushed. True if the ray hit (8-frame throttle), false for a miss (1 frame; {@code S:8397-8407}).
     */
    private static boolean fire(SimContext ctx, SimObject spider) {
        ctx.emit(new SimEvent.SpiderFired(ctx.frame(), spider.slot(), spider.cell(), spider.facing()));
        return Ray.fire(ctx, spider, spider.facing(), RayType.of(SpiderId.ofSlot(spider.slot())));
    }
}
