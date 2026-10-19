package de.latzig.game.tob.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.latzig.game.tob.core.sim.element.Behaviours;
import de.latzig.game.tob.core.sim.element.ElementBehaviour;

/**
 * The facade through which element behaviours ({@code core.sim.element}) read and change the world. Only
 * {@link Simulation} creates one (package-private constructor), so client code can never obtain one. Every mutator
 * delegates to the package-private core and emits its events into the events of the current frame.
 */
public final class SimContext {

    private final WorldState world;
    private final List<ElementBehaviour> behaviours;
    private List<SimEvent> out = new ArrayList<>();

    /** One behaviour per slot, in list order ({@link Behaviours#forType}). */
    SimContext(WorldState world) {
        this.world = Objects.requireNonNull(world, "world");
        List<ElementBehaviour> perSlot = new ArrayList<>();
        for (SimObject object : world.objects()) {
            perSlot.add(Behaviours.forType(object.type()));
        }
        this.behaviours = List.copyOf(perSlot);
    }

    /** Directs the events of the following calls into {@code frameEvents} (one list per frame). */
    void begin(List<SimEvent> frameEvents) {
        this.out = Objects.requireNonNull(frameEvents, "frameEvents");
    }

    /** The event list of the current frame. */
    List<SimEvent> out() {
        return out;
    }

    ElementBehaviour behaviour(SimObject object) {
        return behaviours.get(object.slot());
    }

    public WorldState world() {
        return world;
    }

    /** {@link WorldState#frame()}. */
    public int frame() {
        return world.frame();
    }

    public void emit(SimEvent event) {
        out.add(Objects.requireNonNull(event, "event"));
    }

    /** Fires a zap ray from the emitter's current cell ({@link Ray#fire}); true for a hit. */
    public boolean fireZap(SimObject emitter, Direction direction) {
        return Ray.fire(this, emitter, direction, RayType.ZAP);
    }

    /** Fires a push ray from the emitter's current cell ({@link Ray#fire}); true for a hit. */
    public boolean firePush(SimObject emitter, Direction direction) {
        return Ray.fire(this, emitter, direction, RayType.PUSH);
    }

    /** Starts the 32-frame destruction ({@link Rules#destroy}); does not count. */
    public boolean destroy(SimObject object) {
        return Rules.destroy(world, object, out);
    }

    /** Robots destroyed +1 (saturating), then the victory check. */
    public boolean addRobot() {
        return Rules.addRobot(world, out);
    }

    /** Klondikes collected +1 (saturating), then the victory check. */
    public boolean addKlondike() {
        return Rules.addKlondike(world, out);
    }

    /**
     * Claims {@code cell} as the object's second cell (+16): only if the object holds a cell, has no second cell yet
     * and the cell is free.
     */
    public boolean claim(SimObject object, CellPos cell) {
        if (!object.holdsCell() || object.secondCell() != null || !world.occupancy().isFree(cell)) {
            return false;
        }
        world.occupancy().set(cell, object.slot());
        object.setSecondCell(cell);
        return true;
    }

    /** Frees the object's second cell (+16), if it has one. */
    public void release(SimObject object) {
        if (object.secondCell() != null) {
            world.occupancy().release(object.secondCell());
            object.setSecondCell(null);
        }
    }

    /** Removes an object at once (class $3F): frees its cells and clears its position ({@link Rules#remove}). */
    public void remove(SimObject object) {
        Rules.remove(world, object, out);
    }

    /** Starts a push of a standing object if the cell beyond is free ({@link Push#start}). */
    public boolean startPush(SimObject target, Direction direction) {
        return Push.start(world, target, direction, out);
    }

    /**
     * Exchanges two objects ({@code S:8981-9010}): frees both second cells, then swaps their current cells, the
     * occupancy of those cells and their world positions. Facing and movement are not touched.
     */
    public void swap(SimObject a, SimObject b) {
        release(a);
        release(b);
        CellPos cellA = a.cell();
        CellPos cellB = b.cell();
        a.setCell(cellB);
        b.setCell(cellA);
        world.occupancy().set(cellB, a.slot());
        world.occupancy().set(cellA, b.slot());
        int dx = b.worldX() - a.worldX();
        int dy = b.worldY() - a.worldY();
        int dz = b.worldZ() - a.worldZ();
        a.moveBy(dx, dy, dz);
        b.moveBy(-dx, -dy, -dz);
    }

    /** Stops the movement at once (+10 := 0); the move kind and direction are kept. */
    public void stop(SimObject object) {
        object.setMoveCounter(0);
    }

    /** Sets the facing (+0C) and emits {@link SimEvent.Turned}. */
    public void setFacing(SimObject object, Direction facing) {
        object.setFacing(facing);
        out.add(new SimEvent.Turned(world.frame(), object.slot(), facing));
    }

