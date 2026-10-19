package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;

import de.latzig.game.tob.core.model.ObjectFamily;

/**
 * Win/lose rules and destruction shared by input, handlers and later phases: counters with the victory check
 * ({@code L00AD14}, {@code S:9102-9114}), the failure check ({@code L00AD48}, {@code S:9116-9138}), pending exits,
 * and the destruction mechanism of the zap ({@code S:8786-8812}) with the class-$1A handler ({@code L00BA1E},
 * {@code S:9945-9955}).
 */
public final class Rules {

    public static final int EXIT_COMPLETED = 1;
    public static final int EXIT_TIMEOUT = 2;
    public static final int EXIT_SPIDERS_DESTROYED = 3;
    public static final int EXIT_GRABBER_DESTROYED = 4;
    public static final int EXIT_ABANDONED = 5;
    public static final int EXIT_RESTART = 6;
    /** Exit delay = 21 + clock phase, i.e. 21–27 frames ({@code S:9110-9113}). */
    public static final int EXIT_DELAY_BASE = 21;
    public static final int DESTROY_FRAMES = 32;
    /** The freezer's timer +1C after a grab ({@code move.w #$80,$1c(a2)}, {@code S:8965}). */
    public static final int FREEZER_TIMER = 128;
    /** The freezer's timer at which its freeze ends ({@code cmp.w #$40,d2}, {@code S:9892-9895}). */
    public static final int FREEZER_THAW = 64;

    /** Neighbour order of the mine and bomb loops ({@code move.w #$3,d0} … {@code dbf d0}, offsets {@code W002A98}). */
    private static final List<Direction> BLAST_ORDER = List.of(Direction.WEST, Direction.SOUTH, Direction.EAST,
            Direction.NORTH);

    private Rules() {
    }

    /** A grab collected a klondike: saturating +1, then the victory check. */
    static boolean addKlondike(WorldState world, List<SimEvent> out) {
        Counters counters = world.counters();
        if (!counters.incrementKlondikes()) {
            return false;
        }
        out.add(new SimEvent.CounterChanged(world.frame(), CounterKind.KLONDIKES, counters.klondikesCollected()));
        checkVictory(world, out);
        return true;
    }

    /** A robot was destroyed by a zap or an exchanger: saturating +1, then the victory check. */
    static boolean addRobot(WorldState world, List<SimEvent> out) {
        Counters counters = world.counters();
        if (!counters.incrementRobots()) {
            return false;
        }
        out.add(new SimEvent.CounterChanged(world.frame(), CounterKind.ROBOTS, counters.robotsDestroyed()));
        checkVictory(world, out);
        return true;
    }

    /** Only called after an increment, so a 0/0 tower never completes. */
    static void checkVictory(WorldState world, List<SimEvent> out) {
        Counters counters = world.counters();
        if (counters.klondikesCollected() == counters.klondikesRequired()
                && counters.robotsDestroyed() == counters.robotsRequired()) {
            setExit(world, EXIT_COMPLETED, out);
        }
    }

    /** Runs after a spider was destroyed. Nothing fails while the grabber is alive. */
    static void checkFailure(WorldState world, List<SimEvent> out) {
        if (world.spider(SpiderId.GRABBER).isAlive()) {
            return;
        }
        Counters counters = world.counters();
        if (counters.klondikesCollected() < counters.klondikesRequired()) {
            setExit(world, EXIT_GRABBER_DESTROYED, out);
        } else if (!world.spider(SpiderId.ZAPPER).isAlive() && !world.spider(SpiderId.PUSHER).isAlive()) {
            setExit(world, EXIT_SPIDERS_DESTROYED, out);
        }
    }

    /** Sets a pending exit with delay 21 + clock phase, unless an exit is pending or final already. */
    static boolean setExit(WorldState world, int code, List<SimEvent> out) {
        if (world.isExitPending() || world.isFinished()) {
            return false;
        }
        int delay = EXIT_DELAY_BASE + world.clockPhase();
        world.setExit(code, delay);
        out.add(new SimEvent.ExitPending(world.frame(), code, delay));
        return true;
    }

    /**
     * Starts the 32-frame destruction of an alive object: its flag word is cleared, it stops, frees its second cell
     * and keeps its current cell ({@code S:8786-8793}). Lift riders are immune ({@code S:8766-8771}). Counting a robot
     * is the caller's job (only zaps and exchangers count); a destroyed spider triggers the failure check.
     */
    static boolean destroy(WorldState world, SimObject object, List<SimEvent> out) {
        if (!object.isAlive() || object.isRiding()) {
            return false;
        }
        object.setObjectFlags(0);
        object.setMoveCounter(0);
        object.setDestroyCounter(DESTROY_FRAMES);
        if (object.secondCell() != null) {
            world.occupancy().release(object.secondCell());
            object.setSecondCell(null);
        }
        object.setStatus(ObjectStatus.DESTROYING);
        out.add(new SimEvent.Destroyed(world.frame(), object.slot(), object.cell()));
        if (object.isSpider()) {
            checkFailure(world, out);
        }
        return true;
    }

