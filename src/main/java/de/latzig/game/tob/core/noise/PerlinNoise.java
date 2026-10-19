package de.latzig.game.tob.core.noise;

import java.util.Random;

/**
 * ToB 2005's tiled 2D Perlin noise ({@code tdx.cpp:1364-1530}, {@code tdx.h:24-49}), shared by the haze
 * ({@code TDX_createWaterTexture}) and the clouds ({@code TDX_createCloudTexture}): {@value #OCTAVES} octaves
 * (persistence 0.5) over a {@value #GRID} × {@value #GRID} noise array per octave (values −1..1, ToB 2005's
 * {@code rand() % 20000 / 10000 − 1}), smoothed with {@code TDX_PERLINSMOOTH} (corners / 16, edges / 8, the centre in
 * full) and cosine-interpolated, for a {@value #SIZE} × {@value #SIZE} texture; and ToB 2005's exponential curve
 * 255 − sharpness<sup>steps</sup> · 255. Float arithmetic in ToB 2005's order, {@link StrictMath} for the cosine, so
 * the same generator gives the same pixels everywhere.
 */
public final class PerlinNoise {

    public static final int SIZE = 256;
    public static final int OCTAVES = 4;
    public static final int GRID = 32;
    private static final double PERSISTENCE = 0.5;

    private PerlinNoise() {
    }

    /** ToB 2005's noise arrays, {@code GRID × GRID × OCTAVES} values drawn from {@code random}. */
    public static float[] grid(Random random) {
        float[] noise = new float[GRID * GRID * OCTAVES];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = random.nextInt(20000) / 10000f - 1f;
        }
        return noise;
    }

    /** The fractal sum at texel ({@code x}, {@code y}) of the {@value #SIZE}² texture (ToB 2005's {@code fTotal}). */
    public static float total(float[] noise, int x, int y) {
        float total = 0f;
        for (int octave = 0; octave < OCTAVES; octave++) {
            float amplitude = (float) StrictMath.pow(PERSISTENCE, octave + 1);
            int div = 1 << (OCTAVES - octave - 1);
            float fx = (float) (x % div) / div;
            float fy = (float) (y % div) / div;
            float v00 = smooth(noise, x / div, y / div, octave);
            float v10 = smooth(noise, x / div + 1, y / div, octave);
            float v01 = smooth(noise, x / div, y / div + 1, octave);
            float v11 = smooth(noise, x / div + 1, y / div + 1, octave);
            float wx = (1f - (float) StrictMath.cos(fx * (float) Math.PI)) * 0.5f;
            float wy = (1f - (float) StrictMath.cos(fy * (float) Math.PI)) * 0.5f;
            float top = v00 * (1f - wx) + v10 * wx;
            float bottom = v01 * (1f - wx) + v11 * wx;
            total += (top * (1f - wy) + bottom * wy) * amplitude;
        }
        return total;
    }

    /** ToB 2005's exponential curve: 255 − {@code sharpness}<sup>{@code steps}</sup> · 255 (no steps: 0). */
    public static float curve(int steps, double sharpness) {
        float fade = 1f;
        for (int i = 0; i < steps; i++) {
            fade *= (float) sharpness;
        }
        return 255f - fade * 255f;
    }

    /** ToB 2005's {@code TDX_PERLINSMOOTH}: the noise at (x, y), wrapped, with its 8 neighbours. */
    private static float smooth(float[] noise, int x, int y, int octave) {
        float corners = at(noise, x - 1, y - 1, octave) + at(noise, x + 1, y - 1, octave)
                + at(noise, x - 1, y + 1, octave) + at(noise, x + 1, y + 1, octave);
        float edges = at(noise, x - 1, y, octave) + at(noise, x + 1, y, octave) + at(noise, x, y - 1, octave)
                + at(noise, x, y + 1, octave);
        return corners / 16f + edges / 8f + at(noise, x, y, octave);
    }

    private static float at(float[] noise, int x, int y, int octave) {
        int row = Math.floorMod(y, GRID);
        int column = Math.floorMod(x, GRID);
        return noise[row * GRID * OCTAVES + column + GRID * octave];
    }
}
