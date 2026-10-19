package de.latzig.game.tob.core.model;

import java.util.List;

/** A group of mesh parts drawn together (ToB 2009 mesh group, e.g. 1 = north/south, 2 = east/west for spiders). */
public record MeshGroup(int id, List<MeshPart> parts) {

    public MeshGroup {
        Checks.range("group id", id, 1, 99);
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("group " + id + " has no parts");
        }
    }
}
