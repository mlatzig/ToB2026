package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.noise.PerlinNoise;
import de.latzig.game.tob.core.sim.TestTowers;

class CloudsTest {

    private static final int W = PerlinNoise.GRID;
    private static final int O = PerlinNoise.OCTAVES;

    /** ToB 2005's {@code TDX_PERLINSMOOTH}, transcribed index by index ({@code tdx.h}). */
    private static float smooth(float[] n, int iX, int iY, int o) {
        float v = (n[((iY - 1 + W) % W) * W * O + (((iX - 1 + W) % W) + W * o)]
                + n[((iY - 1 + W) % W) * W * O + (((iX + 1 + W) % W) + W * o)]
                + n[((iY + 1 + W) % W) * W * O + (((iX - 1 + W) % W) + W * o)]
                + n[((iY + 1 + W) % W) * W * O + (((iX + 1 + W) % W) + W * o)]) / 16.0f;
        v += (n[((iY + W) % W) * W * O + (((iX - 1 + W) % W) + W * o)]
                + n[((iY + W) % W) * W * O + (((iX + 1 + W) % W) + W * o)]
                + n[((iY - 1 + W) % W) * W * O + (((iX + W) % W) + W * o)]
                + n[((iY + 1 + W) % W) * W * O + (((iX + W) % W) + W * o)]) / 8.0f;
        return v + n[((iY + W) % W) * W * O + (((iX + W) % W) + W * o)];
    }

    /** ToB 2005's {@code TDX_createCloudTexture} for one texel ({@code tdx.cpp:1395-1440}): grey value and alpha. */
    private static int[] tob2005(float[] n, int iX, int iY, int cover) {
        float total = 0f;
        for (int o = 0; o < O; o++) {
            float amplitude = 1f;
            for (int i = 0; i <= o; i++) {
                amplitude *= 0.5f;
            }
            int div = 1 << (O - o - 1);
            float fx = (float) (iX % div) / div;
            float fy = (float) (iY % div) / div;
            float v4 = smooth(n, iX / div, iY / div, o);
            float v5 = smooth(n, iX / div + 1, iY / div, o);
            float v6 = smooth(n, iX / div, iY / div + 1, o);
            float v7 = smooth(n, iX / div + 1, iY / div + 1, o);
            float cx = (1f - (float) StrictMath.cos(fx * (float) Math.PI)) * 0.5f;
            float cy = (1f - (float) StrictMath.cos(fy * (float) Math.PI)) * 0.5f;
            float i1 = v4 * (1f - cx) + v5 * cx;
            float i2 = v6 * (1f - cx) + v7 * cx;
            total += (i1 * (1f - cy) + i2 * cy) * amplitude;
        }
        int iv = (int) (total * 127f + 128f) - cover;
        if (iv < 0) {
            iv = 0;
        }
        float t = 1f;
        for (int i = 0; i < iv; i++) {
            t *= (float) 0.98;
        }
        t = 255f - t * 255f;
        return new int[] {(int) t, (int) ((t / 255f) * (t / 255f) * 255f)};
    }

    private static TowerDefinition withClouds(boolean clouds, boolean heavy) {
        TowerDefinition t = TestTowers.tower().level(0, "F").build();
        return t.withRemake(new RemakeVisuals(0xFFFFFF, 3, 0, 0, 4, 0xC0C0C0, clouds, heavy, 0, 0, 0));
    }

    private static double clear(byte[] pixels) {
        int clear = 0;
        for (int i = 3; i < pixels.length; i += 4) {
            if (pixels[i] == 0) {
                clear++;
            }
        }
        return clear / (pixels.length / 4.0);
    }

    private static double meanAlpha(byte[] pixels) {
        long sum = 0;
        for (int i = 3; i < pixels.length; i += 4) {
            sum += pixels[i] & 0xFF;
        }
        return sum / (pixels.length / 4.0);
    }

    @Test
    void theCloudTextureIsToB2005sFormulaAndDeterministicPerTower() {
        byte[] light = CloudTexture.generate(42L, SkyTuning.CLOUD_COVER);
        float[] noise = PerlinNoise.grid(AmbientRandom.of(42L, CloudTexture.RANDOM_PART));

        assertEquals(256 * 256 * 4, light.length);
        assertArrayEquals(light, CloudTexture.generate(42L, SkyTuning.CLOUD_COVER), "the same tower, the same sky");
        assertFalse(Arrays.equals(light, CloudTexture.generate(43L, SkyTuning.CLOUD_COVER)), "each tower its own");
        for (int[] texel : new int[][] {{0, 0}, {255, 255}, {17, 200}, {128, 64}, {3, 129}, {250, 7}}) {
            int i = (texel[1] * 256 + texel[0]) * 4;
            int[] expected = tob2005(noise, texel[0], texel[1], SkyTuning.CLOUD_COVER);
            assertEquals(expected[0], light[i] & 0xFF, "grey at " + Arrays.toString(texel));
            assertEquals(expected[0], light[i + 2] & 0xFF);
            assertEquals(expected[1], light[i + 3] & 0xFF, "alpha at " + Arrays.toString(texel));
        }
    }

