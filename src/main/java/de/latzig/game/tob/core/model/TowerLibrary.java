package de.latzig.game.tob.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** An ordered tower library (one JSON file). */
public record TowerLibrary(String id, String name, String source, List<TowerDefinition> towers) {

    public TowerLibrary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        towers = List.copyOf(towers);
        Set<String> ids = new HashSet<>();
        for (TowerDefinition tower : towers) {
            if (!ids.add(tower.id())) {
                throw new IllegalArgumentException("duplicate tower id " + tower.id());
            }
        }
    }
}
