package de.latzig.game.tob.client.logic.effects;

import java.util.Random;

/**
 * The clouds' drift (Phase 4b-3 spec §2 decision 3, {@code sky.cpp:358-365, 761-800}): the texture moves in a direction
 * drawn from the tower seed ({@link AmbientRandom} part {@value #RANDOM_PART}; ToB 2005's {@code rand() % 6283 / 1000}
 * radians) by {@link #TOB2005_FRAMES} / {@link SkyTuning#CLOUD_FRAME_SPEED} texture repeats per simulation frame,
 * twice as fast for heavy cover (below 50, ToB 2005's {@code fSpeedDouble}). A pure function of the simulation time,
 * so the clouds stand still while paused and keep pace with fast-forward.
 *
 * @param u     the drift direction's share along the texture's u
 * @param v     the drift direction's share along the texture's v
 * @param speed texture repeats per simulation frame
 */
public record CloudDrift(float u, float v, float speed) {

    /** The part of {@link AmbientRandom} that draws the drift direction. */
    public static final int RANDOM_PART = 201;
    /** ToB 2005 frames per simulation frame (Phase 4b-1's effect clock). */
    public static final float TOB2005_FRAMES = ExplosionDebris.TOB2005_FRAMES_PER_FRAME;

    /** The drift of the tower seed {@code seed} for {@code cover}. */
    public static CloudDrift of(long seed, int cover) {
        Random random = AmbientRandom.of(seed, RANDOM_PART);
        double angle = random.nextInt(6283) / 1000.0;
        float speed = (float) (TOB2005_FRAMES * (cover < 50 ? 2 : 1) / SkyTuning.CLOUD_FRAME_SPEED);
        return new CloudDrift((float) Math.sin(angle), (float) Math.cos(angle), speed);
    }

    /** The texture offset along u at simulation time {@code now}, in [0, 1). */
    public float offsetU(float now) {
        return fraction(u * speed * (double) now);
    }

    /** The texture offset along v at simulation time {@code now}, in [0, 1). */
    public float offsetV(float now) {
        return fraction(v * speed * (double) now);
    }

    private static float fraction(double value) {
        return (float) (value - Math.floor(value));
    }
}
