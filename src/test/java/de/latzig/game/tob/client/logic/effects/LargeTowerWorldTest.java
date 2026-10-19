package de.latzig.game.tob.client.logic.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.ShadowRange;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.frontend.OrbitCamera;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.LargeTestTowers;
import de.latzig.game.tob.core.sim.TestTowers;

/**
 * Phase 6a spec decision 4 and §4: every size-dependent world element is unchanged for a tower within the 8 × 8 area
 * and covers a 16 × 16 tower: the socket, the terrain's flat zone, the shadow range, the lightning and meteor impact
 * zones and the menu's orbit camera. (The ground haze: {@code LargeTowerViewsTest}.)
 */
class LargeTowerWorldTest {

    private static final TowerDefinition FULL = LargeTestTowers.tower(LargeTestTowers.FULL);
    private static final TowerDefinition NARROW = LargeTestTowers.tower(LargeTestTowers.NARROW);
    private static final String ROW = "FFFFFFFF";
    /** A tower filling the whole original 8 × 8 area on level 0 (cells 4..11). */
    private static final TowerDefinition ORIGINAL = TestTowers.tower()
            .level(0, ROW, ROW, ROW, ROW, ROW, ROW, ROW, ROW).build();

    @Test
    void aTowerWithinTheOriginalAreaKeepsEveryWorldElement() {
        assertEquals(Optional.of(new Socket(3.5f, 3.5f, 11.5f, 11.5f)), Socket.of(ORIGINAL.grid()));
        assertEquals(ShadowRange.DISTANCE, ShadowRange.distance(ORIGINAL), 0f);
        assertEquals(48f, ShadowRange.DISTANCE, 0f, "Phase 4a");
        OrbitCamera orbit = OrbitCamera.of(ORIGINAL);
        assertEquals(OrbitCamera.DISTANCE, orbit.distance(), 0f);
        assertEquals(new OrbitCamera(orbit.targetX(), orbit.targetY(), orbit.targetZ()), orbit);
    }

    @Test
    void theSocketCoversTheFull16x16Tower() {
        assertEquals(Optional.of(new Socket(-0.5f, -0.5f, 15.5f, 15.5f)), Socket.of(FULL.grid()));
        assertEquals(Optional.of(new Socket(-0.5f, 5.5f, 15.5f, 9.5f)), Socket.of(NARROW.grid()));
    }

    @Test
    void theTerrainIsFlatUnderTheWholeGridAndItsSocket() {
        Terrain terrain = Terrain.of(FULL);
        float edge = 15.5f + Socket.BORDER;
        float start = -0.5f - Socket.BORDER;
        for (float x = start; x <= edge; x += 0.5f) {
            for (float z = start; z <= edge; z += 0.5f) {
                assertEquals(Terrain.FLAT_Y, terrain.heightAt(x, z), 1e-6f, x + ", " + z);
            }
        }
        assertEquals(Terrain.FLAT_Y, terrain.heightAt(7.5f - 40f, 7.5f + 40f), 1e-6f, "flat 50 fields out");
    }

    @Test
    void theShadowsOfALargeTowerReachItsFarthestCornerWithTheOriginalsMargin() {
        assertEquals(48f + 4f * (float) Math.sqrt(2.0), ShadowRange.distance(FULL), 1e-4f, "corners 8 x 8 out");
        assertEquals(48f + (float) (Math.hypot(8.0, 2.0) - 4.0 * Math.sqrt(2.0)), ShadowRange.distance(NARROW),
                1e-4f);
        for (TowerDefinition tower : LargeTestTowers.library().towers()) {
            assertTrue(ShadowRange.distance(tower) >= ShadowRange.DISTANCE, tower.id());
        }
    }

    @Test
    void lightningAndMeteorsStrikeOutsideTheFullGridAndItsSocket() {
        float clear = Meteors.TOWER_RADIUS;
        assertTrue(clear >= (float) Math.hypot(8.0 + Socket.BORDER, 8.0 + Socket.BORDER) - 1e-4f);
        for (long seed = 0; seed < 20; seed++) {
            for (Lightning.Strike strike : new Lightning(seed).strikes(0f, 5000f)) {
                float distance = (float) Math.hypot(strike.x() - Lightning.CENTRE, strike.z() - Lightning.CENTRE);
                assertTrue(distance > clear, "lightning " + seed + ": " + distance);
            }
            Meteors meteors = new Meteors(seed);
            for (Meteors.Impact impact : meteors.impacts(0f, 5000f)) {
                float distance = (float) Math.hypot(impact.x() - Lightning.CENTRE, impact.z() - Lightning.CENTRE);
                assertTrue(distance > clear, "meteor " + seed + ": " + distance);
            }
            assertTrue(meteors.closestApproach() > clear, "no meteor path crosses the grid");
        }
    }

    @Test
    void theMenuOrbitOfALargeTowerGrowsWithItsLongerSideAndStaysOutsideIt() {
        OrbitCamera full = OrbitCamera.of(FULL);
        OrbitCamera narrow = OrbitCamera.of(NARROW);

        assertEquals(2f * OrbitCamera.DISTANCE, full.distance(), 1e-6f, "16 fields: twice the radius");
        assertEquals(2f * OrbitCamera.DISTANCE, narrow.distance(), 1e-6f, "the longer side counts");
        for (int step = 0; step < 63; step++) {
            Pose eye = full.eye(step);
            boolean inside = eye.x() > -0.5f && eye.x() < 15.5f && eye.z() > -0.5f && eye.z() < 15.5f;
            assertFalse(inside, "the eye stays outside the tower at " + step + " s: " + eye);
        }
    }
}
