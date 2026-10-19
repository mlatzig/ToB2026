package de.latzig.game.tob.core.model;

import java.util.Objects;
import java.util.Optional;

/** Immutable definition (initial state) of one tower. */
public record TowerDefinition(String id, int group, int index, String title, String author, Conditions conditions,
        Behaviour behaviour, Effects effects, Moon moon, Palette palette, Sounds sounds, Lock lock, TowerGrid grid,
        Optional<RemakeVisuals> remake) {

    public static final int MAX_TEXT_LENGTH = 62;

    public TowerDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Checks.range("group", group, 0, 999);
        Checks.range("index", index, 0, 999);
        Checks.maxLength("title", title, MAX_TEXT_LENGTH);
        Checks.maxLength("author", author, MAX_TEXT_LENGTH);
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(behaviour, "behaviour");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(moon, "moon");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(sounds, "sounds");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(remake, "remake");
    }

    public TowerDefinition withRemake(RemakeVisuals newRemake) {
        return new TowerDefinition(id, group, index, title, author, conditions, behaviour, effects, moon, palette,
                sounds, lock, grid, Optional.of(newRemake));
    }
}
