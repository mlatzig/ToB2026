package de.latzig.game.tob.client.logic.editor;

import java.util.Optional;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.frontend.DoubleClick;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerExtent;

/**
 * The view of the Tower Designer's 3D live preview (Phase 6b spec decision 3): the eye circles the tower and looks at
 * it from the nearest distance that frames the whole tower. Dragging with the left button turns the tower around its
 * vertical axis (horizontally) and tilts the view (vertically: down raises the eye, author 2026-10-15),
 * {@value #RADIANS_PER_PIXEL} rad per pixel; a right click resets the view (from the north,
 * {@value #RESET_PITCH_DEGREES}° from above, centred at the height of level 2's middle); a double click
 * ({@link DoubleClick}'s time and distance) toggles between the small area and the full screen. The eye stays
 * between the horizon ({@value #MIN_PITCH_DEGREES}°) and almost straight above ({@value #MAX_PITCH_DEGREES}°). Scene
 * axes as {@link Pose}: x east, y up, z south (north is −z).
 */
public final class PreviewCamera {

    /** The eye's view of the reset: {@value}° above the horizontal plane through the target. */
    public static final float RESET_PITCH_DEGREES = 20f;
    public static final float MIN_PITCH_DEGREES = 0f;
    public static final float MAX_PITCH_DEGREES = 85f;
    public static final float RADIANS_PER_PIXEL = 0.01f;
    /** The height the view centres on: the middle of level 2 (levels are 1.0 high, level 1 starts at 0). */
    public static final float TARGET_HEIGHT = 1.5f;
    /** The vertical field of view in radians (the game's, {@code CameraController.FIELD_OF_VIEW_DEGREES}). */
    public static final float FIELD_OF_VIEW = 1.2f;
    /** The distance is the nearest that frames the tower plus this many percent. */
    public static final int MARGIN_PERCENT = 5;
    private static final int RIM_POINTS = 32;
    private static final int FIT_STEPS = 40;
    /** The tower framed when the grid is empty: the original 8 × 8 × 4 area. */
    static final TowerExtent EMPTY = new TowerExtent(TowerExtent.ORIGINAL_MIN, TowerExtent.ORIGINAL_MIN,
            TowerExtent.ORIGINAL_MAX, TowerExtent.ORIGINAL_MAX, TowerExtent.ORIGINAL_LEVELS - 1);

    /** Where the eye stands and what it looks at. */
    public record View(Pose eye, Pose target) {
    }

    private float yaw;
    private float pitch = (float) Math.toRadians(RESET_PITCH_DEGREES);
    private boolean maximized;
    private boolean grabbed;
    private boolean known;
    private float lastX;
    private float lastY;
    private double pressSeconds = Double.NEGATIVE_INFINITY;
    private float pressX;
    private float pressY;

    /** Radians the eye stands clockwise from north around the tower (0: north). */
    public float yaw() {
        return yaw;
    }

    /** Radians the eye looks down at the target (0: level). */
    public float pitch() {
        return pitch;
    }

    /** Whether the preview fills the screen. */
    public boolean maximized() {
        return maximized;
    }

    /** Whether the left button holds the view (dragging turns and tilts it). */
    public boolean grabbed() {
        return grabbed;
    }

    /** The right click: from the north, slightly from above. */
    public void reset() {
        yaw = 0f;
        pitch = (float) Math.toRadians(RESET_PITCH_DEGREES);
    }

    /** The double click: the small area or the full screen. */
    public void toggle() {
        maximized = !maximized;
        grabbed = false;
    }

    /** Back to the small area (Esc on the full screen). */
    public void restore() {
        maximized = false;
        grabbed = false;
    }

    /** The mouse is at ({@code x}, {@code y}) (GUI pixels, y up); while grabbed the move turns and tilts the view. */
    public void mouse(float x, float y) {
        if (grabbed && known) {
            yaw = (float) ((yaw + (x - lastX) * RADIANS_PER_PIXEL) % (2 * Math.PI));
            pitch = Math.clamp(pitch - (y - lastY) * RADIANS_PER_PIXEL, (float) Math.toRadians(MIN_PITCH_DEGREES),
                    (float) Math.toRadians(MAX_PITCH_DEGREES));
        }
        lastX = x;
        lastY = y;
        known = true;
    }

