package de.latzig.game.tob.core.sim;

/** Life cycle of an object slot (original class word +00). */
public enum ObjectStatus {
    /** Active: its class handler runs. */
    ALIVE,
    /** Being destroyed (class $1A): still holds its cell for 32 frames ({@code S:9945-9955}). */
    DESTROYING,
    /** Removed (class $3F): a dead list entry that keeps its slot. */
    REMOVED,
    /** A spider slot whose spider is not in the tower (class $3F at load, {@code S:17527-17529}). */
    ABSENT
}
