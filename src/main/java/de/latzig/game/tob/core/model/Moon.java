package de.latzig.game.tob.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Moon settings as raw original values; heading 4 means "no moon". {@code size} is a new optional field (Phase 6b-2
 * Task 0): absent (the original towers and every tower converted so far) means the size class is still drawn from
 * the tower's seed ({@code client.logic.effects.MoonDesign}); present (0-{@value #MAX_SIZE}, the client's size classes
 * Normal/Large/Giant) is used instead. Omitted from the JSON when absent, so the original towers' files and
 * checksums stay unchanged.
 */
public record Moon(int heading, int type, int x, int y, int rotation, Optional<Integer> size) {

    public static final int NO_MOON = 4;

    /** The highest storable value of {@link #size} (the client's size classes Normal 0, Large 1, Giant 2). */
    public static final int MAX_SIZE = 2;

    public Moon {
        Checks.range("moon.heading", heading, 0, 7);
        Checks.range("moon.type", type, 0, 7);
        Checks.range("moon.x", x, 0, 511);
        Checks.range("moon.y", y, 0, 127);
        Checks.range("moon.rotation", rotation, 0, 3);
        Objects.requireNonNull(size, "moon.size");
        size.ifPresent(s -> Checks.range("moon.size", s, 0, MAX_SIZE));
    }

    /** As the canonical constructor, with no stored size (today's seed-derived size class). */
    public Moon(int heading, int type, int x, int y, int rotation) {
        this(heading, type, x, y, rotation, Optional.empty());
    }

    public boolean present() {
        return heading != NO_MOON;
    }
}
