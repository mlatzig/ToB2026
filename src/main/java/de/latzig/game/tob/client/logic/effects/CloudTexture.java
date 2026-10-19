package de.latzig.game.tob.client.logic.effects;

import java.util.OptionalInt;

import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.noise.PerlinNoise;

/**
 * ToB 2005's cloud texture (Phase 4b-3 spec §2 decisions 3 and 4, {@code TDX_createCloudTexture},
 * {@code tdx.cpp:1364-1450}): {@value #SIZE}² texels of ToB 2005's Perlin noise ({@link PerlinNoise}), the exponential
 * curve v = 255 − 0.98<sup>max(0, ⌊t · 127 + 128⌋ − cover)</sup> · 255, the colour (v, v, v) and the alpha
 * (v / 255)² · 255. Cover {@link SkyTuning#CLOUD_COVER} for {@code clouds} towers, {@link SkyTuning#HEAVY_CLOUD_COVER}
 * for {@code heavyClouds} (it wins if both are set, as in ToB 2005's {@code _TWR_initSky}). Generated at every tower
 * start from the tower seed ({@link AmbientRandom} part {@value #RANDOM_PART}): the same tower always has the same
 * sky, each tower its own. Rows in ToB 2005's order (row 0 = its first row); the texture tiles in both directions.
 */
public final class CloudTexture {

    /** The part of {@link AmbientRandom} that draws the cloud noise. */
    public static final int RANDOM_PART = 200;
    public static final int SIZE = PerlinNoise.SIZE;

    private CloudTexture() {
    }

    /** The cloud cover of {@code tower}, or empty without clouds (only ToB 2009 look settings have clouds). */
    public static OptionalInt cover(TowerDefinition tower) {
        if (tower.remake().isEmpty()) {
            return OptionalInt.empty();
        }
        RemakeVisuals remake = tower.remake().get();
        if (remake.heavyClouds()) {
            return OptionalInt.of(SkyTuning.HEAVY_CLOUD_COVER);
        }
        return remake.clouds() ? OptionalInt.of(SkyTuning.CLOUD_COVER) : OptionalInt.empty();
    }

    /** The texture for the tower seed {@code seed} and {@code cover}: {@value #SIZE}² RGBA8 texels, row by row. */
    public static byte[] generate(long seed, int cover) {
        float[] noise = PerlinNoise.grid(AmbientRandom.of(seed, RANDOM_PART));
        byte[] pixels = new byte[SIZE * SIZE * 4];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int steps = Math.max(0, (int) (PerlinNoise.total(noise, x, y) * 127f + 128f) - cover);
                float value = PerlinNoise.curve(steps, SkyTuning.CLOUD_SHARPNESS);
                int i = (y * SIZE + x) * 4;
                byte grey = (byte) (int) value;
                pixels[i] = grey;
                pixels[i + 1] = grey;
                pixels[i + 2] = grey;
                pixels[i + 3] = (byte) (int) ((value / 255f) * (value / 255f) * 255f);
            }
        }
        return pixels;
    }
}
