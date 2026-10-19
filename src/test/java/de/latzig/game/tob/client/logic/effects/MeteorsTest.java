package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.core.model.TowerGrid;

class MeteorsTest {

    private static final float EPS = 1e-3f;
    private static final long[] SEEDS = {1L, 5L, 21L, 42L, "original-003".hashCode(), -987654321L};

    private static float distance(float x, float z) {
        return (float) Math.hypot(x - Lightning.CENTRE, z - Lightning.CENTRE);
    }

    /** Every impact up to {@code total} steps, asked for in chunks of {@code chunk} steps, as a render loop would. */
    private static List<Meteors.Impact> run(Meteors meteors, int total, int chunk) {
        List<Meteors.Impact> out = new ArrayList<>();
        for (int from = 0; from < total; from += chunk) {
            out.addAll(meteors.impacts(from, Math.min(from + chunk, total)));
        }
        return out;
    }

    @Test
    void theSameTowerGivesTheSameMeteorsHoweverItIsAsked() {
        List<Meteors.Impact> once = run(new Meteors(42L), 1000, 100);
        assertEquals(once, run(new Meteors(42L), 1000, 1), "pure functions of time and seed");
        assertEquals(once, run(new Meteors(42L), 1000, 37));
        assertNotEquals(once, run(new Meteors(43L), 1000, 100), "another tower, other meteors");

        Meteors a = new Meteors(42L);
        Meteors b = new Meteors(42L);
        b.at(50f);
        assertEquals(a.at(123.4f), b.at(123.4f), "however the queries went before");
    }

    @Test
    void fourMeteorsFlyFourToNineSecondsFromTheSkyToTheLandscapeAroundTheTower() {
        Meteors meteors = new Meteors(9L);
        for (Meteors.Meteor m : meteors.at(0f)) {
            float elevation = (float) Math.atan2(m.y(), distance(m.x(), m.z()));
            assertEquals(Meteors.SKY, (float) Math.hypot(distance(m.x(), m.z()), m.y()), 0.01f,
                    "on the sky sphere, about 4551 units out");
            assertTrue(elevation >= 0.5f - EPS && elevation <= 1.3f + EPS, "elevation " + elevation);
        }
        List<Meteors.Impact> impacts = run(meteors, 5000, 50);
        for (int meteor = 0; meteor < Meteors.COUNT; meteor++) {
            float before = 0f;
            int flights = 0;
            for (Meteors.Impact impact : impacts) {
                if (impact.meteor() != meteor) {
                    continue;
                }
                float flight = impact.time() - before;
                assertTrue(flight >= 32f - EPS && flight < 64f + EPS, "512 + rand(512) ToB 2005 frames: " + flight);
                assertEquals(Terrain.FLAT_Y, impact.y(), 0f, "on the visible ground");
                float d = distance(impact.x(), impact.z());
                assertTrue(d >= 500f / 32f - EPS && d < 1500f / 32f + EPS, "500-1500 units out: " + d);
                before = impact.time();
                flights++;
            }
            assertTrue(flights > 70, "the next flight starts at once: " + flights);
        }
    }

    @Test
    void noMeteorStrikesTheTowerOrItsSocket() {
        // Author decision: meteors only strike the landscape. The grid with the socket border lies within
        // TOWER_RADIUS of the centre, whatever the tower; every target keeps a margin of more than 4 fields.
        float half = TowerGrid.WIDTH / 2f + Socket.BORDER;
        assertTrue(Meteors.TOWER_RADIUS >= (float) Math.hypot(half, half) - EPS);
        for (long seed : SEEDS) {
            int count = 0;
            for (Meteors.Impact impact : run(new Meteors(seed), 20_000, 100)) {
                assertTrue(distance(impact.x(), impact.z()) > Meteors.TOWER_RADIUS + 4f, "clear: " + impact);
                assertTrue(Math.abs(impact.x() - Lightning.CENTRE) > half
                        || Math.abs(impact.z() - Lightning.CENTRE) > half, "outside the footprint: " + impact);
                count++;
            }
            assertTrue(count > 1000, "many flights: " + count);
        }
    }

    @Test
    void noPathPassesThroughTheTowersVolume() {
        // The sky point lies on the target's side: the path's horizontal projection keeps out of TOWER_RADIUS, so no
        // rock crosses the tower at any height. Checked on the paths and on the rocks as drawn (half width < 0.5).
        for (long seed : SEEDS) {
            Meteors meteors = new Meteors(seed);
            float closest = Float.POSITIVE_INFINITY;
            for (float now = 0f; now < 20_000f; now += 0.25f) {
                for (Meteors.Meteor m : meteors.at(now)) {
                    closest = Math.min(closest, distance(m.x(), m.z()));
                }
                closest = Math.min(closest, meteors.closestApproach());
            }
            assertTrue(closest > Meteors.TOWER_RADIUS + 0.5f, "seed " + seed + ": closest " + closest);
        }
    }

