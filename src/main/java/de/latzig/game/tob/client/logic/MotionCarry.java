package de.latzig.game.tob.client.logic;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Acceptance fix "reversal interpolation": {@link Motion#position} extrapolates the next frame from the state, but a
 * step can change a move before that move's advance (Back reverses a lift ride or a walk, a zap fired in the input
 * phase reverses a worm). The object was then drawn one frame on in the old direction and snapped back by
 * ease(c) + ease(c + 1) units at the step boundary. The simulation is right (the original's behaviour); only the
 * drawing changes: before each step the driver stores every object's predicted pose (state + next frame, what was
 * drawn at alpha → 1), after the step the carry is predicted − state, and {@link Motion#position} adds
 * {@code carry · (1 − alpha)}. The drawn path is continuous and nothing is drawn a frame late.
 * <p>
 * The carry is exactly 0 for every object whose move went on as predicted. It is only kept for an object that was
 * alive and moving (as {@link Motion} predicts it) before the step, or is a spider (Task 16 fix B), and is alive
 * after it, and only up to {@value #MAX_UNITS} units per axis: a move of another object that starts from standing
 * keeps its old look, and a swap or any other cell-sized jump (at least 512 units) is shown at once, not glided.
 * <p>
 * Task 16 (walk stutter): the carry also holds the {@link WalkLookAhead} of the next frame, the walk a standing spider
 * is predicted to start ({@link #walk}). {@link Motion} and {@link de.latzig.game.tob.client.logic.game.AnimationState}
 * draw a predicted walk as if it had started, so a spider walking on over several cells moves uniformly; a predicted
 * walk counts as moving here, so a wrong guess (the key released at the boundary) is glided back by its 16 units at
 * most. The driver predicts in every render frame that ran a step; a prediction is kept until the next step, so a
 * release between two steps never jumps, and a press between two steps is not predicted (no jump at the press): fix B
 * glides its start.
 */
public final class MotionCarry {

    /** Largest carry per axis in world units (a quarter field); a reversal carries at most 36. */
    public static final int MAX_UNITS = 128;

    /** Never carries anything (tests and callers without a driver). */
    public static final MotionCarry NONE = new MotionCarry(true);

    private final boolean fixed;
    /** Per slot x, y, z: the predicted pose between {@link #beforeStep} and {@link #afterStep}, then the carry. */
    private int[] predicted = new int[0];
    private boolean[] carried = new boolean[0];
    private int[] carry = new int[0];
    /** Per spider slot the predicted walk of the next frame (null = none); cleared by every step. */
    private final Direction[] walks = new Direction[WorldState.SPIDER_SLOTS];

    public MotionCarry() {
        this(false);
    }

    private MotionCarry(boolean fixed) {
        this.fixed = fixed;
    }

    /** Stores every object's predicted pose; called right before the simulation step. */
    public void beforeStep(WorldState world) {
        checkMutable();
        List<SimObject> objects = world.objects();
        if (predicted.length != objects.size() * 3) {
            predicted = new int[objects.size() * 3];
            carried = new boolean[objects.size()];
            carry = new int[objects.size() * 3];
        }
        boolean frozen = world.isFrozen();
        for (SimObject object : objects) {
            int slot = object.slot();
            int[] next = Motion.nextFrameUnits(object, frozen, this);
            // Task 16 fix B: a spider is always carried, so a walk it starts from standing without a prediction
            // glides (one frame at double speed) instead of snapping; a spider that stays put carries 0
            boolean moving = object.isMoving() || object.isSpider();
            carried[slot] = object.isAlive() && moving && !(frozen && !object.isSpider());
            predicted[slot * 3] = object.worldX() + next[0];
            predicted[slot * 3 + 1] = object.worldY() + next[1];
            predicted[slot * 3 + 2] = object.worldZ() + next[2];
        }
    }

    /** Sets every object's carry to its prediction error; called right after the simulation step. */
    public void afterStep(WorldState world) {
        checkMutable();
        Arrays.fill(walks, null);
        Arrays.fill(carry, 0);
        for (SimObject object : world.objects()) {
            int slot = object.slot();
            if (slot >= carried.length || !carried[slot] || !object.isAlive()) {
                continue;
            }
            int dx = predicted[slot * 3] - object.worldX();
            int dy = predicted[slot * 3 + 1] - object.worldY();
            int dz = predicted[slot * 3 + 2] - object.worldZ();
            if (Math.abs(dx) <= MAX_UNITS && Math.abs(dy) <= MAX_UNITS && Math.abs(dz) <= MAX_UNITS) {
                carry[slot * 3] = dx;
                carry[slot * 3 + 1] = dy;
                carry[slot * 3 + 2] = dz;
            }
        }
    }

    /**
     * Adds the {@link WalkLookAhead} of the next frame from the command source's {@code peeked} command; called after
     * the steps of a render frame. A walk already predicted since the last step is kept (no jump at a release).
     */
    public void lookAhead(Simulation simulation, Optional<Command> peeked) {
        checkMutable();
        Direction[] guesses = WalkLookAhead.predict(simulation, peeked);
        for (int slot = 0; slot < walks.length; slot++) {
            if (walks[slot] == null) {
                walks[slot] = guesses[slot];
            }
        }
    }

    /** The walk spider {@code slot} is predicted to start in the next frame, or null (also for other slots). */
    public Direction walk(int slot) {
        return slot >= 0 && slot < walks.length ? walks[slot] : null;
    }

    /** The carry of {@code slot} in world units, x axis (0 for an unknown slot). */
    public int x(int slot) {
        return axis(slot, 0);
    }

    /** The carry of {@code slot} in world units, y axis (0 for an unknown slot). */
    public int y(int slot) {
        return axis(slot, 1);
    }

    /** The carry of {@code slot} in world units, z axis (0 for an unknown slot). */
    public int z(int slot) {
        return axis(slot, 2);
    }

    private int axis(int slot, int axis) {
        int index = slot * 3 + axis;
        return slot >= 0 && index < carry.length ? carry[index] : 0;
    }

    private void checkMutable() {
        if (fixed) {
            throw new UnsupportedOperationException("MotionCarry.NONE never carries");
        }
    }
}
