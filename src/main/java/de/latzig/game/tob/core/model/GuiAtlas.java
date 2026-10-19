package de.latzig.game.tob.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** GUI atlas: image, named regions, colours (0xRRGGBB), geometry values and general settings. */
public record GuiAtlas(String image, int imageWidth, int imageHeight, Map<String, GuiRegion> regions,
        Map<String, Integer> colors, Map<String, Integer> geometry, Map<String, String> general) {

    public GuiAtlas {
        Objects.requireNonNull(image, "image");
        Checks.range("imageWidth", imageWidth, 1, 65536);
        Checks.range("imageHeight", imageHeight, 1, 65536);
        regions.forEach((name, r) -> {
            if (r.x() + r.width() > imageWidth || r.y() + r.height() > imageHeight) {
                throw new IllegalArgumentException("region " + name + " outside the image: " + r);
            }
        });
        colors.forEach((name, c) -> Checks.range("color " + name, c, 0, 0xFFFFFF));
        regions = Collections.unmodifiableMap(new TreeMap<>(regions));
        colors = Collections.unmodifiableMap(new TreeMap<>(colors));
        geometry = Collections.unmodifiableMap(new TreeMap<>(geometry));
        general = Collections.unmodifiableMap(new TreeMap<>(general));
    }
}
