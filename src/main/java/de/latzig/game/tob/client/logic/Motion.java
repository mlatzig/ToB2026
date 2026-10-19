package de.latzig.game.tob.client.logic;

import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.LiftRide;
import de.latzig.game.tob.core.sim.Push;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Walk;

/**
 * Interpolated object positions (spec §4.1). The state view holds the position after the last frame in world units
 * (512 per field); the render position adds the fraction {@code alpha} of the movement the next frame makes: a walk
 * 16 units per frame ({@code (32 − counter + alpha) / 32} of the step), a push the push ease table, a lift ride the
 * lift ease table. Objects other than spiders do not move while the world is frozen.
 */
public final class Motion {

    public static final float UNITS_PER_FIELD = SimObject.UNITS_PER_CELL;
    public static final float HALF_PI = (float) (Math.PI / 2);

    private Motion() {
    }

    /** The render position of {@code object} at {@code alpha} of the next frame; {@code frozen} = world frozen. */
    public static Pose position(SimObject object, boolean frozen, float alpha) {
        return position(object, frozen, alpha, MotionCarry.NONE);
    }

    /**
     * The render position with the driver's {@link MotionCarry}: state + {@code alpha} of the next frame +
     * {@code (1 − alpha)} of the carry, so a move changed before its advance (a reversal) is drawn without a jump.
     * Mesh, lift platform, spider camera and spider pick all go through here.
     */
    public static Pose position(SimObject object, boolean frozen, float alpha, MotionCarry carry) {
        int[] next = nextFrameUnits(object, frozen, carry);
        int slot = object.slot();
        float keep = 1f - alpha;
        return new Pose((object.worldX() + next[0] * alpha + carry.x(slot) * keep) / UNITS_PER_FIELD,
                (object.worldY() + next[1] * alpha + carry.y(slot) * keep) / UNITS_PER_FIELD,
                (object.worldZ() + next[2] * alpha + carry.z(slot) * keep) / UNITS_PER_FIELD);
    }

    /** World units (x, y, z) the next frame moves the object, as {@code Walk}, {@code Push} and {@code LiftRide} do. */
    static int[] nextFrameUnits(SimObject object, boolean frozen) {
        return nextFrameUnits(object, frozen, MotionCarry.NONE);
    }

    /**
     * As {@link #nextFrameUnits(SimObject, boolean)}; a standing spider with a walk predicted by {@code carry}'s
     * {@link WalkLookAhead} moves the walk's 16 units (Task 16).
     */
    static int[] nextFrameUnits(SimObject object, boolean frozen, MotionCarry carry) {
        if (!object.isMoving()) {
            Direction walk = object.isSpider() ? carry.walk(object.slot()) : null;
            return walk == null ? new int[3]
                    : new int[] {walk.dx() * Walk.UNITS_PER_FRAME, 0, walk.dy() * Walk.UNITS_PER_FRAME};
        }
        if (frozen && !object.isSpider()) {
            return new int[3];
        }
        Direction direction = object.moveDirection();
        int counter = object.moveCounter();
        return switch (object.moveKind()) {
            case WALK -> new int[] {direction.dx() * Walk.UNITS_PER_FRAME, 0, direction.dy() * Walk.UNITS_PER_FRAME};
            case PUSH -> new int[] {direction.dx() * Push.ease(counter), 0, direction.dy() * Push.ease(counter)};
            case LIFT_UP -> new int[] {0, LiftRide.ease(counter), 0};
            case LIFT_DOWN -> new int[] {0, -LiftRide.ease(counter), 0};
            case IDLE, NONE -> new int[3];
        };
    }

    /**
     * Heading of a direction in radians, clockwise seen from above: north 0, east π/2, south π, west 3π/2. A mesh is
     * turned by {@code −heading} about the up axis (meshes face north, −z).
     */
    public static float heading(Direction direction) {
        return direction.ordinal() * HALF_PI;
    }
}
