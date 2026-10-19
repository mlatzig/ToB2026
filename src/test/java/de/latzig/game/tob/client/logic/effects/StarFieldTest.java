package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class StarFieldTest {

    @Test
    void theStarCountFollowsTheDensityAsToB2005DrawsIt() {
        assertEquals(0, StarField.count(0));
        assertEquals(136, StarField.count(1));
        assertEquals(268, StarField.count(2));
        assertEquals(400, StarField.count(3));
        for (int density = 0; density <= 3; density++) {
            assertEquals(StarField.count(density), StarField.generate(density, 7L).size());
        }
    }

    @Test
    void theStarsAreFixedPerSeedTintedAndSizedLikeToB2005() {
        List<StarField.Star> stars = StarField.generate(3, 42L);

        assertEquals(stars, StarField.generate(3, 42L), "same seed, same sky");
        assertNotEquals(stars, StarField.generate(3, 43L));
        long above = stars.stream().filter(StarField.Star::aboveHorizon).count();
        assertTrue(above > 200 && above < 400, "most, not all, of the plane lies above the horizon: " + above);
        for (StarField.Star star : stars) {
            assertEquals(1f, (float) Math.sqrt(star.x() * star.x() + star.y() * star.y() + star.z() * star.z()),
                    1e-4f);
            assertEquals(0xFF, star.colour() >> 16);
            assertTrue((star.colour() >> 8 & 0xFF) >= 192 && (star.colour() & 0xFF) >= 192);
            assertTrue(star.size() > 0.0005f && star.size() < 0.01f, "a few pixels: " + star.size());
        }
    }
}
