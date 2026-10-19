package de.latzig.game.tob.core.sim;

import java.util.Objects;

import de.latzig.game.tob.core.model.ObjectType;

/**
 * Runtime record of one object slot (the original 32-byte object record, oracle §1.2). The public methods are
 * read-only; only the simulation changes the state.
 */
public final class SimObject {

    /** World units per cell (original: 512; a walk frame moves 16). */
    public static final int UNITS_PER_CELL = 512;

    private final int slot;
    private final ObjectType type;
    private ObjectStatus status;
    private CellPos cell;
    private CellPos secondCell;
    private Direction facing;
    private MoveKind moveKind = MoveKind.NONE;
    private Direction moveDirection = Direction.NORTH;
    private int moveCounter;
    private int destroyCounter;
    private int objectFlags;
    private int classTimer;
    private boolean deactivated;
    private CellPos markedCell;
    private int worldX;
    private int worldY;
    private int worldZ;

    SimObject(int slot, ObjectType type, ObjectStatus status, CellPos cell, Direction facing) {
        this.slot = slot;
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.cell = cell;
        this.facing = Objects.requireNonNull(facing, "facing");
        this.moveDirection = facing;
        this.objectFlags = ObjectDescriptor.flags(type);
        if (cell != null) {
            worldX = cell.x() * UNITS_PER_CELL;
            worldY = cell.level() * UNITS_PER_CELL;
            worldZ = cell.y() * UNITS_PER_CELL;
        }
    }

    public int slot() {
        return slot;
    }

    public ObjectType type() {
        return type;
    }

    public ObjectStatus status() {
        return status;
    }

    /**
     * Current cell (+14). A walk switches it to the target at half-way; a lift ride at the end. Null if absent, or
     * once removed ({@code clr.w $14(a0)}, {@code S:9951}).
     */
    public CellPos cell() {
        return cell;
    }

    /** Second cell (+16): the claimed target before half-way, the old cell after it; null when none is held. */
    public CellPos secondCell() {
        return secondCell;
    }

    public Direction facing() {
        return facing;
    }

    /** Kind of the current or last movement; the original keeps it after the end (+0E). */
    public MoveKind moveKind() {
        return moveKind;
    }

    /**
     * Direction of the current or last movement (+0E, low bits). It starts as the start direction, like +0C
     * ({@code S:17584-17586}). Worms, watchers and hoppers steer by it; a walk reversal flips it.
     */
    public Direction moveDirection() {
        return moveDirection;
    }

    /** Movement counter (+10): counts down from 32; 0 = standing. */
    public int moveCounter() {
        return moveCounter;
    }

    /** Destroy counter (+1A): 32..1 while being destroyed; the klondike uses it for its 7 vanish frames. */
    public int destroyCounter() {
        return destroyCounter;
    }

    /** The flag word (+12), {@link ObjectFlags} bits; starts from {@link ObjectDescriptor#flags}. */
    public int objectFlags() {
        return objectFlags;
    }

    /**
     * Class timer (+1C): the watcher's turn counter (4 or 8 frames), the time bomb's fuse (0..800), the freezer's
     * timer (128..0) and the wiper's timer (63..0); 0 for every other type.
     */
    public int classTimer() {
        return classTimer;
    }

    /** Position in world units: x west → east, y up, z north → south (grid row). */
    public int worldX() {
        return worldX;
    }

    public int worldY() {
        return worldY;
    }

    public int worldZ() {
        return worldZ;
    }

    public boolean isAlive() {
        return status == ObjectStatus.ALIVE;
    }

    /** ALIVE or DESTROYING: the object holds its cell in the occupancy map. */
    public boolean holdsCell() {
        return status == ObjectStatus.ALIVE || status == ObjectStatus.DESTROYING;
    }

    public boolean isMoving() {
        return moveCounter != 0;
    }

    /** Riding a lift; Phase 2b/2d make riders immune to rays and explosions. */
    public boolean isRiding() {
        return isMoving() && (moveKind == MoveKind.LIFT_UP || moveKind == MoveKind.LIFT_DOWN);
    }

    public boolean isSpider() {
        return type.isSpider();
    }

    /**
     * A grabbed klondike during its vanish frames (deliberate deviation 1 of the Phase 2b spec): alive, but
     * transparent to every ray until it is removed.
     */
    public boolean isVanishing() {
        return status == ObjectStatus.ALIVE && (objectFlags & ObjectFlags.KLONDIKE_GRABBED) != 0;
    }

    /**
     * A pushed worm or watcher: permanently inert, a pushable obstacle from now on. The original tests
     * {@code +0E >= 4} ({@code S:9465}, {@code S:9489}); the push kind stays after the push, so both agree.
     */
    public boolean isDeactivated() {
        return deactivated;
    }

    /**
     * The lizard's marked cell (+1C, {@code S:9812}): the cell where it last reversed, eaten at a later standing moment
     * once nobody holds it; null = no mark.
     */
    public CellPos markedCell() {
        return markedCell;
    }

    void setStatus(ObjectStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
    }

    void setCell(CellPos newCell) {
        this.cell = newCell;
    }

    void setSecondCell(CellPos newSecondCell) {
        this.secondCell = newSecondCell;
    }

    void setFacing(Direction newFacing) {
        this.facing = Objects.requireNonNull(newFacing, "newFacing");
    }

    void setMoveKind(MoveKind newMoveKind) {
        this.moveKind = Objects.requireNonNull(newMoveKind, "newMoveKind");
    }

    void setMoveDirection(Direction newMoveDirection) {
        this.moveDirection = Objects.requireNonNull(newMoveDirection, "newMoveDirection");
    }

    void setMoveCounter(int newMoveCounter) {
        this.moveCounter = newMoveCounter;
    }

    void setDestroyCounter(int newDestroyCounter) {
        this.destroyCounter = newDestroyCounter;
    }

    void setObjectFlags(int newObjectFlags) {
        this.objectFlags = newObjectFlags;
    }

    void setClassTimer(int newClassTimer) {
        this.classTimer = newClassTimer;
    }

    void setDeactivated(boolean newDeactivated) {
        this.deactivated = newDeactivated;
    }

    void setMarkedCell(CellPos newMarkedCell) {
        this.markedCell = newMarkedCell;
    }

    void moveBy(int dx, int dy, int dz) {
        worldX += dx;
        worldY += dy;
        worldZ += dz;
    }
}
