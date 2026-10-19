package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class MoonPlacementTest {

    private static final float EPS = 1e-4f;

    private static TowerDefinition withMoon(Moon moon) {
        TowerDefinition t = TestTowers.tower().level(0, "F").build();
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), moon, t.palette(), t.sounds(), t.lock(), t.grid(), t.remake());
    }

    private static float degrees(float radians) {
        return (float) Math.toDegrees(radians);
    }

    @Test
    void heading4MeansNoMoon() {
        assertEquals(Optional.empty(), MoonPlacement.of(withMoon(new Moon(Moon.NO_MOON, 0, 8, 28, 1))));
        assertEquals(Optional.empty(), MoonPlacement.of(withMoon(new Moon(5, 0, 8, 28, 1))), "never shown");
    }

    @Test
    void theMoonOfSoftlySoftlyStandsAlmostDueNorthAboutTwelveDegreesHigh() {
        // original-000: heading 0 (north), type 0, X 8, Y 28, rotation 2 (no effect since the acceptance)
        MoonPlacement moon = MoonPlacement.of(withMoon(new Moon(0, 0, 8, 28, 2))).orElseThrow();

        assertEquals(-2.5f, degrees(moon.azimuth()), EPS, "centre x 152 is 8 pixels left of the screen centre");
        assertEquals(11.875f, degrees(moon.elevation()), EPS, "centre line 52 is 19 lines above the horizon");
    }

    @Test
    void headingRotationAndTheClampsOfTheOriginalLoader() {
        MoonPlacement east = MoonPlacement.of(withMoon(new Moon(1, 3, 128, 56, 0))).orElseThrow();

        assertEquals(90f + (16 + 16 * 17 + 8 - 160) * MoonPlacement.DEGREES_PER_PIXEL, degrees(east.azimuth()), EPS,
                "X is clamped to 17");
        assertEquals(-5f, degrees(east.elevation()), EPS, "Y is clamped to 55: the moon has set");
        assertEquals(east, MoonPlacement.of(withMoon(new Moon(1, 3, 128, 56, 2))).orElseThrow(),
                "acceptance 2026-10-10: the moon does not rotate, the rotation byte has no effect");
        assertEquals(270f - 136f * MoonPlacement.DEGREES_PER_PIXEL,
                degrees(MoonPlacement.of(withMoon(new Moon(3, 0, 0, 0, 1))).orElseThrow().azimuth()), EPS);
    }
}
