package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ExplosionSpritesTest {

    private static long count(List<ExplosionSprites.Sprite> sprites, String sequence) {
        return sprites.stream().filter(s -> s.sequence().equals(sequence)).count();
    }

    private static ExplosionSprites.Sprite first(List<ExplosionSprites.Sprite> sprites, String sequence) {
        return sprites.stream().filter(s -> s.sequence().equals(sequence)).findFirst().orElseThrow();
    }

    @Test
    void nineSparksAFireballAndTwoSmokePuffsPlayOverTheExplosion() {
        ExplosionSprites sprites = ExplosionSprites.start(4f, 1.25f, 7f, new Random(11));

        List<ExplosionSprites.Sprite> start = sprites.at(0f);
        assertEquals(12, start.size());
        assertEquals(9, count(start, ExplosionSprites.SPARKS));
        assertEquals(1, count(start, ExplosionSprites.FIREBALL));
        assertEquals(2, count(start, ExplosionSprites.SMOKE));
        ExplosionSprites.Sprite fireball = first(start, ExplosionSprites.FIREBALL);
        assertEquals(7f / 32f, fireball.halfSize(), 1e-6f, "ToB 2005 size 7 (a half size)");
        assertEquals(4f, fireball.x(), 0f);
        assertEquals(1.25f, fireball.y(), 0f);
        assertEquals(0, fireball.frame());
        for (ExplosionSprites.Sprite spark : start) {
            if (spark.sequence().equals(ExplosionSprites.SPARKS)) {
                // Author decision (2026-10-10, second round, after a live check): size reverted to ToB 2005's own
                // 1.0-1.3 (SPARK_SIZE_FACTOR back to 1x) - enlarging them did not help, the explosion cloud hid them
                // regardless of their own size. The lifetime stretch (SPARK_LIFETIME_FACTOR) stays.
                assertTrue(spark.halfSize() >= 1f / 32f && spark.halfSize() <= 1.3f / 32f,
                        "spark size 1.0-1.3 (ToB 2005's own value: the author reverted the size-up)");
            }
        }

        // 5 simulation frames = 80 ToB 2005 frames = sprite frame 16 (one every 5 ToB 2005 frames)
        List<ExplosionSprites.Sprite> mid = sprites.at(5f);
        assertEquals(16, first(mid, ExplosionSprites.FIREBALL).frame());
        assertEquals(16, first(mid, ExplosionSprites.SMOKE).frame());
        assertEquals(8, first(mid, ExplosionSprites.SPARKS).frame(), "sparks step every second sprite frame");
        assertEquals(4f, first(mid, ExplosionSprites.FIREBALL).x(), 0f, "the fireball does not move");
        assertTrue(first(mid, ExplosionSprites.SMOKE).y() >= 1.25f, "smoke drifts up or level, without gravity");

        assertEquals(2, count(sprites.at(7.4f), ExplosionSprites.SMOKE), "smoke: 24 sprite frames = 7.5 frames");
        assertEquals(0, count(sprites.at(7.6f), ExplosionSprites.SMOKE));
        assertEquals(1, count(sprites.at(9.9f), ExplosionSprites.FIREBALL), "fireball: 32 sprite frames = 10 frames");
        assertEquals(0, count(sprites.at(10f), ExplosionSprites.FIREBALL), "fireball unchanged: still 10 frames");
        assertEquals(0, count(sprites.at(10f), ExplosionSprites.SMOKE), "smoke unchanged: still 10 frames");
        assertEquals(List.of(), sprites.at(ExplosionDebris.DURATION), "nothing outlasts the 32-frame explosion");
        assertEquals(sprites.at(3.3f), sprites.at(3.3f), "a pure function of the time");
    }

    @Test
    void sparksStayVisibleLongerThanToB2005AtTheirOriginalSizeButEndWithinTheExplosion() {
        // ToB 2005's sparks faded out within about 8-10 simulation frames (size 1.0-1.3 over the 0.008 per ToB 2005
        // frame shrink); barely noticeable next to the much bigger fireball (half size 7) and smoke (5-9). The
        // author's first-round 2026-10-10 decision enlarged them (x2.5), but a live check found the explosion cloud
        // hid them regardless of their own size; the second-round decision reverted the size to ToB 2005's own
        // 1.0-1.3 (SPARK_SIZE_FACTOR = 1x) and kept only the longer fade (SPARK_LIFETIME_FACTOR = 1.75x, so about
        // 13.7-17.8 simulation frames), still well inside the 32-frame explosion (ExplosionDebris.DURATION).
        ExplosionSprites sprites = ExplosionSprites.start(0f, 0f, 0f, new Random(11));

        // Still visible well past ToB 2005's own fade-out point (about 8-10 frames), proving the lifetime was
        // actually extended and not just the size.
        assertTrue(count(sprites.at(12f), ExplosionSprites.SPARKS) > 0,
                "sparks outlast ToB 2005's original ~8-10 frame fade-out point");

        // Gone well before the 32-frame explosion ends (the author's requirement: still within the explosion).
        assertEquals(0, count(sprites.at(20f), ExplosionSprites.SPARKS),
                "sparks fade out well before the explosion's 32 frames end (20 < DURATION = "
                        + ExplosionDebris.DURATION + ")");

        // The size at any given time is exactly the author's size factor (reverted to ToB 2005's own 1x) applied to
        // ToB 2005's size, minus the scaled shrink: half(f) = size * 1.0 - (0.008 * 1.0 / 1.75) * f, independently
        // derived here.
        float sparkSizeFactor = 1.0f;
        float sparkLifetimeFactor = 1.75f;
        float tob2005FadePerFrame = 0.008f * sparkSizeFactor / sparkLifetimeFactor;
        long seed = 23L;
        ExplosionSprites pinned = ExplosionSprites.start(0f, 0f, 0f, new Random(seed));
        // Reconstruct the first spark's size draw independently (ToB 2005's draw order: dirY, dirZ, yaw, size).
        Random reconstruct = new Random(seed);
        reconstruct.nextInt(80);
        reconstruct.nextInt(4);
        reconstruct.nextInt(31415);
        float sizeDraw = 1f + reconstruct.nextInt(4) / 10f;
        float frames = 6f;
        float f = frames * ExplosionDebris.TOB2005_FRAMES_PER_FRAME;
        float expectedHalf = (sizeDraw * sparkSizeFactor - tob2005FadePerFrame * f) / 32f;
        ExplosionSprites.Sprite spark0 = first(pinned.at(frames), ExplosionSprites.SPARKS);
        assertEquals(expectedHalf, spark0.halfSize(), 1e-5f,
                "spark half size is ToB 2005's size x the size factor, minus the lifetime-scaled shrink");
    }

    @Test
    void sparksFallBackUnderGravity() {
        ExplosionSprites sprites = ExplosionSprites.start(0f, 0f, 0f, new Random(5));

        float highest = Float.NEGATIVE_INFINITY;
        for (ExplosionSprites.Sprite spark : sprites.at(1f)) {
            if (spark.sequence().equals(ExplosionSprites.SPARKS)) {
                highest = Math.max(highest, spark.y());
            }
        }
        float lowestLater = Float.POSITIVE_INFINITY;
        for (ExplosionSprites.Sprite spark : sprites.at(7f)) {
            if (spark.sequence().equals(ExplosionSprites.SPARKS)) {
                lowestLater = Math.min(lowestLater, spark.y());
            }
        }
        assertTrue(highest >= 0f, "they start upwards or level");
        assertTrue(lowestLater < 0f, "dir.y loses 0.015 per ToB 2005 frame: they come down again");
    }

    @Test
    void isAPureFunctionOfTimeEvenAfterManySmallStepsQueriedItBeforehand() {
        // A frame-rate-dependent implementation that integrates per call (e.g. Euler steps from the last queried
        // time) would pass sprites.at(t) == sprites.at(t) on the same call, but would diverge here: querying many
        // small steps before the target time must give exactly the same result as querying the target time alone.
        ExplosionSprites fresh = ExplosionSprites.start(2f, 3f, 1f, new Random(13));
        ExplosionSprites stepped = ExplosionSprites.start(2f, 3f, 1f, new Random(13));

        List<ExplosionSprites.Sprite> direct = fresh.at(5f);
        for (float t = 0f; t < 5f; t += 0.01f) {
            stepped.at(t);
        }
        List<ExplosionSprites.Sprite> afterManySteps = stepped.at(5f);

        assertEquals(direct, afterManySteps,
                "the same time gives exactly the same sprites, however many small steps queried it beforehand");
    }

    @Test
    void aSparksYMatchesTheClosedFormFormulaDerivedFromTheBriefsCoefficients() {
        // Brief/ToB 2005 coefficients (ExplosionSprites' own javadoc): sparks fly along a direction turned by a
        // random yaw, 0.05 units per ToB 2005 frame, while gravity takes 0.015 per frame off the direction's y.
        // Position is the integral of velocity: velocity(f) = dirY - 0.015 f, so
        // y(f) = y0 + 0.05 * integral_0^f velocity = y0 + 0.05 * (dirY * f - 0.015/2 * f^2), in field units (/32).
        // This is derived independently here from the documented coefficients, not copied from the implementation.
        float sparkSpeedPerTob2005Frame = 0.05f;
        float sparkGravityPerTob2005Frame = 0.015f;
        float unitsPerField = 32f;
        long seed = 17L;
        float startY = 3f;
        float frames = 3f;

        ExplosionSprites sprites = ExplosionSprites.start(2f, startY, 1f, new Random(seed));

        // Reconstruct the first spark's (i = 0) random direction-y draw independently: ToB 2005's draw order is
        // direction y, direction z, yaw, size, per sprite (see ExplosionSprites' javadoc), so a fresh Random with
        // the same seed yields the same first draw as the one consumed inside ExplosionSprites.start.
        float dirY0 = new Random(seed).nextInt(80) / 100f;

        float f = frames * ExplosionDebris.TOB2005_FRAMES_PER_FRAME;
        float expectedY = startY + sparkSpeedPerTob2005Frame
                * (dirY0 * f - (sparkGravityPerTob2005Frame / 2f) * f * f) / unitsPerField;

        ExplosionSprites.Sprite spark0 = first(sprites.at(frames), ExplosionSprites.SPARKS);
        assertEquals(expectedY, spark0.y(), 1e-5f,
                "spark y is the closed-form integral of dir.y under constant 0.015/frame gravity");
    }
}
