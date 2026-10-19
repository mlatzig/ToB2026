package de.latzig.game.tob.client.logic.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * One tower of the catalogue ({@link PickerModel}; Phase 3a's tower picker, removed in Phase 5a): the label, the
 * 1-based number of the tower in its library (shown in the title bar), the tower if it could be read, and its
 * problems (read errors and {@code TowerCheck} ERROR findings). Only a tower without problems can be played.
 */
public record TowerEntry(String label, int number, Optional<TowerDefinition> tower, List<String> problems) {

    public TowerEntry {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(tower, "tower");
        problems = List.copyOf(problems);
    }

    public boolean playable() {
        return tower.isPresent() && problems.isEmpty();
    }
}
