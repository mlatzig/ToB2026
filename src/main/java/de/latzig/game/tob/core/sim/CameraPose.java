package de.latzig.game.tob.core.sim;

/**
 * The pose of one external camera (record {@code W001C8E + 32·n}) in original units, 512 per cell: height
 * {@code $8}, sideways offset {@code $14} and distance {@code $16}. Every tower start resets it
 * ({@code S:17708-17720}). Steps and limits follow the panel routine ({@code S:8408-8453} for moves,
 * {@code S:8478-8514} for the zoom). A step that would leave the range is ignored, not clamped.
 * <p>
 * The world position ({@code $6}, {@code $A}) is the offset and the distance turned by the camera's heading, a quarter
 * turn per heading step ({@code L00A574}, {@code S:8586-8597}). {@code $6} points east and {@code $A} north: a walk
 * north adds to {@code $A}, a walk east to {@code $6} ({@code L00BAAC}, {@code S:9983-9992}). The origin is the tower
 * centre. The "Link Cameras" option ({@code W0017CA}) is not modelled; it comes in Phase 5.
 * <p>
 * Phase 6a: the start distance and the "In" limit come from the tower's {@link CameraLimits}; the constants below
 * are the originals' values, which every tower within the 8 × 8 area keeps. Task 9 (acceptance change): the sideways
 * offset limit also comes from {@link CameraLimits} ({@link CameraLimits#sidewaysLimit}); {@link #OFFSET_LIMIT} is
 * the originals' value, kept exactly for a tower within the 8 × 8 area.
 */
public final class CameraPose {

    public static final int START_HEIGHT = 0x100;
    public static final int MIN_HEIGHT = 0x80;
    /** Exclusive: the highest reachable height is {@code 0xF80}. */
    public static final int HEIGHT_LIMIT = 0x1000;
    public static final int HEIGHT_STEP = 0x80;
    /** Inclusive in both directions; a tower within the 8 × 8 area keeps exactly this value. */
    public static final int OFFSET_LIMIT = 0x1000;
    public static final int OFFSET_STEP = 0x80;
    /** {@code $F3C0}: about 6.1 cells from the tower centre. */
    public static final int START_DISTANCE = -3136;
    /**
     * The "In" limit, deviation 5: 4.625 cells from the centre, three In steps from the start. The original stops at
     * {@code $F9C0} (−1600, about 3.1 cells, {@code S:8487}), inside the 8×8 tower (±2048), and its renderer just
     * drops the rows behind the eye ({@code L00BF14}, {@code S:10243-10250}). ToB 2026 keeps the eye more than half a
     * cell in front of the tower edge. Larger towers keep the same margin from their own edge ({@link CameraLimits},
     * Phase 6a).
     */
    public static final int NEAREST = -2368;
    /** The "Out" limit {@code $C5C0}: about 29 cells. */
    public static final int FARTHEST = -14912;
    public static final int DISTANCE_STEP = 0x100;

    private final CameraId id;
    private final int nearest;
    private final int offsetLimit;
    private int height = START_HEIGHT;
    private int offset;
    private int distance;

    /** A camera of a tower within the original 8 × 8 area. */
    CameraPose(CameraId id) {
        this(id, CameraLimits.ORIGINAL);
    }

    /** A camera at its start for a tower with {@code limits} (Phase 6a; Task 9: also the sideways limit). */
    CameraPose(CameraId id, CameraLimits limits) {
        this.id = id;
        this.nearest = limits.nearest(id);
        this.offsetLimit = limits.sidewaysLimit(id);
        this.distance = limits.startDistance(id);
    }

    public CameraId id() {
        return id;
    }

    /** Height above the base of level 0. */
    public int height() {
        return height;
    }

    /** Sideways offset; positive is to the camera's right. */
    public int offset() {
        return offset;
    }

    /** Negative: the camera stands behind the tower centre, seen along its heading. */
    public int distance() {
        return distance;
    }

    /** The "In" limit: {@link #NEAREST} for a tower within the 8 × 8 area, else from {@link CameraLimits}. */
    public int nearest() {
        return nearest;
    }

    /**
     * The sideways offset limit (Task 9): {@link #OFFSET_LIMIT} for a tower within the 8 × 8 area, else from
     * {@link CameraLimits#sidewaysLimit}.
     */
    public int offsetLimit() {
        return offsetLimit;
    }

    /** {@code $6}: east of the tower centre. */
    public int east() {
        return switch (id) {
            case NORTH -> offset;
            case EAST -> distance;
            case SOUTH -> -offset;
            case WEST -> -distance;
        };
    }

    /** {@code $A}: north of the tower centre. */
    public int north() {
        return switch (id) {
            case NORTH -> distance;
            case EAST -> -offset;
            case SOUTH -> -distance;
            case WEST -> offset;
        };
    }

    /** Forward ({@code S:8408-8418}): height + $80, ignored from $1000 on. */
    boolean raise() {
        int next = height + HEIGHT_STEP;
        if (next >= HEIGHT_LIMIT) {
            return false;
        }
        height = next;
        return true;
    }

    /** Back ({@code S:8419-8429}): height − $80, ignored below $80. */
    boolean lower() {
        int next = height - HEIGHT_STEP;
        if (next < MIN_HEIGHT) {
            return false;
        }
        height = next;
        return true;
    }

    /**
     * Left ({@code S:8430-8441}): offset − $80, ignored below −{@link #offsetLimit()} ({@link #OFFSET_LIMIT} for a
     * tower within the 8 × 8 area, Task 9).
     */
    boolean shiftLeft() {
        int next = offset - OFFSET_STEP;
        if (next < -offsetLimit) {
            return false;
        }
        offset = next;
        return true;
    }

    /**
     * Right ({@code S:8442-8453}): offset + $80, ignored above {@link #offsetLimit()} ({@link #OFFSET_LIMIT} for a
     * tower within the 8 × 8 area, Task 9).
     */
    boolean shiftRight() {
        int next = offset + OFFSET_STEP;
        if (next > offsetLimit) {
            return false;
        }
        offset = next;
        return true;
    }

    /** Stop/In ({@code S:8478-8495}): distance + $100, ignored above {@link #nearest()} (deviation 5). */
    boolean zoomIn() {
        int next = distance + DISTANCE_STEP;
        if (next > nearest) {
            return false;
        }
        distance = next;
        return true;
    }

    /** Run/Out ({@code S:8500-8514}): distance − $100, ignored below −14912. */
    boolean zoomOut() {
        int next = distance - DISTANCE_STEP;
        if (next < FARTHEST) {
            return false;
        }
        distance = next;
        return true;
    }
}
