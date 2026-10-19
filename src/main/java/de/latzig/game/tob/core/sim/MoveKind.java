package de.latzig.game.tob.core.sim;

/**
 * Kind of movement (original +0E: 0–3 walk, 4–7 pushed, 8 lift up, $A lift down, ≥ $10 idle). The original keeps +0E
 * after the movement ends, so a pushed object stays {@code PUSH} while standing (a pushed worm or watcher is inert).
 * {@code IDLE} is the hopper's 16-frame wait: the movement step only counts +10 down ({@code S:10094}).
 */
public enum MoveKind {
    NONE, WALK, PUSH, LIFT_UP, LIFT_DOWN, IDLE
}