    /**
     * The left button went down on the preview at {@code seconds} (any monotonic clock), where the mouse was last:
     * a second press within {@link DoubleClick#INTERVAL} and {@link DoubleClick#DISTANCE} toggles the size,
     * otherwise the view is grabbed.
     */
    public void press(double seconds) {
        if (seconds - pressSeconds <= DoubleClick.INTERVAL && Math.abs(lastX - pressX) <= DoubleClick.DISTANCE
                && Math.abs(lastY - pressY) <= DoubleClick.DISTANCE) {
            pressSeconds = Double.NEGATIVE_INFINITY;
            toggle();
            return;
        }
        pressSeconds = seconds;
        pressX = lastX;
        pressY = lastY;
        grabbed = true;
    }

    /** The left button went up. */
    public void release() {
        grabbed = false;
    }

    /**
     * The eye and the target for {@code tower} in a view of {@code aspect} (width / height): the target is the middle
     * of the tower's footprint at {@link #TARGET_HEIGHT}; the eye stands at the nearest distance (plus
     * {@value #MARGIN_PERCENT} %) from which the upright cylinder around the tower's box — the footprint's
     * circumcircle, from the ground to the top level's ceiling — fits the field of view. The cylinder does not change
     * as the tower turns, so turning never changes the distance; tilting does, a little.
     */
    public View view(TowerDefinition tower, float aspect) {
        TowerExtent box = Optional.ofNullable(tower).flatMap(t -> TowerExtent.of(t.grid())).orElse(EMPTY);
        float cx = (box.minX() + box.maxX()) / 2f;
        float cz = (box.minY() + box.maxY()) / 2f;
        double rx = (box.maxX() - box.minX() + 1) / 2.0;
        double rz = (box.maxY() - box.minY() + 1) / 2.0;
        double radius = Math.sqrt(rx * rx + rz * rz);
        double distance = fit(radius, box.topLevel() + 1, aspect) * (1 + MARGIN_PERCENT / 100.0);
        double flat = distance * Math.cos(pitch);
        Pose target = new Pose(cx, TARGET_HEIGHT, cz);
        Pose eye = new Pose((float) (cx + flat * Math.sin(yaw)), (float) (TARGET_HEIGHT + distance * Math.sin(pitch)),
                (float) (cz - flat * Math.cos(yaw)));
        return new View(eye, target);
    }

    /**
     * The nearest distance at the current pitch (looking north, the cylinder is round) from which the cylinder of
     * {@code radius} around the target, from 0 to {@code top}, lies inside the field of view (bisection).
     */
    private double fit(double radius, double top, float aspect) {
        double tanV = Math.tan(FIELD_OF_VIEW / 2.0);
        double tanH = tanV * aspect;
        double cos = Math.cos(pitch);
        double sin = Math.sin(pitch);
        double near = 0.0;
        double far = 1.0;
        while (!fits(radius, top, far, tanH, tanV, cos, sin)) {
            far *= 2.0;
        }
        for (int i = 0; i < FIT_STEPS; i++) {
            double middle = (near + far) / 2.0;
            if (fits(radius, top, middle, tanH, tanV, cos, sin)) {
                far = middle;
            } else {
                near = middle;
            }
        }
        return far;
    }

    /** Whether the cylinder's rims (sampled) lie in front of the eye at {@code distance} and inside the view. */
    private static boolean fits(double radius, double top, double distance, double tanH, double tanV, double cos,
            double sin) {
        for (double height : new double[] {0.0, top}) {
            for (int i = 0; i < RIM_POINTS; i++) {
                double angle = 2.0 * Math.PI * i / RIM_POINTS;
                double x = radius * Math.cos(angle);
                double y = height - TARGET_HEIGHT;
                double z = radius * Math.sin(angle);
                // the eye looks along +z (south), from z = -distance cos, y = distance sin, tilted down by the pitch
                double ez = z + distance * cos;
                double ey = y - distance * sin;
                double depth = ez * cos - ey * sin;
                double up = ey * cos + ez * sin;
                if (depth <= 0.0 || Math.abs(x) > tanH * depth || Math.abs(up) > tanV * depth) {
                    return false;
                }
            }
        }
        return true;
    }
}
