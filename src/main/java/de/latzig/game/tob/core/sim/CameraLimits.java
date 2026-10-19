package de.latzig.game.tob.core.sim;

import de.latzig.game.tob.core.model.TowerExtent;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Where each external camera starts and how far "In" may bring it (Phase 6a spec decision 2), from how far the tower
 * reaches from the grid centre (7.5, 7.5) towards the camera's eye, in original units (512 per cell). The originals
 * fill at most cells 4..11, 4 cells (2048 units) each way: the start distance −3136 lies 1088 units (2.125 cells) and
 * the "In" limit −2368 320 units (0.625 cell) outside that edge ({@link CameraPose#START_DISTANCE},
 * {@link CameraPose#NEAREST}). A tower beyond the 8 × 8 area keeps both margins from its own edge on each camera's
 * side, never closer than the originals' values; a tower within the 8 × 8 area keeps the original values exactly
 * ({@link #ORIGINAL}). The "Out" limit {@link CameraPose#FARTHEST} stays for every tower.
 * <p>
 * The reach of camera {@code c} is measured on the side its eye stands: camera NORTH stands south of the tower (its
 * edge is the south-most row, {@code maxY}), EAST west of it ({@code minX}), SOUTH north of it ({@code minY}), WEST
 * east of it ({@code maxX}).
 * <p>
 * Acceptance change (Task 9): the sideways offset limit ({@link #sidewaysLimit}) follows the tower's reach along the
 * camera's sideways axis (both directions, the larger side), with the originals' margin: a tower within the 8 × 8
 * area keeps {@link CameraPose#OFFSET_LIMIT} exactly. NORTH and SOUTH move sideways along X (their sideways axis is
 * the tower's east/west reach); EAST and WEST move sideways along Y (the tower's north/south reach).
 *
 * @param north the reach towards camera NORTH's eye (units)
 * @param east the reach towards camera EAST's eye
 * @param south the reach towards camera SOUTH's eye
 * @param west the reach towards camera WEST's eye
 */
public record CameraLimits(int north, int east, int south, int west) {

    /** The original area's half size: 4 cells. */
    public static final int ORIGINAL_REACH = 4 * 512;
    /** The start's distance beyond the tower edge: −3136 = −(2048 + 1088). */
    public static final int START_MARGIN = -CameraPose.START_DISTANCE - ORIGINAL_REACH;
    /** The "In" limit's distance beyond the tower edge: −2368 = −(2048 + 320). */
    public static final int NEAREST_MARGIN = -CameraPose.NEAREST - ORIGINAL_REACH;
    /** The originals' limits, for every tower within the 8 × 8 area. */
    public static final CameraLimits ORIGINAL = new CameraLimits(ORIGINAL_REACH, ORIGINAL_REACH, ORIGINAL_REACH,
            ORIGINAL_REACH);

    private static final int UNITS_PER_CELL = 512;
    /** The grid centre 7.5 plus the half cell to a cell's far edge: the edge of cell n lies (n − 7) cells away. */
    private static final int CENTRE_OFFSET = 7;

    /** The limits of a tower with {@code grid}: {@link #ORIGINAL} within the 8 × 8 area (or empty). */
    public static CameraLimits of(TowerGrid grid) {
        return TowerExtent.of(grid).filter(extent -> !extent.withinOriginalFootprint()).map(CameraLimits::of)
                .orElse(ORIGINAL);
    }

    private static CameraLimits of(TowerExtent extent) {
        return new CameraLimits(reach(extent.maxY() - CENTRE_OFFSET), reach(CENTRE_OFFSET + 1 - extent.minX()),
                reach(CENTRE_OFFSET + 1 - extent.minY()), reach(extent.maxX() - CENTRE_OFFSET));
    }

    private static int reach(int cells) {
        return Math.max(ORIGINAL_REACH, cells * UNITS_PER_CELL);
    }

    /** How far the tower reaches from the centre towards {@code camera}'s eye (units). */
    public int reach(CameraId camera) {
        return switch (camera) {
            case NORTH -> north;
            case EAST -> east;
            case SOUTH -> south;
            case WEST -> west;
        };
    }

    /** The distance every tower start resets {@code camera} to (negative: behind the centre). */
    public int startDistance(CameraId camera) {
        return -(reach(camera) + START_MARGIN);
    }

    /** The "In" limit of {@code camera}: no zoom step goes above it. */
    public int nearest(CameraId camera) {
        return -(reach(camera) + NEAREST_MARGIN);
    }

    /**
     * The sideways offset limit of {@code camera} (Task 9, acceptance change): {@code max(ORIGINAL_REACH * 2,
     * sideReach + ORIGINAL_REACH)}, where {@code sideReach} is the larger of the tower's two reaches along the
     * camera's sideways axis. Since both reaches are already at least {@link #ORIGINAL_REACH}, the outer
     * {@code max} only restates {@link CameraPose#OFFSET_LIMIT} for a tower within the 8 × 8 area.
     */
    public int sidewaysLimit(CameraId camera) {
        int sideReach = switch (camera) {
            case NORTH, SOUTH -> Math.max(east, west);
            case EAST, WEST -> Math.max(north, south);
        };
        return Math.max(CameraPose.OFFSET_LIMIT, sideReach + ORIGINAL_REACH);
    }
}
