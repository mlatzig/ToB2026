package de.latzig.game.tob.client.logic.effects;

import java.util.Random;

/**
 * The random numbers of the ambient effects (Phase 4b-2 spec §2 decision 5): one independent generator per effect
 * part (bolt, meteor), seeded from the tower and the part, so the same tower gives the same sequence however the
 * parts are queried. The seed and the part are spread with two large odd constants, since {@link Random} gives
 * similar first numbers for neighbouring seeds.
 */
public final class AmbientRandom {

    private AmbientRandom() {
    }

    /** The generator of part {@code part} of the effects of a tower whose seed is {@code seed}. */
    public static Random of(long seed, int part) {
        return new Random(seed * 0x9E3779B97F4A7C15L + (part + 1) * 0xBF58476D1CE4E5B9L);
    }
}
