package de.latzig.game.tob.core.sim;

import java.util.List;
import java.util.Objects;

/**
 * Something that happened in one frame. Every event carries the frame number ({@link WorldState#frame()}: 0 in READY)
 * and the slot or the cell. Clients interpolate positions from the state view, not from events.
 */
public sealed interface SimEvent {

    int frame();

    /** A walk started from {@code from} towards {@code to}; the target is not claimed yet. */
    record MoveStarted(int frame, int slot, CellPos from, CellPos to) implements SimEvent {
    }

    /** The mover claimed {@code cell} at its claim frame. */
    record MoveClaimed(int frame, int slot, CellPos cell) implements SimEvent {
    }

    /** A walk or lift ride turned back (claim failed, or back while moving). */
    record MoveReversed(int frame, int slot) implements SimEvent {
    }

    /** The current cell switched (walk half-way, lift arrival). */
    record CellChanged(int frame, int slot, CellPos from, CellPos to) implements SimEvent {
    }

    /** A walk ended; the mover stands in {@code cell}. */
    record MoveFinished(int frame, int slot, CellPos cell) implements SimEvent {
    }

    record Turned(int frame, int slot, Direction facing) implements SimEvent {
    }

    record LiftRideStarted(int frame, int slot, CellPos from, CellPos to) implements SimEvent {
    }

    /** A push ray started pushing the object from {@code from}; {@code to} is claimed at once. */
    record PushStarted(int frame, int slot, CellPos from, CellPos to) implements SimEvent {
    }

    /** A push ended after 16 frames; the object stands in {@code cell} and its old cell is free. */
    record PushFinished(int frame, int slot, CellPos cell) implements SimEvent {
    }

    record LiftRideFinished(int frame, int slot, CellPos cell) implements SimEvent {
    }

    /** A spider fired from {@code cell} in {@code direction}; the {@link RayFired} of its ray follows. */
    record SpiderFired(int frame, int slot, CellPos cell, Direction direction) implements SimEvent {
    }

    /**
     * A ray was traced: every cell it entered in order, with its direction and type there, including deflections
     * and conversions. It is followed by one {@link RayHit} per occupant it reached (the last one after the effect's
     * own events). A ray that ends at a box, the grid edge or its range has no last {@code RayHit}.
     */
    record RayFired(int frame, int slot, RayType type, List<RayStep> path) implements SimEvent {
        public RayFired {
            Objects.requireNonNull(type, "type");
            path = List.copyOf(path);
        }
    }

    /** The ray of {@code slot} reached the occupant {@code targetSlot}. */
    record RayHit(int frame, int slot, int targetSlot, RayOutcome outcome) implements SimEvent {
        public RayHit {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** A grab collected the klondike {@code slot}; it vanishes 7 frames later. */
    record Collected(int frame, int slot, int grabberSlot) implements SimEvent {
    }

    /** A grab exchanged the grabber with the exchanger: they swapped cells; the exchanger is destroyed. */
    record Exchanged(int frame, int grabberSlot, int exchangerSlot) implements SimEvent {
    }

    /**
     * A watcher starts a turn towards {@code toFacing}; its facing and +0E change at once. {@code frames} is 4 for a
     * quarter turn (left or right) and 8 for a half turn, which is shown counter-clockwise.
     */
    record TurnStarted(int frame, int slot, Direction toFacing, int frames) implements SimEvent {
        public TurnStarted {
            Objects.requireNonNull(toFacing, "toFacing");
        }
    }

    /** A watcher's turn ended; it re-tests straight, right and back in the same frame. */
    record TurnFinished(int frame, int slot) implements SimEvent {
    }

    /** A mover found no way: a watcher idling this frame, or a hopper starting its 16-frame wait. */
    record Idle(int frame, int slot) implements SimEvent {
    }

    /** The floor of {@code cell} was removed by the shared floor rule; the client rebuilds the floor. */
    record FloorEaten(int frame, CellPos cell) implements SimEvent {
        public FloorEaten {
            Objects.requireNonNull(cell, "cell");
        }
    }

    /** A pushed worm or watcher became permanently inert; it stays a pushable obstacle. */
    record Deactivated(int frame, int slot) implements SimEvent {
    }

    /**
     * A proximity mine or a time bomb exploded ({@code S:9682-9720}, {@code S:9742-9778}). {@code cells} are its in-grid
     * N/E/S/W neighbour cells in the original test order W, S, E, N. The {@link Destroyed} events of the neighbours it
     * hit follow, then the one of the exploding object itself.
     */
    record Exploded(int frame, int slot, List<CellPos> cells) implements SimEvent {
        public Exploded {
            cells = List.copyOf(cells);
        }
    }

    /** A time bomb's fuse passed another tenth; {@code percent} is 10..90. For audio only (Phase 4). */
    record FuseTick(int frame, int slot, int percent) implements SimEvent {
    }

    /** A grab activated the freezer {@code slot}: the freeze counter went down by one. */
    record Frozen(int frame, int slot) implements SimEvent {
    }

    /** The freeze of the freezer {@code slot} ended (timer 64) or a zap cut it short: the counter went up by one. */
    record Thawed(int frame, int slot) implements SimEvent {
    }

    /** A forcer started to move the lift whose platform is in {@code cell}: up from the bottom, or down from the top. */
    record ForcerLiftStarted(int frame, CellPos cell, boolean up) implements SimEvent {
        public ForcerLiftStarted {
            Objects.requireNonNull(cell, "cell");
        }
    }

    /** The forcer lift that started with its platform in {@code cell} arrived, 32 unfrozen frames later. */
    record ForcerLiftFinished(int frame, CellPos cell) implements SimEvent {
        public ForcerLiftFinished {
            Objects.requireNonNull(cell, "cell");
        }
    }

    /**
     * A wiper swept {@code direction}: {@code cells} are the cells it passed, from its neighbour outward until the grid
     * edge or the first blocked cell. A {@link FloorEaten} follows for every floor that went.
     */
    record WiperSweep(int frame, int slot, Direction direction, List<CellPos> cells) implements SimEvent {
        public WiperSweep {
            Objects.requireNonNull(direction, "direction");
            cells = List.copyOf(cells);
        }
    }

    /** The object starts its 32-frame destruction; it keeps its cell meanwhile. */
    record Destroyed(int frame, int slot, CellPos cell) implements SimEvent {
    }

    /** A destroyed object left the world and freed its cell. */
    record Removed(int frame, int slot, CellPos cell) implements SimEvent {
    }

    record CounterChanged(int frame, CounterKind counter, int value) implements SimEvent {
    }

    record ReadyEnded(int frame) implements SimEvent {
    }

    /** The time limit lost one second; {@code secondsLeft} is the new value. */
    record TimeTick(int frame, int secondsLeft) implements SimEvent {
    }

    /** Exit {@code code} becomes final after the countdown {@code delay}. */
    record ExitPending(int frame, int code, int delay) implements SimEvent {
    }

    record Exited(int frame, int code) implements SimEvent {
    }
}