    @Test
    void heavyCloudsCoverMoreOfTheSkyAndWinOverLightClouds() {
        byte[] light = CloudTexture.generate(5L, SkyTuning.CLOUD_COVER);
        byte[] heavy = CloudTexture.generate(5L, SkyTuning.HEAVY_CLOUD_COVER);

        assertTrue(clear(light) > 0.1, "light clouds leave clear sky: " + clear(light));
        assertTrue(clear(heavy) < clear(light) / 4, "heavy clouds: " + clear(heavy));
        assertTrue(meanAlpha(heavy) > meanAlpha(light) * 2, meanAlpha(heavy) + " vs " + meanAlpha(light));
        assertEquals(OptionalInt.of(SkyTuning.CLOUD_COVER), CloudTexture.cover(withClouds(true, false)));
        assertEquals(OptionalInt.of(SkyTuning.HEAVY_CLOUD_COVER), CloudTexture.cover(withClouds(true, true)),
                "heavyClouds wins");
        assertEquals(OptionalInt.empty(), CloudTexture.cover(withClouds(false, false)));
        TowerDefinition plain = TestTowers.tower().level(0, "F").build();
        assertEquals(Optional.empty(), plain.remake());
        assertEquals(OptionalInt.empty(), CloudTexture.cover(plain), "no ToB 2009 look settings: no clouds");
    }

    @Test
    void theCloudsDriftOnSimulationTimeTwiceAsFastWhenHeavy() {
        CloudDrift light = CloudDrift.of(9L, SkyTuning.CLOUD_COVER);
        CloudDrift heavy = CloudDrift.of(9L, SkyTuning.HEAVY_CLOUD_COVER);

        assertEquals(16f / 8192f, light.speed(), 1e-9f, "16 ToB 2005 frames per simulation frame / 8192");
        assertEquals(2 * light.speed(), heavy.speed(), 1e-9f);
        assertEquals(1f, light.u() * light.u() + light.v() * light.v(), 1e-5f);
        assertEquals(light.u(), heavy.u(), 0f, "the same direction");
        assertEquals(light.offsetU(37.5f), light.offsetU(37.5f), 0f, "stands still while the time stands still");
        assertEquals(light.u() * light.speed() * 10f, light.offsetU(110f) - light.offsetU(100f), 1e-4f);
        for (float now = 0f; now < 20_000f; now += 123.4f) {
            assertTrue(light.offsetU(now) >= 0f && light.offsetU(now) < 1f);
            assertTrue(heavy.offsetV(now) >= 0f && heavy.offsetV(now) < 1f);
        }
        assertFalse(CloudDrift.of(9L, 80).equals(CloudDrift.of(10L, 80)), "the seed draws the direction");
    }

    @Test
    void thePlaneIsToB2005sAtSixtyPercentOfTheSkyHeightAndFadesOut() {
        MeshArrays plane = CloudPlane.arrays();

        assertEquals(CloudPlane.RINGS * CloudPlane.SEGMENTS * 2, plane.triangles());
        assertEquals(0.6 * (0.8 * 8192 / 1.8 - 1800), CloudPlane.height(0), 1e-6, "0.6 × the sky's height");
        assertEquals(192f / 255f, CloudPlane.alpha(0), 1e-6f);
        assertEquals(0f, CloudPlane.alpha(CloudPlane.RADIUS), 0f);
        assertEquals(96f / 255f, CloudPlane.alpha(2048), 1e-6f);
        float maxAlpha = 0f;
        for (int i = 3; i < plane.colors().length; i += 4) {
            maxAlpha = Math.max(maxAlpha, plane.colors()[i]);
        }
        assertEquals(192f / 255f, maxAlpha, 1e-6f);
        assertEquals(34.5f, plane.maxY(), 0.05f, "1104.6 units above the camera");
        double perField = SkyTuning.CLOUD_TILES / (2 * Math.sqrt(2) * 8192 / 1.8) * 32;
        int t = CloudPlane.SEGMENTS * 2;
        float du = plane.uvs()[t * 6 + 2] - plane.uvs()[t * 6];
        float dx = plane.positions()[t * 9 + 3] - plane.positions()[t * 9];
        assertEquals(perField, du / dx, 1e-4, "the texture repeats 6 times across the sky plane");
    }
}
