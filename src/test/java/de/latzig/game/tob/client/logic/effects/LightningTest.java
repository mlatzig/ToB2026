package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LightningTest {

    private static final float EPS = 1e-4f;

    private static List<Lightning.Strike> ofBolt(List<Lightning.Strike> strikes, int bolt) {
        return strikes.stream().filter(s -> s.bolt() == bolt).toList();
    }

    private static Lightning.Bolt shown(Lightning lightning, int bolt, float now) {
        return lightning.bolts(now).stream().filter(b -> b.bolt() == bolt).findFirst().orElse(null);
    }

    @Test
    void theSameTowerGivesTheSameStrikesHoweverFinelyTheyAreAsked() {
        List<Lightning.Strike> once = new Lightning(42L).strikes(0f, 2000f);
        Lightning stepped = new Lightning(42L);
        List<Lightning.Strike> steps = new ArrayList<>();
        float last = 0f;
        for (float now = 0.37f; now < 2000f; now += 0.37f) {
            steps.addAll(stepped.strikes(last, now));
            last = now;
        }
        steps.addAll(stepped.strikes(last, 2000f));

        assertEquals(once, steps, "render frames of any length give the same strikes");
        assertEquals(once, new Lightning(42L).strikes(0f, 2000f), "seeded");
        assertNotEquals(once, new Lightning(43L).strikes(0f, 2000f), "another tower, other strikes");
        assertFalse(once.isEmpty());
    }

    @Test
    void threeBoltsWaitAsInToB2005AndStrikeAgainInBurstsAsInTheOriginal() {
        List<Lightning.Strike> strikes = new Lightning(7L).strikes(0f, 20_000f);
        int bursts = 0;
        int waits = 0;
        for (int bolt = 0; bolt < Lightning.BOLTS; bolt++) {
            List<Lightning.Strike> own = ofBolt(strikes, bolt);
            assertTrue(own.size() > 100, "bolt " + bolt);
            float first = own.get(0).time();
            assertTrue(first >= 16f && first < 64f, "the first wait: 256 + rand(768) ToB 2005 frames: " + first);
            for (int i = 0; i < own.size(); i++) {
                Lightning.Strike s = own.get(i);
                float distance = (float) Math.hypot(s.x() - Lightning.CENTRE, s.z() - Lightning.CENTRE);
                assertTrue(distance >= 500f / 32f - EPS && distance <= 1500f / 32f + EPS, "distance " + distance);
                if (i == 0) {
                    continue;
                }
                Lightning.Strike before = own.get(i - 1);
                float gap = s.time() - before.time();
                boolean samePlace = s.x() == before.x() && s.z() == before.z();
                if (gap < 14f) {
                    assertTrue(gap >= 5f - EPS * 100 && gap <= 12f + EPS * 100, "a burst: 5-12 frames, " + gap);
                    assertTrue(samePlace, "a burst strikes the same place");
                    bursts++;
                } else {
                    assertTrue(gap >= 16f - EPS * 100 && gap < 64f + EPS * 100, "a wait: 16-64 frames, " + gap);
                    assertFalse(samePlace, "after a wait the bolt strikes elsewhere");
                    waits++;
                }
            }
        }
        double share = (double) bursts / (bursts + waits);
        assertTrue(share > 0.45 && share < 0.55, "about half of the strikes are followed by a burst: " + share);
        for (int i = 1; i < strikes.size(); i++) {
            assertTrue(strikes.get(i).time() >= strikes.get(i - 1).time(), "by time");
        }
    }

    @Test
    void aBoltShowsOverTheLastFourFramesBeforeItsStrikeWithItsKeysBackwards() {
        Lightning lightning = new Lightning(11L);
        Lightning.Strike strike = ofBolt(lightning.strikes(0f, 200f), 0).get(0);
        float t = strike.time();

        assertEquals(4f, Lightning.BOLT_FRAMES, 0f, "64 ToB 2005 frames");
        assertEquals(null, shown(lightning, 0, t - 4.01f), "not yet");
        Lightning.Bolt early = shown(lightning, 0, t - 3f);
        assertEquals(48f, early.key(), EPS, "3 frames before the strike: key 48 of 64");
        assertEquals(strike.x(), early.x(), 0f);
        assertEquals(strike.z(), early.z(), 0f);
        assertEquals(16f, shown(lightning, 0, t - 1f).key(), EPS);
        assertEquals(0f, shown(lightning, 0, t).key(), EPS, "the last key at the strike");
        Lightning.Bolt after = shown(lightning, 0, t + 0.01f);
        assertTrue(after == null || after.key() < 64f, "afterwards only a burst shows the bolt again");
    }

    @Test
    void everyStrikeFlashesWithItsBoltAndTheBrightestFlashCounts() {
        Lightning lightning = new Lightning(11L);
        Lightning.Strike first = lightning.strikes(0f, 200f).get(0);
        float t = first.time();

        assertEquals(0f, lightning.flash(0f), 0f, "nothing long before the first strike");
        assertEquals(0f, lightning.flash(t - AmbientTuning.FLASH_FRAMES - 0.01f), 0f);
        assertEquals(1f, lightning.flash(t - AmbientTuning.FLASH_FRAMES + 0.001f), 1e-2f, "full with the bolt");
        assertEquals(0.5f, lightning.flash(t - AmbientTuning.FLASH_FRAMES / 2f), EPS);
        assertFalse(lightning.bolts(t - AmbientTuning.FLASH_FRAMES / 2f).isEmpty(), "while the bolt shows");
        assertEquals(0f, lightning.flash(t), 0f, "over at the strike");
        float max = 0f;
        for (float now = 0f; now < 2000f; now += 0.1f) {
            float flash = lightning.flash(now);
            assertTrue(flash >= 0f && flash <= 1f);
            max = Math.max(max, flash);
        }
        assertTrue(max > 0.9f);
    }

    @Test
    void theSameSimulationTimeShowsTheSameBoltsAndFlash() {
        Lightning lightning = new Lightning(5L);
        Lightning.Strike strike = lightning.strikes(0f, 200f).get(0);
        float paused = strike.time() - 2.5f;

        List<Lightning.Bolt> bolts = lightning.bolts(paused);
        float flash = lightning.flash(paused);
        for (int frame = 0; frame < 20; frame++) {
            assertEquals(bolts, lightning.bolts(paused), "a paused game: the bolt stands still");
            assertEquals(flash, lightning.flash(paused), 0f);
        }
        assertFalse(bolts.isEmpty());
    }
}
