package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/**
 * Complete runtime state of one tower attempt: cell flags, live occupancy, objects in list order, lifts, counters,
 * frame counter, clock, READY and exit state. The public methods are read-only; the simulation mutates through the
 * package-private ones.
 */
public final class WorldState {

    /** Slots 0–2 are the zapper, pusher and grabber. */
    public static final int SPIDER_SLOTS = 3;
    /** The clock {@code W00167A} counts 6..0: one time-limit second is 7 frames ({@code S:6918-6936}). */
    public static final int CLOCK_PERIOD = 7;
    /**
     * The freeze counter's start value with the enemies frozen (Phase 6b, the designer's "Enemy Movement" off): −1
     * after READY, and a freezer's −1/+1 keep it negative, so the enemies never move.
     */
    public static final int FROZEN_FOR_GOOD = -2;

    private final TowerDefinition tower;
    private final SimOptions options;
    private final int[] flags = new int[CellPos.COUNT];
    private final Occupancy occupancy = new Occupancy();
    private final List<SimObject> objects;
    private final List<SimObject> objectsView;
    private final List<Lift> lifts;
    private final Counters counters;
    private final ForcerLifts forcerLifts = new ForcerLifts();
    private int frame;
    private int clockPhase;
    private int timeLeft;
    private boolean ready = true;
    /**
     * {@code W001898}: a counter, negative while frozen. Tower load sets it to −1 ({@code S:17652}); the designer's
     * test play sets it to "Enemy Movement" − 1 ({@code S:12823-12825}), i.e. −2 with the enemies frozen, so the end of
     * READY leaves the world frozen for good ({@link SimOptions#enemiesFrozen()}).
     */
    private int freeze;
    private int exitCode;
    private int exitDelay;
    private boolean finished;

    private WorldState(TowerDefinition tower, SimOptions options, List<SimObject> objects, List<Lift> lifts) {
        this.tower = tower;
        this.options = options;
        this.objects = objects;
        this.objectsView = Collections.unmodifiableList(objects);
        this.lifts = List.copyOf(lifts);
        Conditions conditions = tower.conditions();
        this.counters = new Counters(conditions.klondikes(), conditions.robots());
        this.timeLeft = conditions.timeLimitEnabled() ? conditions.timeLimitSeconds() : 0;
        this.freeze = options.enemiesFrozen() ? FROZEN_FOR_GOOD : -1;
    }

