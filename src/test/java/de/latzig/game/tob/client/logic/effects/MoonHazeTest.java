package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.TowerColours;

/** Acceptance change "moon haze" (Phase 6b-2): the moon takes on the sky's colour by its elevation. */
class MoonHazeTest {

    private static final TowerColours COLOURS = new TowerColours(0x0088CC, 0x102030, 0x005555, 0, 0, 0, 0, 0);

    private static double degrees(double value) {
        return Math.toRadians(value);
    }

    @Test
    void theHazeIsStrongAtTheHorizonAndWeakHighInTheSky() {
        assertEquals(SkyTuning.MOON_HAZE_HORIZON, MoonHaze.strength(0.0), 1e-6f);
        assertEquals(SkyTuning.MOON_HAZE_HORIZON, MoonHaze.strength(degrees(-15)), 1e-6f, "below the horizon too");
        assertEquals(SkyTuning.MOON_HAZE_HIGH, MoonHaze.strength(degrees(SkyTuning.MOON_HAZE_FADE_DEGREES)), 1e-6f);
        assertEquals(SkyTuning.MOON_HAZE_HIGH, MoonHaze.strength(degrees(80)), 1e-6f);
        assertEquals((SkyTuning.MOON_HAZE_HIGH + SkyTuning.MOON_HAZE_HORIZON) / 2,
                MoonHaze.strength(degrees(SkyTuning.MOON_HAZE_FADE_DEGREES / 2)), 1e-6f, "halfway");
        float last = Float.POSITIVE_INFINITY;
        for (int e = -10; e <= 90; e++) {
            float strength = MoonHaze.strength(degrees(e));
            assertTrue(strength <= last, "never stronger higher up: " + e);
            last = strength;
        }
    }

    @Test
    void theTunedStrengthsKeepTheMoonRecognisable() {
        assertEquals(0.25f, SkyTuning.MOON_HAZE_HIGH, 0.1f);
        assertEquals(0.6f, SkyTuning.MOON_HAZE_HORIZON, 0.1f);
        assertTrue(SkyTuning.MOON_HAZE_HIGH < SkyTuning.MOON_HAZE_HORIZON && SkyTuning.MOON_HAZE_HORIZON < 1f);
    }

    @Test
    void eachVertexTakesTheDomesColourAndTextureSpotInItsDirectionWithItsElevationsStrength() {
        // A vertex straight up, one on the horizon, one at 10 degrees (a giant moon's centre), at any distance.
        float high = (float) Math.sin(degrees(10));
        float wide = (float) Math.cos(degrees(10));
        float[] positions = {0f, 50f, 0f, 3f, 0f, -4f, 0f, high * 120f, -wide * 120f};

        MoonHaze.Vertices haze = MoonHaze.vertices(positions, COLOURS);

        assertEquals(6, haze.uvs().length);
        assertEquals(12, haze.colors().length);
        for (int v = 0; v < 3; v++) {
            float x = positions[v * 3];
            float y = positions[v * 3 + 1];
            float z = positions[v * 3 + 2];
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            float[] look = SkyDome.look(COLOURS, x / length, y / length, z / length);
            assertArrayEquals(new float[] {look[0], look[1]}, new float[] {haze.uvs()[v * 2], haze.uvs()[v * 2 + 1]},
                    1e-6f, "the sky texture spot behind the vertex");
            assertArrayEquals(new float[] {look[2], look[3], look[4]}, new float[] {haze.colors()[v * 4],
                    haze.colors()[v * 4 + 1], haze.colors()[v * 4 + 2]}, 1e-6f, "the sky colour behind the vertex");
            assertEquals(MoonHaze.strength(Math.asin(y / length)), haze.colors()[v * 4 + 3], 1e-6f, "the strength");
        }
        assertEquals((COLOURS.skyTop() >> 16 & 0xFF) / 255f, haze.colors()[0], 1e-6f, "the zenith: the top colour");
        assertEquals((COLOURS.skyHorizon() & 0xFF) / 255f, haze.colors()[6], 1e-6f, "the horizon colour");
        assertEquals(SkyTuning.MOON_HAZE_HIGH, haze.colors()[3], 1e-6f);
        assertEquals(SkyTuning.MOON_HAZE_HORIZON, haze.colors()[7], 1e-6f);
    }

    @Test
    void theRingTakesTheHazeOfTheMoonsCentre() {
        MoonDesign.Ring ring = new MoonDesign.Ring(1.3f, 2.1f, 0xFFFFFF);
        double elevation = degrees(10);

        MoonDesign.Ring hazed = MoonHaze.ring(ring, COLOURS, elevation);

        assertEquals(ring.inner(), hazed.inner(), 0f);
        assertEquals(ring.outer(), hazed.outer(), 0f);
        int sky = TowerColours.mix(COLOURS.skyTop(), COLOURS.skyHorizon(), SkyDome.horizonMix(elevation));
        assertEquals(TowerColours.mix(0xFFFFFF, sky, MoonHaze.strength(elevation)), hazed.colour());
    }
}
