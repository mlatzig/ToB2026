package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;

class MoonDesignTest {

    private static final int SEEDS = 20_000;
    private static final float[] TOWARDS_SUN = {0.635f, 0.762f, 0.127f};

    private static TowerDefinition withMoon(String id, Moon moon) {
        TowerDefinition t = TestTowers.tower().id(id).level(0, "F").build();
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), moon, t.palette(), t.sounds(), t.lock(), t.grid(), t.remake());
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    @Test
    void theSeedPicksEveryLookOfTheTypeAndTheSizeClassesInTheirShares() {
        for (int type = 0; type <= 5; type++) {
            Map<MoonDesign.Look, Integer> looks = new EnumMap<>(MoonDesign.Look.class);
            Map<MoonDesign.SizeClass, Integer> sizes = new EnumMap<>(MoonDesign.SizeClass.class);
            for (int seed = 0; seed < SEEDS; seed++) {
                MoonDesign design = MoonDesign.design(type, seed, 0f, 0.2f);
                assertEquals(type, design.look().type(), "the look belongs to the original type");
                looks.merge(design.look(), 1, Integer::sum);
                sizes.merge(design.size(), 1, Integer::sum);
                if (design.look() == MoonDesign.Look.PAIR) {
                    assertNotEquals(MoonDesign.SizeClass.GIANT, design.size(), "a pair is never giant");
                }
            }
            int count = MoonDesign.Look.ofType(type).size();
            for (MoonDesign.Look look : MoonDesign.Look.ofType(type)) {
                double share = looks.getOrDefault(look, 0) / (double) SEEDS;
                assertEquals(1.0 / count, share, 0.02, "type " + type + " look " + look);
            }
            double giant = sizes.getOrDefault(MoonDesign.SizeClass.GIANT, 0) / (double) SEEDS;
            double large = sizes.getOrDefault(MoonDesign.SizeClass.LARGE, 0) / (double) SEEDS;
            double pairs = looks.getOrDefault(MoonDesign.Look.PAIR, 0) / (double) SEEDS;
            assertEquals(SkyTuning.GIANT_SHARE * (1 - pairs), giant, 0.01, "type " + type + ": about 1 in 20 giant");
            assertEquals(SkyTuning.LARGE_SHARE + SkyTuning.GIANT_SHARE * pairs, large, 0.015,
                    "type " + type + ": about 1 in 6 large");
        }
        assertEquals(MoonDesign.Look.ofType(0), MoonDesign.Look.ofType(7), "types 6 and 7 look like type 0");
    }

    @Test
    void theSameTowerAlwaysHasTheSameMoonAndNormalMoonsKeepTheirPlace() {
        Moon moon = new Moon(0, 0, 8, 28, 2);
        TowerDefinition tower = withMoon("original-000", moon);
        MoonDesign design = MoonDesign.of(tower).orElseThrow();

        assertEquals(design, MoonDesign.of(withMoon("original-000", moon)).orElseThrow(), "seeded by the tower id");
        assertEquals(Optional.empty(), MoonDesign.of(withMoon("x", new Moon(Moon.NO_MOON, 0, 8, 28, 2))));
        for (int seed = 0; seed < 200; seed++) {
            MoonDesign normal = MoonDesign.design(0, seed, 0.3f, 0.2f);
            if (normal.size() == MoonDesign.SizeClass.NORMAL) {
                assertEquals(0.3f, normal.azimuth(), 0f, "Phase 4a's place from the tower data");
                assertEquals(0.2f, normal.elevation(), 0f);
            }
        }
    }

    /**
     * Decision 14: large and giant moons stand near a compass direction and fit the level spider view, whose upper
     * edge lies 34.4° above the horizon (vertical field 1.2 rad); a giant moon's lower part sets behind the hills.
     */
    @Test
    void largeAndGiantMoonsStandNearACompassDirectionInsideTheSpiderView() {
        double top = Math.toDegrees(0.6);
        int giants = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            MoonDesign design = MoonDesign.design(seed % 6, seed, 0f, 0.2f);
            if (design.size() == MoonDesign.SizeClass.NORMAL) {
                continue;
            }
            double azimuth = Math.toDegrees(design.azimuth());
            double off = Math.abs(azimuth - 90 * Math.round(azimuth / 90));
            double half = Math.toDegrees(design.size().width()) / 2;
            double elevation = Math.toDegrees(design.elevation());
            if (design.size() == MoonDesign.SizeClass.GIANT) {
                giants++;
                assertTrue(off <= SkyTuning.GIANT_AZIMUTH_JITTER_DEGREES + 1e-3, "giant azimuth " + azimuth);
                assertTrue(elevation - half < -8, "a giant moon partly sets: bottom " + (elevation - half));
            } else {
                assertTrue(off <= SkyTuning.LARGE_AZIMUTH_JITTER_DEGREES + 1e-3, "large azimuth " + azimuth);
                assertTrue(elevation - half > 0, "a large moon stands above the horizon: " + (elevation - half));
            }
            assertTrue(elevation + half <= top + 0.5, "the top stays in the spider view: " + (elevation + half));
        }
        assertTrue(giants > SEEDS / 30, "giant moons occur: " + giants);
    }

    @Test
    void crescentsStayCrescentsAndTheLitSideFacesTheSun() {
        for (int seed = 0; seed < 600; seed++) {
            MoonDesign design = MoonDesign.design(seed % 6, seed, (float) Math.toRadians(-2.5), 0.2f);
            float share = design.litShare();
            switch (design.look()) {
                case THIN_CRESCENT, COLD_CRESCENT -> assertTrue(share < 0.35f, design.look() + " " + share);
                case WIDE_CRESCENT -> assertTrue(share < 0.5f && share > 0.35f, design.look() + " " + share);
                default -> assertTrue(share > 0.75f, design.look() + " " + share);
            }
            assertEquals(design.look() == MoonDesign.Look.WIDE_CRESCENT ? 0f : SkyTuning.EARTHSHINE,
                    design.earthshine(), 0f, "only the wider crescent has no earthshine");
            float[] light = design.light(TOWARDS_SUN);
            float[] d = design.direction();
            assertEquals(1f, (float) Math.sqrt(dot(light, light)), 1e-4f);
            assertEquals((float) Math.cos(design.phase()), -dot(light, d), 1e-4f, "the phase: light to viewer");
            float along = dot(TOWARDS_SUN, d);
            float[] side = {TOWARDS_SUN[0] - along * d[0], TOWARDS_SUN[1] - along * d[1],
                    TOWARDS_SUN[2] - along * d[2]};
            assertTrue(dot(light, side) > 0f, "the lit side faces the sun");
        }
    }

    @Test
    void theMoonTurnsSlowlyOnSimulationTime() {
        MoonDesign design = MoonDesign.design(3, 9L, 0f, 0.2f);

        assertEquals(design.spin(100f), design.spin(100f), 0f, "a pure function of the simulation time");
        float turn = SkyTuning.MOON_TURN_FRAMES;
        float step = design.spin(turn / 4) - design.spin(0f);
        assertEquals(Math.PI / 2, step < 0 ? step + 2 * Math.PI : step, 1e-3, "a quarter turn in a quarter period");
        assertEquals(design.spin(5f), design.spin(5f + turn), 1e-3f, "one turn per period");
        assertTrue(turn / SimDriver.FRAMES_PER_SECOND > 170 && turn / SimDriver.FRAMES_PER_SECOND < 190,
                "about three minutes per turn");
        float[] axis = design.axis();
        assertEquals(1f, (float) Math.sqrt(dot(axis, axis)), 1e-5f);
        assertTrue(axis[1] > 0.9f, "the axis stands roughly upright");
    }

    @Test
    void ringsAndTextureWidthsFollowTheLookAndTheSizeClass() {
        MoonDesign ringed = null;
        MoonDesign companion = null;
        for (int seed = 0; ringed == null || companion == null; seed++) {
            MoonDesign design = MoonDesign.design(seed % 2 == 0 ? 3 : 2, seed, 0f, 0.2f);
            if (design.look() == MoonDesign.Look.GAS_RINGED) {
                ringed = design;
            }
            if (design.look() == MoonDesign.Look.COMPANION && design.size() == MoonDesign.SizeClass.NORMAL) {
                companion = design;
            }
        }
        MoonDesign.Ring ring = ringed.ring().orElseThrow();
        assertTrue(ring.inner() > 1f && ring.outer() > ring.inner());
        byte[] bands = ring.texture(128);
        assertEquals(128 * 4, bands.length);
        assertTrue((bands[3] & 0xFF) < (bands[64 * 4 + 3] & 0xFF) / 3, "faded out at the inner edge");
        assertEquals(2, companion.bodies().size());
        assertEquals(1024, companion.textureWidth(companion.bodies().get(0), false));
        assertEquals(512, companion.textureWidth(companion.bodies().get(1), false), "a small companion: half");
        assertEquals(512, companion.textureWidth(companion.bodies().get(0), true), "half detail: one level smaller");
        assertEquals(4096, MoonDesign.SizeClass.GIANT.textureWidth(false));
        assertEquals(2048, MoonDesign.SizeClass.LARGE.textureWidth(false));
    }

    @Test
    void theStoredMoonSizeIsUsedWhenPresentButTheDeveloperOverrideStillWins() {
        Moon stored = new Moon(0, 0, 8, 28, 2, Optional.of(MoonDesign.SizeClass.LARGE.ordinal()));
        TowerDefinition tower = withMoon("original-000", stored);

        MoonDesign design = MoonDesign.of(tower).orElseThrow();
        assertEquals(MoonDesign.SizeClass.LARGE, design.size(), "the stored size is used");

        SkyOptions forcedGiant = new SkyOptions(false, Optional.empty(), Optional.of(MoonDesign.SizeClass.GIANT));
        MoonDesign overridden = MoonDesign.of(tower, forcedGiant).orElseThrow();
        assertEquals(MoonDesign.SizeClass.GIANT, overridden.size(), "the developer override still wins");
    }

    /**
     * Author decision A (2026-10-15, final review I2): a <em>stored</em> Large or Giant size must honour the
     * tower's Moon direction (heading) and Moon Height (the model's {@code y}) exactly as a Normal moon does,
     * instead of the seed's own quarter.
     */
    @Test
    void aStoredLargeOrGiantSizeHonoursTheMoonDirectionAndHeightLikeANormalMoon() {
        Moon east = new Moon(1, 0, 8, 10, 2, Optional.of(MoonDesign.SizeClass.GIANT.ordinal()));
        Moon south = new Moon(2, 0, 8, 10, 2, Optional.of(MoonDesign.SizeClass.GIANT.ordinal()));
        TowerDefinition eastTower = withMoon("original-000", east);
        TowerDefinition southTower = withMoon("original-000", south);

        MoonDesign eastDesign = MoonDesign.of(eastTower).orElseThrow();
        MoonDesign southDesign = MoonDesign.of(southTower).orElseThrow();
        MoonPlacement eastPlacement = MoonPlacement.of(eastTower).orElseThrow();
        MoonPlacement southPlacement = MoonPlacement.of(southTower).orElseThrow();

        assertEquals(MoonDesign.SizeClass.GIANT, eastDesign.size());
        assertEquals(eastPlacement.azimuth(), eastDesign.azimuth(), 0f, "a stored Giant follows the Moon direction");
        assertEquals(eastPlacement.elevation(), eastDesign.elevation(), 0f, "a stored Giant follows Moon Height");
        assertEquals(southPlacement.azimuth(), southDesign.azimuth(), 0f, "and so does a different direction");
        assertNotEquals(eastDesign.azimuth(), southDesign.azimuth(), "switching Moon direction moves the moon");

        Moon highLarge = new Moon(0, 0, 8, 0, 2, Optional.of(MoonDesign.SizeClass.LARGE.ordinal()));
        Moon lowLarge = new Moon(0, 0, 8, 55, 2, Optional.of(MoonDesign.SizeClass.LARGE.ordinal()));
        TowerDefinition highTower = withMoon("original-001", highLarge);
        TowerDefinition lowTower = withMoon("original-001", lowLarge);
        MoonDesign highDesign = MoonDesign.of(highTower).orElseThrow();
        MoonDesign lowDesign = MoonDesign.of(lowTower).orElseThrow();
        assertEquals(MoonPlacement.of(highTower).orElseThrow().elevation(), highDesign.elevation(), 0f);
        assertTrue(highDesign.elevation() > lowDesign.elevation(), "Moon Height 55 (y=0) stands higher than y=55");
    }

    /**
     * Author decision A, final review I2: type 2's paired look ("Companion") still never renders as Giant, and the
     * demoted Large still honours the stored placement (not the seed's quarter).
     */
    @Test
    void theType2PairLookStillDemotesAStoredGiantToLargeAndHonoursThePlacement() {
        Moon moon = new Moon(1, 2, 8, 10, 2, Optional.of(MoonDesign.SizeClass.GIANT.ordinal()));
        TowerDefinition tower = withMoon("original-000", moon);
        SkyOptions forcedPairLook = new SkyOptions(false, Optional.of(MoonDesign.Look.PAIR), Optional.empty());

        MoonDesign design = MoonDesign.of(tower, forcedPairLook).orElseThrow();

        assertEquals(MoonDesign.Look.PAIR, design.look());
        assertEquals(MoonDesign.SizeClass.LARGE, design.size(), "a pair is never giant, even stored");
        MoonPlacement placement = MoonPlacement.of(tower).orElseThrow();
        assertEquals(placement.azimuth(), design.azimuth(), 0f, "the demoted pair still honours the Moon direction");
        assertEquals(placement.elevation(), design.elevation(), 0f, "the demoted pair still honours Moon Height");
    }

    /**
     * Author decision A, final review I2: without a stored size every original tower keeps exactly the seed-chosen
     * placement it had before this fix (compared against the unchanged {@link MoonDesign#design(int, long, float,
     * float)} legacy entry point, which never honours the placement).
     */
    @Test
    void originalTowersWithoutAStoredSizeKeepTheirSeedChosenPlacementUnchanged() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");

        int checked = 0;
        for (TowerDefinition tower : TowerAssets.allTowers()) {
            assertEquals(Optional.empty(), tower.moon().size(), tower.id() + ": no original tower stores a size");
            Optional<MoonPlacement> placement = MoonPlacement.of(tower);
            Optional<MoonDesign> expected = placement.map(p -> MoonDesign.design(tower.moon().type(),
                    tower.id().hashCode(), p.azimuth(), p.elevation()));
            assertEquals(expected, MoonDesign.of(tower), tower.id() + ": unstored moon design unchanged by I2");
            checked++;
        }
        assertEquals(144, checked);
    }

    @Test
    void theDeveloperOverridesForceTheLookAndTheSizeButAPairIsNeverGiant() {
        SkyOptions options = new SkyOptions(true, Optional.of(MoonDesign.Look.GAS_RINGED),
                Optional.of(MoonDesign.SizeClass.GIANT));
        TowerDefinition tower = withMoon("original-000", new Moon(0, 0, 8, 28, 2));
        MoonDesign forced = MoonDesign.of(tower, options).orElseThrow();
        MoonDesign own = MoonDesign.of(tower).orElseThrow();

        assertTrue(options.halfMoonDetail());
        assertEquals(MoonDesign.Look.GAS_RINGED, forced.look());
        assertEquals(MoonDesign.SizeClass.GIANT, forced.size());
        assertEquals(own.spinStart(), forced.spinStart(), 0f, "the seed's draws stay the same");
        assertTrue(SkyOptions.DEFAULT.withHalfMoonDetail(true).halfMoonDetail(), "Phase 5b: the Moon detail option");
        assertEquals(SkyOptions.DEFAULT, SkyOptions.DEFAULT.withHalfMoonDetail(false));
        assertTrue(options.withHalfMoonDetail(false).halfMoonDetail(), "a test's half detail stays half");
        assertEquals(MoonDesign.SizeClass.LARGE, MoonDesign.design(2, 1L, 0f, 0f, Optional.of(MoonDesign.Look.PAIR),
                Optional.of(MoonDesign.SizeClass.GIANT)).size(), "a pair is never giant, even when forced");
    }

    /**
     * Decision 11: the largest design (a giant moon with a companion: 4096 × 2048 plus 2048 × 1024 at full detail)
     * is generated within the budget. Best of two runs (the first warms up the JIT). Decision 11's "three times the
     * measured time within the budget" is a planning rule for choosing the resolutions, checked by reading this
     * measurement (the logged line) on the author's PC; it is not a per-build gate, because the generation runs on
     * the common fork-join pool and slows down with any other load on the machine (final review, Important 1). The
     * build fails only if the budget itself is exceeded (about six times the measured time on the author's PC).
     * Tagged {@code timing}: the slower GitHub Actions runners leave it out (Phase 7d).
     */
    @Test
    @Tag("timing")
    void theGiantMoonsTexturesStayWithinTheBudget() {
        MoonDesign giant = null;
        for (int seed = 0; giant == null; seed++) {
            MoonDesign design = MoonDesign.design(2, seed, 0f, 0.2f);
            if (design.size() == MoonDesign.SizeClass.GIANT && design.bodies().size() == 2) {
                giant = design;
            }
        }
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 2; run++) {
            long start = System.nanoTime();
            for (MoonDesign.Body body : giant.bodies()) {
                int width = giant.textureWidth(body, SkyTuning.MOON_HALF_DETAIL);
                MoonTexture.generate(body.surface(), width, width / 2);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        double seconds = best / 1e9;
        System.out.printf("MoonDesignTest: giant moon textures in %.0f ms (planning rule: 3 x = %.0f ms %s the"
                + " %.0f ms budget)%n", seconds * 1000, 3 * seconds * 1000,
                3 * seconds <= SkyTuning.MOON_BUDGET_SECONDS ? "within" : "EXCEEDS",
                SkyTuning.MOON_BUDGET_SECONDS * 1000);
        assertTrue(seconds <= SkyTuning.MOON_BUDGET_SECONDS, seconds + " s within the budget");
    }
}
