package de.latzig.game.tob.core.model;

import java.util.Objects;

/** One grid cell: a base element and an optional placed object ({@code null} = none). */
public record Cell(BaseType base, ObjectType object) {

    public static final Cell EMPTY = new Cell(BaseType.EMPTY, null);

    public Cell {
        Objects.requireNonNull(base, "base");
    }

    public boolean hasObject() {
        return object != null;
    }
}
