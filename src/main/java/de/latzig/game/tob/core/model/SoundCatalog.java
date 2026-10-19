package de.latzig.game.tob.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** All sounds by 4-digit id (content of {@code sounds/sounds.json}). */
public record SoundCatalog(Map<String, SoundEntry> sounds) {

    public SoundCatalog {
        sounds.keySet().forEach(id -> {
            if (!id.matches("\\d{4}")) {
                throw new IllegalArgumentException("sound id must have 4 digits: " + id);
            }
        });
        sounds = Collections.unmodifiableMap(new TreeMap<>(sounds));
    }
}