    /**
     * The effect of a zap ray on a zappable object ({@code S:8786-8812}), whoever fired it: the destruction of
     * {@link #destroy}; for a spider that is all (the failure check runs there). For any other object: a freezer with
     * a running timer has it cleared ({@code S:8796-8800}); if it was in its freeze phase (timer above 64), the world
     * thaws ({@code S:8801}) and {@link SimEvent.Thawed} is emitted. In its recharge phase (timer 1..64) the freeze
     * counter stays: <b>deliberate deviation 4</b> of the Phase 2d spec (the original adds 1 there as well, which
     * lifts {@code W001898} to +1, so that a later freeze from a second freezer has no effect). Then robots +1 with
     * the victory check.
     */
    static void zap(WorldState world, SimObject target, List<SimEvent> out) {
        if (!destroy(world, target, out) || target.isSpider()) {
            return;
        }
        if (target.type().family() == ObjectFamily.FREEZER && target.classTimer() != 0) {
            boolean freezing = target.classTimer() > FREEZER_THAW;
            target.setClassTimer(0);
            if (freezing) {
                world.thaw();
                out.add(new SimEvent.Thawed(world.frame(), target.slot()));
            }
        }
        addRobot(world, out);
    }

    /**
     * The explosion of a proximity mine ({@code S:9682-9720}) or a time bomb ({@code S:9742-9778}) at its current cell
     * (+14, so the half-way rule applies to a bomb being pushed). Emits {@link SimEvent.Exploded}, then hits the holder
     * of every N/E/S/W neighbour cell in the order W, S, E, N, whether it stands in the cell or claimed it, and whatever
     * its flags say: blocks, enemies, lizards, converters, mines and bombs are destroyed too. A lift rider is spared
     * ({@code S:9692-9698}). A holder that is already being destroyed gets the full 32 frames again (class $1A,
     * +1A := 32, flag word 0; {@code S:9699-9706}). A destroyed mine or bomb never explodes in turn, because only alive
     * objects run their handler. Then the exploding object itself is destroyed; with {@code destroyFloor} its flag word
     * becomes {@link ObjectFlags#DESTROYS_FLOOR} ({@code S:9709-9714}), so its floor goes when its destruction ends
     * ({@link #tickDestroyed}). Nothing is counted; the failure check runs ({@code S:9720}, {@code S:9778}).
     */
    static void explode(WorldState world, SimObject center, boolean destroyFloor, List<SimEvent> out) {
        if (!center.isAlive()) {
            return;
        }
        List<CellPos> cells = new ArrayList<>();
        for (Direction direction : BLAST_ORDER) {
            CellPos cell = center.cell().step(direction);
            if (cell.inGrid()) {
                cells.add(cell);
            }
        }
        out.add(new SimEvent.Exploded(world.frame(), center.slot(), cells));
        for (CellPos cell : cells) {
            int holder = world.occupancy().holder(cell);
            // a negative holder is no object (Occupancy.FREE); the bomb's own second cell is skipped here
            if (holder >= 0 && holder != center.slot()) {
                blast(world, world.object(holder), out);
            }
        }
        destroy(world, center, out);
        if (destroyFloor) {
            center.setObjectFlags(ObjectFlags.DESTROYS_FLOOR);
        }
        checkFailure(world, out);
    }

    /** One neighbour of an explosion: destroyed (riders are immune), or re-armed if it is being destroyed already. */
    private static void blast(WorldState world, SimObject object, List<SimEvent> out) {
        if (object.status() == ObjectStatus.DESTROYING) {
            object.setObjectFlags(0);
            object.setDestroyCounter(DESTROY_FRAMES);
            out.add(new SimEvent.Destroyed(world.frame(), object.slot(), object.cell()));
        } else {
            destroy(world, object, out);
        }
    }

    /**
     * Handler of a destroying object ({@code L00BA1E}, {@code S:9945-9955}): after 32 calls it is removed. If its flag
     * word holds {@link ObjectFlags#DESTROYS_FLOOR} (an exploded mine in a tower whose mines destroy the floor), the bit
     * is cleared and the shared floor rule then runs on the cell it left ({@code S:9952-9954}, {@link FloorRule}).
     */
    static void tickDestroyed(WorldState world, SimObject object, List<SimEvent> out) {
        object.setDestroyCounter(object.destroyCounter() - 1);
        if (object.destroyCounter() != 0) {
            return;
        }
        CellPos cell = object.cell();
        boolean destroysFloor = (object.objectFlags() & ObjectFlags.DESTROYS_FLOOR) != 0;
        object.setObjectFlags(object.objectFlags() & ~ObjectFlags.DESTROYS_FLOOR);
        remove(world, object, out);
        if (destroysFloor) {
            FloorRule.remove(world, cell, out);
        }
    }

    /**
     * Removes an object (class $3F): its cells are freed and its cell is then cleared ({@code clr.w $14(a0)},
     * {@code S:9951}, {@code S:9831}), as in the original, so no removed object keeps a stale position.
     */
    static void remove(WorldState world, SimObject object, List<SimEvent> out) {
        if (object.secondCell() != null) {
            world.occupancy().release(object.secondCell());
            object.setSecondCell(null);
        }
        object.setStatus(ObjectStatus.REMOVED);
        world.occupancy().release(object.cell());
        out.add(new SimEvent.Removed(world.frame(), object.slot(), object.cell()));
        object.setCell(null);
    }
}
