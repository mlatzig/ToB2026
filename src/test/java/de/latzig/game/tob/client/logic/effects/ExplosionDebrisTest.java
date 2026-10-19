package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ExplosionDebrisTest {

    private static final float EPS = 1e-5f;
    /** One horizontal triangle facing up, 0.25 fields above the origin; its centroid is (0.1, 0.25, 0.1). */
    private static final float[] TRIANGLE = {0f, 0.25f, 0f, 0f, 0.25f, 0.3f, 0.3f, 0.25f, 0f};

    private static float[] centroid(float[] p) {
        return new float[] {(p[0] + p[3] + p[6]) / 3f, (p[1] + p[4] + p[7]) / 3f, (p[2] + p[5] + p[8]) / 3f};
    }

    private static float size(float[] p) {
        float[] c = centroid(p);
        float max = 0f;
        for (int k = 0; k < 3; k++) {
            float dx = p[k * 3] - c[0];
            float dy = p[k * 3 + 1] - c[1];
            float dz = p[k * 3 + 2] - c[2];
            max = Math.max(max, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return max;
    }

    @Test
    void aTriangleStartsAtHalfSizeFliesUpAlongItsTiltedNormalAndFallsBackBelowTheFloor() {
        ExplosionDebris debris = ExplosionDebris.of(TRIANGLE, ExplosionDebris.OBJECT_MIN_HEIGHT, new Random(7));
        float[] out = new float[9];

        debris.positionsAt(0f, out);
        assertArrayEquals(new float[] {0.1f, 0.25f, 0.1f}, centroid(out), EPS, "the centroid stays at the start");
        assertEquals(size(TRIANGLE) * 0.5f, size(out), EPS, "ToB 2005 shrinks every triangle to 0.5 first");
        debris.positionsAt(4f, out);
        float early = centroid(out)[1];
        assertTrue(early > 0.25f, "the flight normal points up (flipped if needed), so it rises first: " + early);
        debris.positionsAt(ExplosionDebris.DURATION, out);
        assertFalse(debris.visible(0, ExplosionDebris.DURATION), "gravity on the normal brings it below y 0");
        float[] c = centroid(out);
        for (int k = 0; k < 3; k++) {
            assertEquals(c[1], out[k * 3 + 1], EPS, "a vanished triangle collapses to its centroid");
        }
        assertEquals(1, debris.triangles());
    }

    @Test
    void theShrinkFollowsSimulationTimeNotTheFrameRate() {
        // ToB 2005 shrank by 0.98 per rendered frame (dxe.cpp:2479-2481); here 0.98 per 1/60 s of ToB 2005 time:
        // one simulation frame = 16 ToB 2005 frames at 128 per second = 7.5 sixtieths of a second
        assertEquals(Math.pow(0.98, 7.5), ExplosionDebris.scale(1f), 1e-6);
        assertEquals(ExplosionDebris.scale(1f) * ExplosionDebris.scale(1f), ExplosionDebris.scale(2f), 1e-6);
        assertEquals(1f, ExplosionDebris.scale(0f), 0f);

        ExplosionDebris debris = ExplosionDebris.of(TRIANGLE, ExplosionDebris.OBJECT_MIN_HEIGHT, new Random(3));
        float[] direct = new float[9];
        float[] stepped = new float[9];
        debris.positionsAt(5f, direct);
        for (float t = 0f; t < 5f; t += 0.37f) {
            debris.positionsAt(t, stepped);
        }
        debris.positionsAt(5f, stepped);
        assertArrayEquals(direct, stepped, 0f, "the same time gives the same debris, however many frames drew it");
        assertEquals(size(TRIANGLE) * 0.5f * ExplosionDebris.scale(5f), size(direct), EPS);
    }

    @Test
    void floorDebrisMayFallOneLevelAndATriangleBelowTheMinimumIsGoneFromTheStart() {
        float[] low = {0f, -0.5f, 0f, 0f, -0.5f, 0.3f, 0.3f, -0.5f, 0f};
        float[] out = new float[9];

        ExplosionDebris object = ExplosionDebris.of(low, ExplosionDebris.OBJECT_MIN_HEIGHT, new Random(1));
        ExplosionDebris floor = ExplosionDebris.of(low, ExplosionDebris.FLOOR_MIN_HEIGHT, new Random(1));

        assertFalse(object.visible(0, 0f), "below an object's minimum height (0) at once");
        assertFalse(object.visible(0, 2f), "and it stays gone");
        assertTrue(floor.visible(0, 2f), "a floor's debris may fall one level (ToB 2005: -32 units)");
        object.positionsAt(1f, out);
        assertEquals(0f, size(out), EPS);
        assertEquals(-1f, ExplosionDebris.FLOOR_MIN_HEIGHT, 0f);
        assertEquals(16f, ExplosionDebris.TOB2005_FRAMES_PER_FRAME, 0f, "512 ToB 2005 frames in 32 frames");
    }
}
