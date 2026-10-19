package de.latzig.game.tob.core.sim;

import java.util.Objects;

/** One cell a ray entered, with its direction and its type there (a converter changes the type). */
public record RayStep(CellPos cell, Direction direction, RayType type) {

    public RayStep {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(type, "type");
    }
}
