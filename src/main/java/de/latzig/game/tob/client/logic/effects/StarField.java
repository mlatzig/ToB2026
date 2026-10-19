package de.latzig.game.tob.client.logic.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The stars of a tower's sky (Phase 4a spec §2 decision 4), as ToB 2005 places them ({@code sky.cpp:312-330},
 * {@code bpe.cpp:456-476}): {@code stars × 4} sprites at random points of the curved sky plane, tinted
 * (255, 192–255, 192–255) and 5–14 units large. The original's star density 0–3 becomes ToB 2005/2009's star value
 * 0/34/67/100 (report 07 §6). Random but fixed per tower: the seed is the tower id's hash. Stars below the horizon
 * (the corners of the 2005 plane) are kept and hidden by the view.
 */
public final class StarField {

    /** ToB 2009 {@code fx.stars} per original star density (report 07 §6: 0/34/67/100). */
    static final int[] STARS_2009 = {0, 34, 67, 100};
    /** ToB 2005 draws four sprites per star value ({@code g_iStars * 4}). */
    static final int SPRITES_PER_STAR = 4;
    /** {@code SKY_DIVISIONS * 100}: the 2005 random grid across the sky plane. */
    private static final int STEPS = 800;

    /**
     * One star: the unit direction from the camera ({@code y} up, the scene's x east and z south), its angular size in
     * radians and its colour 0xRRGGBB.
     */
    public record Star(float x, float y, float z, float size, int colour) {

        public boolean aboveHorizon() {
            return y > 0f;
        }
    }

    private StarField() {
    }

    /** The number of star sprites for a star density 0–3. */
    public static int count(int starDensity) {
        return STARS_2009[starDensity] * SPRITES_PER_STAR;
    }

    public static List<Star> of(TowerDefinition tower) {
        return generate(tower.effects().starDensity(), tower.id().hashCode());
    }

    static List<Star> generate(int starDensity, long seed) {
        Random random = new Random(seed);
        double a = SkyDome.ATMOSPHERE;
        double half = SkyDome.PLANE_HALF;
        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < count(starDensity); i++) {
            double x = -half + random.nextInt(STEPS) * 2 * half / STEPS;
            double z = -half + random.nextInt(STEPS) * 2 * half / STEPS;
            double y = (4 * a - (x * x + z * z) / a) * 0.2 - 1800;
            double distance = 0.96 * Math.sqrt(x * x + y * y + z * z);
            int colour = 0xFF0000 | (192 + random.nextInt(64)) << 8 | (192 + random.nextInt(64));
            double size = 5 + random.nextInt(10);
            stars.add(new Star((float) (x * 0.96 / distance), (float) (y * 0.96 / distance),
                    (float) (z * 0.96 / distance), (float) (size / distance), colour));
        }
        return stars;
    }
}
