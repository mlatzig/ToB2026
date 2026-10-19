package de.latzig.game.tob.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Named sub-images (sprites) and frame grids (sequences) of the FX atlas image ({@code fx/fx-atlas.json}). */
public record FxAtlas(String image, int imageWidth, int imageHeight, Map<String, FxSprite> sprites,
        Map<String, FxSequence> sequences, String source) {

    public FxAtlas {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(source, "source");
        Checks.range("imageWidth", imageWidth, 1, 65536);
        Checks.range("imageHeight", imageHeight, 1, 65536);
        sprites.forEach((name, s) -> {
            if (!inside(s.x(), s.y(), s.width(), s.height(), imageWidth, imageHeight)) {
                throw new IllegalArgumentException("sprite " + name + " outside the image: " + s);
            }
        });
        sequences.forEach((name, q) -> {
            if (!inside(q.x(), q.y(), q.width(), q.height(), imageWidth, imageHeight)) {
                throw new IllegalArgumentException("sequence " + name + " outside the image: " + q);
            }
        });
        sprites = Collections.unmodifiableMap(new TreeMap<>(sprites));
        sequences = Collections.unmodifiableMap(new TreeMap<>(sequences));
    }

    public static boolean inside(int x, int y, int width, int height, int imageWidth, int imageHeight) {
        return x >= 0 && y >= 0 && (long) x + width <= imageWidth && (long) y + height <= imageHeight;
    }
}
