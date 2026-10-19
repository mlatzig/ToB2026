package de.latzig.game.tob.client.logic.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.Simulation;

/**
 * The frame-locked simulation driver: a fixed-step accumulator at the original rate of 50/7 ≈ 7.14 frames per second
 * (one step = one original frame, 7 VBLs). Each step takes at most one command from the {@link CommandSource} (Phase
 * 2 carry-over 5) and hands the frame's events to the {@link StepListener}. Rendering interpolates with
 * {@link #alpha()}, the fraction of the next frame that has elapsed, always in [0, 1).
 */
public final class SimDriver {

    public static final double FRAMES_PER_SECOND = 50.0 / 7.0;
    public static final double FRAME_SECONDS = 7.0 / 50.0;
    /** At most this many steps per update; a longer stall drops the backlog instead of fast-forwarding. */
    public static final int MAX_STEPS_PER_UPDATE = 4;

    /** Receives the events of every step; {@code tick} counts the steps of this driver from 1. */
    @FunctionalInterface
    public interface StepListener {
        void onStep(List<SimEvent> events, int tick);
    }

    private final Simulation simulation;
    private final CommandSource source;
    private final StepListener listener;
    private final MotionCarry carry = new MotionCarry();
    private double accumulator;
    private int ticks;
    private boolean paused;

    public SimDriver(Simulation simulation, CommandSource source, StepListener listener) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.source = Objects.requireNonNull(source, "source");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Advances by {@code seconds} of wall-clock time and runs every frame that became due, at most
     * {@link #MAX_STEPS_PER_UPDATE}. Nothing happens while paused or after the attempt has finished.
     *
     * @return the number of steps run
     * @throws SimFailure if a step throws; the driver is unusable afterwards
     */
    public int update(double seconds) {
        if (paused || simulation.isFinished()) {
            return 0;
        }
        accumulator += Math.max(0.0, seconds);
        int steps = 0;
        while (accumulator >= FRAME_SECONDS && steps < MAX_STEPS_PER_UPDATE && !simulation.isFinished()) {
            accumulator -= FRAME_SECONDS;
            step();
            steps++;
        }
        if (accumulator >= FRAME_SECONDS || simulation.isFinished()) {
            accumulator = simulation.isFinished() ? 0.0 : accumulator % FRAME_SECONDS;
        }
        if (steps > 0 && !simulation.isFinished()) {
            // Task 16: right after the steps, the walk a standing spider will start next (drawing only); not between
            // steps, where a new prediction would jump the drawing at once (a fresh press glides by fix B instead)
            carry.lookAhead(simulation, source.peek(simulation));
        }
        return steps;
    }

    private void step() {
        String towerId = simulation.world().tower().id();
        int frame = simulation.frame();
        List<SimEvent> events;
        try {
            Optional<Command> command = source.next(simulation);
            carry.beforeStep(simulation.world());
            events = simulation.step(command.map(List::of).orElse(List.of()));
            carry.afterStep(simulation.world());
        } catch (RuntimeException e) {
            throw new SimFailure(towerId, frame, e);
        }
        ticks++;
        listener.onStep(events, ticks);
    }

    /** Elapsed fraction of the next frame, in [0, 1); 0 after the attempt has finished. */
    public float alpha() {
        float alpha = (float) (accumulator / FRAME_SECONDS);
        return alpha >= 1f ? Math.nextDown(1f) : Math.max(0f, alpha);
    }

    /** Steps run so far. */
    public int ticks() {
        return ticks;
    }

    public boolean isPaused() {
        return paused;
    }

    /** Pausing holds the simulation and the interpolation fraction (developer key). */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Simulation simulation() {
        return simulation;
    }

    /** The prediction error of the last step, drawn over the next frame ({@link Motion#position}). */
    public MotionCarry carry() {
        return carry;
    }
}
