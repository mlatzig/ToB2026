package de.latzig.game.tob.core.noise;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class PerlinNoiseTest {

    @Test
    void theGridIsToB2005sNoiseFromTheGenerator() {
        float[] grid = PerlinNoise.grid(new Random(7L));
        Random again = new Random(7L);

        assertEquals(PerlinNoise.GRID * PerlinNoise.GRID * PerlinNoise.OCTAVES, grid.length);
        for (float value : grid) {
            assertEquals(again.nextInt(20000) / 10000f - 1f, value, 0f, "rand() % 20000 / 10000 - 1");
        }
        assertArrayEquals(grid, PerlinNoise.grid(new Random(7L)));
    }

    @Test
    void theNoiseTilesEvery256TexelsAndTheCurveRisesFromZero() {
        float[] grid = PerlinNoise.grid(new Random(3L));
        for (int y = 0; y < 256; y += 17) {
            for (int x = 0; x < 256; x += 13) {
                assertEquals(PerlinNoise.total(grid, x, y), PerlinNoise.total(grid, x + 256, y), 0f);
                assertEquals(PerlinNoise.total(grid, x, y), PerlinNoise.total(grid, x, y + 256), 0f);
            }
        }
        assertEquals(0f, PerlinNoise.curve(0, 0.98), 0f);
        assertEquals(0f, PerlinNoise.curve(-5, 0.98), 0f, "no steps: no value");
        assertEquals(255f - 0.98f * 255f, PerlinNoise.curve(1, 0.98), 1e-4f);
        assertTrue(PerlinNoise.curve(200, 0.98) > PerlinNoise.curve(100, 0.98));
        assertTrue(PerlinNoise.curve(255, 0.98) < 255f);
    }
}