    @Test
    void theRockNeverGoesBelowTheTerrainAndBreaksApartOnIt() {
        // The hilliest ground; the end of every flight is known when it is drawn, so no frame between two steps (now =
        // step + 0.99) shows a rock under the surface.
        Terrain terrain = Terrain.generate(10, 7L, Lightning.CENTRE, Lightning.CENTRE);
        Meteors.Ground ground = terrain::heightAt;
        Meteors meteors = new Meteors(21L, ground);
        int impacts = 0;
        for (int step = 0; step < 3000; step++) {
            for (float alpha : new float[] {0f, 0.5f, 0.99f}) {
                for (Meteors.Meteor m : meteors.at(step + alpha)) {
                    assertTrue(m.y() >= ground.height(m.x(), m.z()) - 1e-4f, "above the ground: " + m.y());
                }
            }
            for (Meteors.Impact impact : meteors.impacts(step, step + 1f)) {
                assertEquals(terrain.heightAt(impact.x(), impact.z()), impact.y(), 0f, "on the terrain");
                impacts++;
            }
        }
        assertTrue(impacts > 100, "impacts: " + impacts);
        Meteors.Impact last = meteors.impacts(2900f, 3000f).get(0);
        Meteors.Meteor atImpact = meteors.at(last.time() - 0.01f).stream().filter(m -> m.meteor() == last.meteor())
                .findFirst().orElseThrow();
        assertEquals(last.y(), atImpact.y(), 0.05f, "the rock comes down to the impact point");
        assertEquals(last.x(), atImpact.x(), 0.05f);
        assertEquals(last.z(), atImpact.z(), 0.05f);
    }

    @Test
    void aMeteorFliesStraightAndTurns() {
        Meteors meteors = new Meteors(3L);
        Meteors.Impact impact = meteors.impacts(0f, 100f).stream().filter(i -> i.meteor() == 0).findFirst()
                .orElseThrow();
        Meteors.Meteor a = meteors.at(0f).get(0);
        Meteors.Meteor b = meteors.at(impact.time() / 2f).get(0);
        assertEquals((a.x() + impact.x()) / 2f, b.x(), 0.01f, "half way at half the time");
        assertEquals((a.y() + impact.y()) / 2f, b.y(), 0.01f);
        assertEquals((a.z() + impact.z()) / 2f, b.z(), 0.01f);
        assertEquals(a.yaw() + 0.01f * impact.time() * 8f, b.yaw(), 1e-3f, "0.01 rad per ToB 2005 frame");
        assertEquals(a.yaw() + 0.01f * impact.time() * 16f, impact.yaw(), 1e-3f);
        assertEquals((impact.time() / 2f * 16f) % Meteors.MESH_LOOP, b.key(), 0.01f, "the rock's 256-frame loop");
    }

    @Test
    void theTrailRunsFromHotFireBehindTheRockToEmbersAndShrinksOverTheLastTenPercent() {
        Meteors meteors = new Meteors(3L);
        Meteors.Impact impact = meteors.impacts(0f, 100f).stream().filter(i -> i.meteor() == 0).findFirst()
                .orElseThrow();
        Meteors.Meteor m = meteors.at(impact.time() * 0.5f).get(0);

        assertEquals(Meteors.TRAIL, m.trail().size());
        for (int i = 0; i < Meteors.TRAIL; i++) {
            ExplosionSprites.Sprite sprite = m.trail().get(i);
            assertEquals(ExplosionSprites.FIREBALL, sprite.sequence());
            assertEquals(8 + i, sprite.frame(), "fireball frames 8-31");
            assertEquals(0.5f, sprite.halfSize(), 0f, "half size 16 units");
            float back = (float) Math.sqrt(Math.pow(sprite.x() - m.x(), 2) + Math.pow(sprite.y() - 0.5f - m.y(), 2)
                    + Math.pow(sprite.z() - m.z(), 2));
            assertEquals(0.25f * (i + 1), back, 1e-3f, "8 units apart, 16 units above the rock");
            assertTrue(sprite.y() - 0.5f > m.y(), "back along the path, towards the sky");
        }
        assertEquals(0.25f, meteors.at(impact.time() * 0.95f).get(0).trail().get(0).halfSize(), 0.01f,
                "half way through the last 10 %: half size");
        assertTrue(meteors.at(impact.time() - 0.001f).get(0).trail().stream()
                .allMatch(s -> s.halfSize() < 0.01f), "nothing left at the end");
    }

    @Test
    void aLongGameKeepsABoundedHistory() {
        Meteors meteors = new Meteors(5L);
        for (int step = 0; step < 20_000; step++) {
            meteors.at(step + 0.5f);
            meteors.impacts(step, step + 1f);
        }
        assertTrue(meteors.cachedFlights() <= 40, "bounded: " + meteors.cachedFlights());
        assertEquals(new Meteors(5L).impacts(19_900f, 20_000f), meteors.impacts(19_900f, 20_000f),
                "no drift: the same impacts as a fresh query");
        for (Meteors.Meteor m : meteors.at(19_999.9f)) {
            assertTrue(Float.isFinite(m.x()) && Float.isFinite(m.y()) && Float.isFinite(m.z()));
        }
    }
}
