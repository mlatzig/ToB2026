package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The headless simulation: one {@link #step(List)} is one original frame (play loop {@code L008C2A},
 * {@code S:6886-6966}).
 * <p>
 * READY frame (world frozen, {@code S:6901-6916}): clock phase −1, input (including Abandon/Restart), spider
 * movement, handlers; no frame counter, no time limit; the world thaws after the frame in which an action ended
 * READY. Normal frame ({@code S:6917-6949}): frame counter +1; clock phase −1 and every 7th frame one time-limit
 * second; then input, which is where Abandon/Restart are read ({@code L00A130}, {@code S:8333-8339}) — so a pending
 * exit set by the time-limit tick in this same frame refuses them, as in the original; movement (spiders in slot
 * order with their program steps, then — unless frozen — every other moving object in list order); handlers in list
 * order; countdown of a pending exit.
 * <p>
 * An accepted Abandon or Restart (the pause button, {@code W001B9E} = 0) does not cut the frame short: input, movement
 * and handlers run, and the play loop leaves right after the handlers ({@code S:6912}, {@code S:6944}), before the
 * thaw and the exit countdown. Then the attempt ends with exit 5 or 6 (Phase 2 carry-over 10).
 */
public final class Simulation {

    private final WorldState world;
    private final SimContext ctx;
    private final SpiderController controller;

    private Simulation(WorldState world) {
        this.world = world;
        this.ctx = new SimContext(world);
        this.controller = new SpiderController(CameraLimits.of(world.tower().grid()));
    }

    /**
     * @throws IllegalArgumentException if the tower cannot be loaded (unpaired lift start, spider placed twice)
     */
    public static Simulation start(TowerDefinition tower, SimOptions options) {
        return new Simulation(WorldState.load(tower, options));
    }

    /** Advances one frame with the commands of this frame and returns its events. */
    public List<SimEvent> step(List<Command> commands) {
        if (world.isFinished()) {
            throw new IllegalStateException("the simulation has finished with exit " + world.exitCode());
        }
        List<SimEvent> out = new ArrayList<>();
        ctx.begin(out);
        boolean readyFrame = world.isReady();
        if (readyFrame) {
            world.countClock();
        } else {
            world.advanceFrame();
            if (world.countClock()) {
                tickTimeLimit(out);
            }
        }
        int exit = acceptedExit(commands);
        controller.applyInput(ctx, commands);
        moveObjects(out);
        runHandlers(out);
        if (exit != 0) {
            world.finish(exit);
            out.add(new SimEvent.Exited(world.frame(), exit));
        } else if (readyFrame) {
            if (!world.isReady()) {
                world.thaw();
            }
        } else if (world.countDownExit()) {
            out.add(new SimEvent.Exited(world.frame(), world.exitCode()));
        }
        return List.copyOf(out);
    }

    /**
     * Abandon/Restart, read at input time ({@code L00A130}, {@code S:8333-8339}): accepted unless an exit is already
     * pending, in which case the pause is refused and the pending exit runs its course.
     *
     * @return the exit code that ends the attempt after this frame, or 0
     */
    private int acceptedExit(List<Command> commands) {
        if (world.isExitPending()) {
            return 0;
        }
        for (Command command : commands) {
            if (command instanceof Command.Abandon) {
                return Rules.EXIT_ABANDONED;
            }
            if (command instanceof Command.Restart) {
                return Rules.EXIT_RESTART;
            }
        }
        return 0;
    }

    public WorldState world() {
        return world;
    }

    public SpiderProgram program(SpiderId id) {
        return controller.program(id);
    }

    public SpiderProgram cameraProgram(CameraId id) {
        return controller.cameraProgram(id);
    }

    /** The pose of an external camera; read-only for callers outside {@code core.sim}. */
    public CameraPose camera(CameraId id) {
        return controller.camera(id);
    }

    public Viewpoint selected() {
        return controller.selected();
    }

    public int frame() {
        return world.frame();
    }

    public boolean isReady() {
        return world.isReady();
    }

    public boolean isFinished() {
        return world.isFinished();
    }

    /** Code of the pending or final exit; 0 = none. */
    public int exitCode() {
        return world.exitCode();
    }

    /** Counters API for tests in {@code core.sim}: a grab collected a klondike. Events use the current frame. */
    List<SimEvent> addKlondike() {
        List<SimEvent> out = new ArrayList<>();
        Rules.addKlondike(world, out);
        return List.copyOf(out);
    }

    /** Counters API for tests in {@code core.sim}: a zap or an exchanger destroyed a robot. */
    List<SimEvent> addRobot() {
        List<SimEvent> out = new ArrayList<>();
        Rules.addRobot(world, out);
        return List.copyOf(out);
    }

    /** Destruction API for tests in {@code core.sim} (see {@link Rules#destroy}). */
    List<SimEvent> destroy(int slot) {
        List<SimEvent> out = new ArrayList<>();
        Rules.destroy(world, world.object(slot), out);
        return List.copyOf(out);
    }

    /** One time-limit second ({@code S:6918-6936}); reaching 0 sets exit 2 with delay 21 + 6 = 27. */
    private void tickTimeLimit(List<SimEvent> out) {
        if (world.timeLeft() == 0 || world.isExitPending()) {
            return;
        }
        world.setTimeLeft(world.timeLeft() - 1);
        out.add(new SimEvent.TimeTick(world.frame(), world.timeLeft()));
        if (world.timeLeft() == 0) {
            Rules.setExit(world, Rules.EXIT_TIMEOUT, out);
        }
    }

    /** {@code L00ADAC} ({@code S:9140-9167}). */
    private void moveObjects(List<SimEvent> out) {
        for (int slot = 0; slot < WorldState.SPIDER_SLOTS; slot++) {
            SimObject spider = world.object(slot);
            if (!spider.isAlive()) {
                continue;
            }
            if (!spider.isMoving()) {
                controller.programStep(ctx, spider);
            }
            if (spider.isMoving()) {
                advance(spider, out);
            }
        }
        if (world.isFrozen()) {
            return;
        }
        for (int slot = WorldState.SPIDER_SLOTS; slot < world.objects().size(); slot++) {
            SimObject object = world.object(slot);
            if (object.isAlive() && object.isMoving()) {
                advance(object, out);
            }
        }
        // step 3c: forcer-driven lifts (L00AE16, S:9169-9214), inside the unfrozen branch as in the original
        world.forcerLifts().advance(world, out);
    }

    private void advance(SimObject object, List<SimEvent> out) {
        switch (object.moveKind()) {
            case WALK -> Walk.advance(world, object, out);
            case PUSH -> Push.advance(world, object, out);
            case LIFT_UP, LIFT_DOWN -> LiftRide.advance(world, object, out);
            case IDLE -> object.setMoveCounter(object.moveCounter() - 1);
            case NONE -> throw new IllegalStateException("moving without a move kind: slot " + object.slot());
        }
    }

    /** {@code L00B016} ({@code S:9257-9272}). */
    private void runHandlers(List<SimEvent> out) {
        for (SimObject object : world.objects()) {
            switch (object.status()) {
                case DESTROYING -> Rules.tickDestroyed(world, object, out);
                case ALIVE -> ctx.behaviour(object).act(ctx, object);
                case REMOVED, ABSENT -> {
                }
            }
        }
    }
}
