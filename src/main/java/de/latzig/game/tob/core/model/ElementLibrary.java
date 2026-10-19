package de.latzig.game.tob.core.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/** All element definitions plus base textures (content of {@code elements.json}). */
public record ElementLibrary(Map<ObjectType, ElementDefinition> elements, Map<String, BaseTexture> base) {

    public ElementLibrary {
        // Sorted by code (not enum ordinal), matching the stable key order of elements.json.
        TreeMap<ObjectType, ElementDefinition> sorted = new TreeMap<>(Comparator.comparing(ObjectType::code));
        elements.forEach((type, definition) -> {
            if (definition.type() != type) {
                throw new IllegalArgumentException("key " + type + " does not match definition " + definition.type());
            }
            sorted.put(type, definition);
        });
        elements = Collections.unmodifiableMap(sorted);
        base = Collections.unmodifiableMap(new TreeMap<>(base));
    }
}