    public void setObjectFlags(SimObject object, int flags) {
        object.setObjectFlags(flags);
    }

    public void setDestroyCounter(SimObject object, int counter) {
        object.setDestroyCounter(counter);
    }

    /** Sets the lizard's mark (+1C); null clears it ({@code S:9804}, {@code S:9812}). */
    public void setMark(SimObject lizard, CellPos cell) {
        lizard.setMarkedCell(cell);
    }

    /**
     * Starts a wait ({@code ori.w #$10,$e(a0)}, {@code move.w #$10,$10(a0)}, {@code S:9648-9649}): the movement step
     * counts +10 down without moving ({@code S:10094}); +0E keeps its direction. Emits {@link SimEvent.Idle}.
     */
    public void startIdle(SimObject mover, int frames) {
        mover.setMoveKind(MoveKind.IDLE);
        mover.setMoveCounter(frames);
        out.add(new SimEvent.Idle(world.frame(), mover.slot()));
    }

    /**
     * Starts a watcher turn ({@code S:9537-9539}, {@code S:9548-9550}, {@code S:9555-9557}): +0E and the facing become
     * {@code toFacing} at once, the class timer +1C := {@code frames}, and {@link SimEvent.TurnStarted} is emitted.
     */
    public void startTurn(SimObject mover, Direction toFacing, int frames) {
        mover.setMoveDirection(toFacing);
        mover.setFacing(toFacing);
        mover.setClassTimer(frames);
        out.add(new SimEvent.TurnStarted(world.frame(), mover.slot(), toFacing, frames));
    }

    /** Sets the class timer (+1C): the watcher's turn counter; in Phase 2d the freezer and wiper timers (M4). */
    public void setClassTimer(SimObject object, int value) {
        object.setClassTimer(value);
    }

    /**
     * Sets the mover's direction (+0E) and its facing without moving, and emits {@link SimEvent.Turned}: a blocked worm
     * ({@code eori.w #$2,$e(a0)}, {@code S:9485}).
     */
    public void setHeading(SimObject mover, Direction heading) {
        mover.setMoveDirection(heading);
        setFacing(mover, heading);
    }

    /** Reverses a walk at once, at any counter ({@link Walk#turnBack}); false if the mover is not walking. */
    public boolean reverseWalk(SimObject mover) {
        return Walk.turnBack(world, mover, out);
    }

    /** Makes a pushed worm or watcher permanently inert; emits {@link SimEvent.Deactivated} the first time only. */
    public void deactivate(SimObject mover) {
        if (!mover.isDeactivated()) {
            mover.setDeactivated(true);
            out.add(new SimEvent.Deactivated(world.frame(), mover.slot()));
        }
    }

    /**
     * The free-cell test with start {@code L00A638} ({@code S:8639-8657}): if the mover stands and its neighbour in
     * {@code direction} has floor, is not blocked and is neither held nor claimed, the walk starts (+0E := direction,
     * +10 := 32) and {@link SimEvent.MoveStarted} is emitted. The target is claimed later, at the claim frame.
     */
    public boolean tryStep(SimObject mover, Direction direction) {
        return Walk.start(world, mover, direction, out);
    }

    /**
     * The query {@code L00B47C} ({@code S:9577-9593}): the cell has floor, is not blocked and is neither held nor
     * claimed. No standing test, no effect.
     */
    public boolean isFree(CellPos cell) {
        return world.isWalkable(cell) && world.occupancy().isFree(cell);
    }

    /** The shared floor rule ({@link FloorRule}); emits {@link SimEvent.FloorEaten} when the floor goes. */
    public boolean removeFloor(CellPos cell) {
        return FloorRule.remove(world, cell, out);
    }

    /**
     * A proximity mine or time bomb explodes ({@link Rules#explode}): its neighbours and then itself are destroyed;
     * with {@code removeFloorOfSelf} its floor goes when its 32-frame destruction ends.
     */
    public void explode(SimObject center, boolean removeFloorOfSelf) {
        Rules.explode(world, center, removeFloorOfSelf, out);
    }

    /** A wiper sweep in {@code direction} from the wiper's current cell ({@link FloorRule#sweep}). */
    public void sweep(SimObject wiper, Direction direction) {
        FloorRule.sweep(world, wiper, direction, out);
    }

    /**
     * A forcer grab ({@link ForcerLifts}): false while a forcer-lift motion runs (the global lock); otherwise every
     * qualifying lift starts to move {@code up} or down, and the grab is a hit even if no lift qualified.
     */
    public boolean startForcerLifts(boolean up) {
        return world.forcerLifts().start(world, up, out);
    }

    /** {@code W001898} −1 (a freezer starts, Phase 2d). */
    public void freeze() {
        world.freeze();
    }

    /** {@code W001898} +1 (a freezer expires or is zapped). */
    public void thaw() {
        world.thaw();
    }
}
