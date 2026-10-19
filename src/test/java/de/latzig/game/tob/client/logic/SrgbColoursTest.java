package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Phase 5e (controller gamma rule): the sRGB → linear conversion of the Classic Look's untextured colours. */
class SrgbColoursTest {

    @Test
    void zeroAndOneAreFixedPoints() {
        assertEquals(0f, SrgbColours.toLinear(0f), 0f);
        assertEquals(1f, SrgbColours.toLinear(1f), 1e-6f);
    }

    @Test
    void theStandardCurveIsLinearBelowItsKneeAndAPowerAbove() {
        assertEquals(0.04045f / 12.92f, SrgbColours.toLinear(0.04045f), 1e-7f, "the linear segment");
        assertEquals(0.21404f, SrgbColours.toLinear(0.5f), 1e-5f, "mid grey");
    }

    @Test
    void thePalettesYellowGroundBecomesItsLinearValues() {
        float[] linear = SrgbColours.rgb(0xCCAA00, true);

        assertArrayEquals(new float[] {0.60383f, 0.40198f, 0f}, linear, 1e-4f, "0xCCAA00, entry 16 of The Sacrifice");
        assertArrayEquals(new float[] {0xCC / 255f, 0xAA / 255f, 0f}, SrgbColours.rgb(0xCCAA00, false), 0f,
                "unconverted: the plain channels");
    }
}
