package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLimits;

/**
 * The lift motion started by a forcer: grab of forcer up {@code L00ABDC} ({@code S:9023-9061}) and forcer down
 * {@code L00AC74} ({@code S:9062-9101}), motion {@code L00AE16} ({@code S:9169-9214}, pipeline step 3c, which runs only
 * while the world is not frozen). One motion at a time: its 32-frame timer {@code W0018C6} is the global lock shared by
 * up and down, also when no lift qualified.
 * <ul>
 * <li>Up: every lift cell with the platform at the bottom (lift bit, no "rides down" bit, not blocked) and no holder,
 * at most {@link TowerLimits#maxLifts} (20 within the original 8 × 8 × 4 area, 64 beyond it, Phase 6a), scanned from
 * the bottom level up, rows south → north, x west → east. The cell above gets its floor at once; after 32 frames the
 * lower cell becomes blocked.</li>
 * <li>Down: every lift cell with the platform at the top (lift and "rides down" bits) above a blocked cell, with no
 * holder, at most {@link TowerLimits#maxLifts}, scanned from the top level down, rows north → south, x east → west.
 * The lower cell is unblocked at once; after 32 frames the upper cell loses its floor.</li>
 * <li>Both cells of each shaft are held by the pseudo slot {@link Occupancy#FORCER_LIFT} until the end. The original
 * tests only the platform cell ({@code S:9041}, {@code S:9080}) and overwrites the other cell's holder
 * ({@code S:9044}, {@code S:9083}); ToB 2026 also requires the other cell to be free (extension of deviation 2, only
 * reachable after an exchange stopped a ride mid-shaft, compare deviation 2 in {@link LiftRide}).</li>
 * </ul>
 * The cell flags stay authoritative; the {@link Lift} views follow (MOVING_UP/MOVING_DOWN, then TOP/BOTTOM). The
 * render-only bit 6 ({@code S:9084}, {@code S:9191}) is not kept.
 */
public final class ForcerLifts {

    /** Frames of a motion and of the lock ({@code move.w #$20,W0018C6}, {@code S:9056}, {@code S:9096}). */
    public static final int FRAMES = 32;
    /**
     * At most this many lifts move at once in a tower within the original 8 × 8 × 4 area
     * ({@code cmp.w #$14,d3}, {@code S:9050}, {@code S:9090}); {@link TowerLimits#maxLifts} raises this to 64 in a
     * larger tower (author decision 2026-10-14, Phase 6a task 1). Original towers stay bit-exact: their grid is
     * always within the area, so {@link TowerLimits#maxLifts} returns this same value for them.
     */
    public static final int MAX_LIFTS = TowerLimits.MAX_LIFTS;

    private final List<CellPos> platforms = new ArrayList<>();
    private int timer;
    private boolean up;
    private int limit = MAX_LIFTS;

    ForcerLifts() {
    }

    /** True while a motion runs; then every forcer grab fails. */
    public boolean isActive() {
        return timer != 0;
    }

    /** Frames left: 32 at the grab, 0 when idle. */
    public int timer() {
        return timer;
    }

    /** Direction of the current or last motion. */
    public boolean isUp() {
        return up;
    }

    /** The platform cells of the moving lifts in selection order: the lower cells going up, the upper going down. */
    public List<CellPos> platforms() {
        return List.copyOf(platforms);
    }

    /** Every cell the moving lifts hold: each platform cell, followed by its partner above or below. */
    public List<CellPos> heldCells() {
        List<CellPos> cells = new ArrayList<>();
        for (CellPos platform : platforms) {
            cells.add(platform);
            cells.add(partner(platform));
        }
        return cells;
    }

    /** A forcer grab: false while a motion runs (the lock); otherwise selects and starts the lifts, a hit. */
    boolean start(WorldState world, boolean goUp, List<SimEvent> out) {
        if (timer != 0) {
            return false;
        }
        up = goUp;
        limit = TowerLimits.maxLifts(world.tower().grid());
        if (goUp) {
            for (int level = 0; level < TowerGrid.LEVELS - 1; level++) {
                for (int y = TowerGrid.DEPTH - 1; y >= 0; y--) {
                    for (int x = 0; x < TowerGrid.WIDTH; x++) {
                        select(world, new CellPos(x, y, level), out);
                    }
                }
            }
        } else {
            for (int level = TowerGrid.LEVELS - 1; level > 0; level--) {
                for (int y = 0; y < TowerGrid.DEPTH; y++) {
                    for (int x = TowerGrid.WIDTH - 1; x >= 0; x--) {
                        select(world, new CellPos(x, y, level), out);
                    }
                }
            }
        }
        timer = FRAMES;
        return true;
    }

    private void select(WorldState world, CellPos platform, List<SimEvent> out) {
        if (platforms.size() == limit || !world.isLift(platform)) {
            return;
        }
        CellPos partner = partner(platform);
        boolean qualifies = up ? !world.liftRidesDown(platform) && !world.isBlocked(platform)
                : world.liftRidesDown(platform) && world.isBlocked(partner);
        if (!qualifies || !world.occupancy().isFree(platform) || !world.occupancy().isFree(partner)) {
            return;
        }
        world.occupancy().set(platform, Occupancy.FORCER_LIFT);
        world.occupancy().set(partner, Occupancy.FORCER_LIFT);
        if (up) {
            world.setFlags(partner, CellFlags.FLOOR);
        } else {
            world.clearFlags(partner, CellFlags.BLOCKED);
        }
        LiftState moving = up ? LiftState.MOVING_UP : LiftState.MOVING_DOWN;
        world.liftAt(platform).ifPresent(lift -> lift.update(moving, Occupancy.FREE));
        platforms.add(platform);
        out.add(new SimEvent.ForcerLiftStarted(world.frame(), platform, up));
    }

    /** One frame of the motion (step 3c): after 32 frames the lifts arrive and free their cells. */
    void advance(WorldState world, List<SimEvent> out) {
        if (timer == 0) {
            return;
        }
        timer--;
        if (timer != 0) {
            return;
        }
        LiftState arrived = up ? LiftState.TOP : LiftState.BOTTOM;
        for (CellPos platform : platforms) {
            if (up) {
                world.setFlags(platform, CellFlags.BLOCKED);
            } else {
                world.clearFlags(platform, CellFlags.FLOOR);
            }
            world.occupancy().release(platform);
            world.occupancy().release(partner(platform));
            world.liftAt(platform).ifPresent(lift -> lift.update(arrived, Occupancy.FREE));
            out.add(new SimEvent.ForcerLiftFinished(world.frame(), platform));
        }
        platforms.clear();
    }

    private CellPos partner(CellPos platform) {
        return up ? platform.above() : platform.below();
    }
}