    /**
     * Builds the start state. List order (oracle §1.1): the three spider slots, then every other object by level
     * ascending, row south → north (y descending), x west → east. Absent spiders keep their slot as ABSENT. Other
     * objects start with the direction and the flag word of the descriptor table ({@link ObjectDescriptor}).
     *
     * @throws IllegalArgumentException if a lift start has no matching stop or a spider is placed twice
     */
    public static WorldState load(TowerDefinition tower, SimOptions options) {
        Objects.requireNonNull(tower, "tower");
        Objects.requireNonNull(options, "options");
        TowerGrid grid = tower.grid();
        List<SimObject> objects = new ArrayList<>();
        for (SpiderId id : SpiderId.values()) {
            objects.add(new SimObject(id.slot(), id.type(), ObjectStatus.ABSENT, null, Direction.NORTH));
        }
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = TowerGrid.DEPTH - 1; y >= 0; y--) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = grid.cell(x, y, level);
                    if (!cell.hasObject()) {
                        continue;
                    }
                    CellPos pos = new CellPos(x, y, level);
                    if (cell.object().isSpider()) {
                        SpiderId id = SpiderId.of(cell.object());
                        if (objects.get(id.slot()).status() != ObjectStatus.ABSENT) {
                            throw new IllegalArgumentException(cell.object().code() + " placed twice");
                        }
                        objects.set(id.slot(),
                                new SimObject(id.slot(), cell.object(), ObjectStatus.ALIVE, pos, Direction.NORTH));
                    } else {
                        objects.add(new SimObject(objects.size(), cell.object(), ObjectStatus.ALIVE, pos,
                                ObjectDescriptor.direction(cell.object())));
                    }
                }
            }
        }
        WorldState world = new WorldState(tower, options, objects, pairLifts(grid));
        for (int index = 0; index < CellPos.COUNT; index++) {
            CellPos pos = CellPos.ofIndex(index);
            world.flags[index] = CellFlags.of(grid.cell(pos.x(), pos.y(), pos.level()).base());
        }
        for (SimObject object : objects) {
            if (object.holdsCell()) {
                world.occupancy.set(object.cell(), object.slot());
            }
        }
        return world;
    }

    /** Up start + up stop above, down start + down stop below; orphan stops stay plain flag cells. */
    private static List<Lift> pairLifts(TowerGrid grid) {
        List<Lift> lifts = new ArrayList<>();
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    CellPos pos = new CellPos(x, y, level);
                    BaseType base = grid.cell(x, y, level).base();
                    if (base == BaseType.LIFT_UP_START) {
                        CellPos stop = pos.above();
                        if (baseAt(grid, stop) != BaseType.LIFT_UP_STOP) {
                            throw new IllegalArgumentException("lift up start at " + pos + " has no up stop above");
                        }
                        lifts.add(new Lift(pos, stop, LiftState.BOTTOM));
                    } else if (base == BaseType.LIFT_DOWN_START) {
                        CellPos stop = pos.below();
                        if (baseAt(grid, stop) != BaseType.LIFT_DOWN_STOP) {
                            throw new IllegalArgumentException(
                                    "lift down start at " + pos + " has no down stop below");
                        }
                        lifts.add(new Lift(stop, pos, LiftState.TOP));
                    }
                }
            }
        }
        return lifts;
    }

    private static BaseType baseAt(TowerGrid grid, CellPos pos) {
        return pos.inGrid() ? grid.cell(pos.x(), pos.y(), pos.level()).base() : BaseType.EMPTY;
    }

    public TowerDefinition tower() {
        return tower;
    }

    public SimOptions options() {
        return options;
    }

    /** The {@link CellFlags} bits of a cell; 0 outside the grid. */
    public int flags(CellPos pos) {
        return pos.inGrid() ? flags[pos.index()] : 0;
    }

    public boolean hasFloor(CellPos pos) {
        return (flags(pos) & CellFlags.FLOOR) != 0;
    }

    public boolean isBlocked(CellPos pos) {
        return (flags(pos) & CellFlags.BLOCKED) != 0;
    }

    public boolean isLift(CellPos pos) {
        return (flags(pos) & CellFlags.LIFT) != 0;
    }

    public boolean liftRidesDown(CellPos pos) {
        return (flags(pos) & CellFlags.LIFT_DOWN) != 0;
    }

    /** Floor and not blocked: the cell part of the free-cell test {@code L00A638} ({@code S:8639-8657}). */
    public boolean isWalkable(CellPos pos) {
        return hasFloor(pos) && !isBlocked(pos);
    }

    public Occupancy occupancy() {
        return occupancy;
    }

    /** All slots in list order; removed objects stay as dead entries. */
    public List<SimObject> objects() {
        return objectsView;
    }

    public SimObject object(int slot) {
        return objects.get(slot);
    }

    public SimObject spider(SpiderId id) {
        return objects.get(id.slot());
    }

    public List<Lift> lifts() {
        return lifts;
    }

    public Optional<Lift> liftAt(CellPos pos) {
        for (Lift lift : lifts) {
            if (lift.contains(pos)) {
                return Optional.of(lift);
            }
        }
        return Optional.empty();
    }

    /** The forcer-driven lift motion (pipeline step 3c). */
    public ForcerLifts forcerLifts() {
        return forcerLifts;
    }

    public Counters counters() {
        return counters;
    }

    /** Frame counter {@code W001678}: 0 in READY, then 1, 2, … for the normal frames. */
    public int frame() {
        return frame;
    }

    /** Clock {@code W00167A}, 0..6; starts at 0 (the original keeps it from the previous tower). */
    public int clockPhase() {
        return clockPhase;
    }

    /** Remaining time-limit seconds; 0 = no limit. */
    public int timeLeft() {
        return timeLeft;
    }

    public boolean isReady() {
        return ready;
    }

    /** World frozen: the counter {@code W001898} is negative, during READY and while any freezer is active. */
    public boolean isFrozen() {
        return freeze < 0;
    }

    /**
     * The counter {@code W001898} itself: −1 in READY, one less for every freezer in its freeze phase. It never exceeds
     * 0 in ToB 2026, because a zap during a freezer's recharge phase does not thaw (Phase 2d deviation 4).
     */
    public int freezeCounter() {
        return freeze;
    }

    /** Exit code of the pending or final exit; 0 = none. */
    public int exitCode() {
        return exitCode;
    }

    /** Frames left until a pending exit becomes final ({@code W001894}); 0 = none pending. */
    public int exitDelay() {
        return exitDelay;
    }

    public boolean isExitPending() {
        return exitDelay != 0;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Consistency of the live occupancy map (test and debug aid): every object that holds a cell holds its current
     * cell and its second cell, every cell of a moving forcer lift is held by {@link Occupancy#FORCER_LIFT}, and every
     * held cell belongs to such an object as its current or second cell, or to a moving forcer lift.
     */
    public List<String> occupancyProblems() {
        List<String> problems = new ArrayList<>();
        for (SimObject object : objects) {
            if (!object.holdsCell()) {
                continue;
            }
            if (occupancy.holder(object.cell()) != object.slot()) {
                problems.add("slot " + object.slot() + " does not hold its cell " + object.cell());
            }
            if (object.secondCell() != null && occupancy.holder(object.secondCell()) != object.slot()) {
                problems.add("slot " + object.slot() + " does not hold its second cell " + object.secondCell());
            }
        }
        List<CellPos> forcerCells = forcerLifts.heldCells();
        for (CellPos cell : forcerCells) {
            if (occupancy.holder(cell) != Occupancy.FORCER_LIFT) {
                problems.add("forcer lift cell " + cell + " is not held by the forcer lift");
            }
        }
        for (Map.Entry<CellPos, Integer> held : occupancy.heldCells().entrySet()) {
            int slot = held.getValue();
            if (slot == Occupancy.FORCER_LIFT) {
                if (!forcerCells.contains(held.getKey())) {
                    problems.add("cell " + held.getKey() + " held by a forcer lift that does not move there");
                }
                continue;
            }
            if (slot < 0 || slot >= objects.size()) {
                problems.add("cell " + held.getKey() + " held by unknown slot " + slot);
                continue;
            }
            SimObject object = objects.get(slot);
            if (!object.holdsCell()
                    || !(held.getKey().equals(object.cell()) || held.getKey().equals(object.secondCell()))) {
                problems.add("cell " + held.getKey() + " held by slot " + slot + ", which neither stands in it nor"
                        + " claimed it");
            }
        }
        return problems;
    }

    void setFlags(CellPos pos, int bits) {
        flags[pos.index()] |= bits;
    }

    void clearFlags(CellPos pos, int bits) {
        flags[pos.index()] &= ~bits;
    }

    void advanceFrame() {
        frame++;
    }

    /** Counts the clock down; true when it wraps from 0 to 6, i.e. once every 7 calls. */
    boolean countClock() {
        clockPhase--;
        if (clockPhase < 0) {
            clockPhase = CLOCK_PERIOD - 1;
            return true;
        }
        return false;
    }

    void setTimeLeft(int seconds) {
        this.timeLeft = seconds;
    }

    void endReady() {
        this.ready = false;
    }

    /** A freezer activated by a fire subtracts 1 from the counter ({@code S:8966}); arrives with 2d. */
    void freeze() {
        freeze--;
    }

    /** The end of READY ({@code S:6916}), a freezer's expiry ({@code S:9895}) or a zap on it ({@code S:8801}) adds 1. */
    void thaw() {
        freeze++;
    }

    void setExit(int code, int delay) {
        this.exitCode = code;
        this.exitDelay = delay;
    }

    /** End-of-frame countdown of a pending exit ({@code S:6944-6949}); true when the exit became final now. */
    boolean countDownExit() {
        if (exitDelay == 0) {
            return false;
        }
        exitDelay--;
        if (exitDelay == 0) {
            finished = true;
            return true;
        }
        return false;
    }

    /** Ends the attempt at once (abandon, restart). */
    void finish(int code) {
        this.exitCode = code;
        this.exitDelay = 0;
        this.finished = true;
    }
}
