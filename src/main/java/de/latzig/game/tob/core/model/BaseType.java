package de.latzig.game.tob.core.model;

/**
 * Static base element of a grid cell, with the original lift semantics (lossless).
 * An up-lift has its platform at the bottom (start) and a stop marker one level above;
 * a down-lift has its platform at the top (start) and a stop marker one level below.
 */
public enum BaseType {
    EMPTY,
    FLOOR,
    /** Solid box; acts as floor for the level above. */
    BOX,
    LIFT_UP_START,
    LIFT_DOWN_START,
    LIFT_UP_STOP,
    LIFT_DOWN_STOP
}
