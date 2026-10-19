package de.latzig.game.tob.client.logic.frontend;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * The menu background's camera (Phase 5a spec decision 13): ToB 2009's slow orbit around the demo tower
 * ({@code AbstractStartGamePart.doOutput}): the eye circles the tower's midpoint at distance 230 (ToB 2009 units, 32
 * per field) at 0.1 rad/s and looks at the midpoint, which lies 10 units above the top level of the tower's bounding
 * box of base elements ({@code SourceTower.calculateMaximalRange}/{@code calculateMaximalLevels}, integer middle).
 * ToB 2009's eye height (4 ToB 2009 units above the ground) is too low to show what happens on a demo tower's upper
 * levels, so the author decided (2026-10-13, Task 17) to deviate from it: the eye instead sits at a fixed elevation
 * angle above the target ({@link #ELEVATION_DEGREES}), so its height is {@code targetY + DISTANCE *
 * tan(ELEVATION_DEGREES)} and rises automatically with a tall tower's top level. Converted to ToB 2026 world units
 * (one field = 1, a field's centre at its index, level floors at their level): a ToB 2009 corner {@code (x, y)} is
 * {@code (x − 0.5, y + 0.5)}, ToB 2009 height {@code h} is {@code (h − 1) / 32}, and ToB 2009's {@code +z} is ToB
 * 2026's {@code −z}.
 * <p>
 * Phase 6a spec decision 4: should a tower wider than 8 fields ever run here, the orbit's radius grows with its
 * longer side ({@link #DISTANCE} × side / 8), so it is framed like an original; every tower up to 8 fields wide (all
 * demo towers) keeps {@link #DISTANCE}.
 *
 * @param targetX the midpoint the eye looks at
 * @param distance the orbit's radius (fields)
 */
public record OrbitCamera(float targetX, float targetY, float targetZ, float distance) {

    public static final float DISTANCE = 230f / 32f;
    /** The eye's elevation angle above the target's horizontal plane (the author's decision, 2026-10-13). */
    public static final float ELEVATION_DEGREES = 25f;
    public static final float TARGET_ABOVE_TOP = 10f / 32f;
    /** Radians per second. */
    public static final float SPEED = 0.1f;
    /** The longest side (fields) that keeps {@link #DISTANCE}: the original 8 × 8 area. */
    public static final int ORIGINAL_SIDE = 8;

    /** An orbit at the original {@link #DISTANCE}. */
    public OrbitCamera(float targetX, float targetY, float targetZ) {
        this(targetX, targetY, targetZ, DISTANCE);
    }

    /** The orbit around {@code tower}'s base elements; an empty tower orbits the grid centre. */
    public static OrbitCamera of(TowerDefinition tower) {
        TowerGrid grid = tower.grid();
        int xMin = TowerGrid.WIDTH;
        int yMin = TowerGrid.DEPTH;
        int xMax = -1;
        int yMax = -1;
        int top = 0;
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    if (grid.cell(x, y, level).base() != BaseType.EMPTY) {
                        xMin = Math.min(xMin, x);
                        yMin = Math.min(yMin, y);
                        xMax = Math.max(xMax, x);
                        yMax = Math.max(yMax, y);
                        top = level;
                    }
                }
            }
        }
        if (xMax < 0) {
            return new OrbitCamera(CameraPlacement.GRID_CENTRE, TARGET_ABOVE_TOP, CameraPlacement.GRID_CENTRE);
        }
        int midX = (xMin + xMax) / 2;
        int midY = (yMin + yMax) / 2;
        int side = Math.max(xMax - xMin + 1, yMax - yMin + 1);
        float distance = side <= ORIGINAL_SIDE ? DISTANCE : DISTANCE * side / ORIGINAL_SIDE;
        return new OrbitCamera(midX - 0.5f, top + TARGET_ABOVE_TOP, midY + 0.5f, distance);
    }

    /**
     * The eye {@code seconds} after the orbit started ({@code x = D·sin(−0.1 t)}, {@code z = D·cos(0.1 t)}, D the
     * {@link #distance}); its height looks down on {@link #target()} at {@link #ELEVATION_DEGREES}.
     */
    public Pose eye(double seconds) {
        double angle = SPEED * seconds;
        float eyeHeight = targetY + distance * (float) Math.tan(Math.toRadians(ELEVATION_DEGREES));
        return new Pose(targetX + (float) (distance * Math.sin(-angle)), eyeHeight,
                targetZ - (float) (distance * Math.cos(angle)));
    }

    public Pose target() {
        return new Pose(targetX, targetY, targetZ);
    }
}
