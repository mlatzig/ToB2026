package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.effects.ExplosionSprites;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TowerAssets;
import de.latzig.game.tob.core.sim.WorldState;

class AmbientViewsTest {

    private static final Vector3f LEFT = new Vector3f(-1f, 0f, 0f);
    private static final Vector3f UP = Vector3f.UNIT_Y;

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    @Test
    void boltsShowBeforeTheirStrikeAndEveryStrikeBreaksBlackGroundDebrisApart() {
        AssetLibrary assets = assets();
        ExplosionView explosions = new ExplosionView(assets, 1L);
        LightningView view = new LightningView(assets, explosions, 77L);
        Lightning.Strike first = new Lightning(77L).strikes(0f, 200f).get(0);

        view.update(0f, LEFT);
        assertEquals(0, view.shownBolts());
        view.update(first.time() - 2f, LEFT);
        assertTrue(view.shownBolts() >= 1, "the bolt shows over the last 4 frames");
        Geometry bolt = view.bolt(first.bolt());
        assertEquals(CullHint.Inherit, bolt.getLocalCullHint(), "shown");
        assertEquals(new Vector3f(first.x(), Lightning.BOLT_Y, first.z()), bolt.getLocalTranslation());
        assertEquals(Bucket.Transparent, bolt.getQueueBucket());
        assertEquals(0, view.strikes());

        view.update(first.time() + 0.5f, LEFT);
        assertEquals(1, view.strikes());
        assertEquals(1, explosions.active(), "the ground debris");
        explosions.update(first.time() + 0.5f, LEFT, UP);
        assertEquals(12, explosions.shownSprites(), "with the 12 explosion sprites");
        assertEquals(0.5f / AmbientTuning.FLASH_FRAMES, view.flash(first.time() - 0.5f), 1e-5f, "with the bolt");
        assertEquals(0f, view.flash(first.time() + 0.5f), 0f);
        view.update(first.time() + 0.5f, LEFT);
        assertEquals(1, view.strikes(), "a paused game strikes nothing new");
    }

    /** Phase 4c: each update hands its new strikes and impacts on, once, for their sounds. */
    @Test
    void eachUpdateHandsItsNewStrikesAndImpactsOnOnce() {
        AssetLibrary assets = assets();
        LightningView lightning = new LightningView(assets, new ExplosionView(assets, 1L), 77L);
        MeteorView meteors = new MeteorView(assets, new ExplosionView(assets, 1L), 5L, Meteors.FLAT_GROUND);
        List<Lightning.Strike> strikes = new Lightning(77L).strikes(0f, 100f);
        List<Meteors.Impact> impacts = new Meteors(5L).impacts(0f, 100f);
        List<Lightning.Strike> struck = new ArrayList<>();
        List<Meteors.Impact> hit = new ArrayList<>();

        for (float now = 0.5f; now <= 100f; now += 0.5f) {
            lightning.update(now, LEFT);
            meteors.update(now, LEFT, UP);
            struck.addAll(lightning.newStrikes());
            hit.addAll(meteors.newImpacts());
        }
        lightning.update(100f, LEFT);
        meteors.update(100f, LEFT, UP);

        assertEquals(strikes, struck);
        assertEquals(impacts, hit);
        assertEquals(List.of(), lightning.newStrikes(), "paused: nothing new");
        assertEquals(List.of(), meteors.newImpacts());
    }

    @Test
    void theBoltTurnsAboutYToFaceTheCamera() {
        AssetLibrary assets = assets();
        LightningView view = new LightningView(assets, new ExplosionView(assets, 1L), 77L);
        Lightning.Strike first = new Lightning(77L).strikes(0f, 200f).get(0);
        Vector3f left = new Vector3f(0f, 0f, 1f);

        view.update(first.time() - 1f, left);

        Vector3f right = view.bolt(first.bolt()).getLocalRotation().mult(Vector3f.UNIT_X);
        assertEquals(0f, right.x, 1e-5f);
        assertEquals(-1f, right.z, 1e-5f, "the billboard's x axis is the camera's right");
        assertEquals(0f, right.y, 1e-5f, "turned about y only");
    }

    @Test
    void meteorsFlyWithTheirTrailsAndBreakApartOnImpact() {
        AssetLibrary assets = assets();
        ExplosionView explosions = new ExplosionView(assets, 1L);
        MeteorView view = new MeteorView(assets, explosions, 5L, Meteors.FLAT_GROUND);
        Meteors reference = new Meteors(5L);
        Meteors.Impact first = reference.impacts(0f, 100f).get(0);

        view.update(1f, LEFT, UP);
        assertEquals(Meteors.COUNT * Meteors.TRAIL, view.shownSprites(), "24 trail sprites per meteor");
        Vector3f before = view.rock(0).getLocalTranslation().clone();
        view.update(1f, LEFT, UP);
        assertEquals(before, view.rock(0).getLocalTranslation(), "a paused game: the rock stands still");
        view.update(2f, LEFT, UP);
        assertNotEquals(before, view.rock(0).getLocalTranslation(), "it flies on with the simulation");
        assertEquals(0, view.impacts());

        float now = first.time() + 0.25f;
        view.update(now, LEFT, UP);
        assertEquals(1, view.impacts());
        assertEquals(1, explosions.active(), "the rock breaks apart");
        Geometry debris = (Geometry) ((Node) explosions.node().getChild("explosion-1")).getChild(0);
        assertEquals(AssetLibrary.color(0x878787), debris.getMaterial().getParamValue("Diffuse"),
                "Phase 4b-4 decision 4: ToB 2005's explosion colour 16/16/16 averaged with the rock's white");
        assertEquals(AssetLibrary.color(0xFFFFFF), view.rock(0).getMaterial().getParamValue("Diffuse"));
    }

    @Test
    void aMeteorBreaksApartVisiblyOnTheGroundInsteadOfSinkingIntoIt() {
        // Acceptance defect: ToB 2005's end point (y -0.5) lay under the 4b-1 ground (y -0.033); the rock sank half
        // into it and the debris never came up through the surface.
        AssetLibrary assets = assets();
        ExplosionView explosions = new ExplosionView(assets, 1L);
        MeteorView view = new MeteorView(assets, explosions, 5L, Meteors.FLAT_GROUND);
        Meteors reference = new Meteors(5L);
        Meteors.Impact first = reference.impacts(0f, 100f).get(0);
        view.update(first.time() - 1e-3f, LEFT, UP);
        assertTrue(view.rock(first.meteor()).getLocalTranslation().y >= Terrain.FLAT_Y - 1e-3f,
                "the rock's origin never goes below the ground");
        float now = first.time() + 0.5f;
        view.update(now, LEFT, UP);
        explosions.update(now, LEFT, UP);

        assertEquals(1, view.impacts());
        Node blast = (Node) explosions.node().getChild("explosion-1");
        assertEquals(Terrain.FLAT_Y, blast.getLocalTranslation().y, 1e-3f, "breaks apart on the ground");
        FloatBuffer positions = (FloatBuffer) ((Geometry) blast.getChild(0)).getMesh().getBuffer(Type.Position)
                .getData();
        int above = 0;
        int corners = positions.limit() / 3;
        for (int c = 0; c < corners; c++) {
            if (blast.getLocalTranslation().y + positions.get(c * 3 + 1) > Terrain.FLAT_Y) {
                above++;
            }
        }
        assertEquals(corners, above, "every flying piece of the rock is above the ground");
        assertEquals(ExplosionSprites.COUNT, explosions.shownSprites(), "with the 12 explosion sprites");
    }

    @Test
    void theTrailsAndBoltsKeepTheirMeshBuffersAndTheBoltPlaysItsAtlasKeys() {
        AssetLibrary assets = assets();
        MeteorView meteors = new MeteorView(assets, new ExplosionView(assets, 1L), 5L, Meteors.FLAT_GROUND);
        LightningView lightning = new LightningView(assets, new ExplosionView(assets, 1L), 77L);
        Lightning.Strike first = new Lightning(77L).strikes(0f, 200f).get(0);

        meteors.update(1f, LEFT, UP);
        Object trailPositions = meteors.trailGeometry().getMesh().getBuffer(Type.Position).getData();
        meteors.update(1.5f, LEFT, UP);
        meteors.update(2.5f, LEFT, UP);
        assertEquals(Meteors.COUNT * Meteors.TRAIL, meteors.shownSprites());
        assertSame(trailPositions, meteors.trailGeometry().getMesh().getBuffer(Type.Position).getData());

        lightning.update(first.time() - 3f, LEFT);
        Geometry bolt = lightning.bolt(first.bolt());
        Object boltPositions = bolt.getMesh().getBuffer(Type.Position).getData();
        float[] earlier = texCoords(bolt);
        lightning.update(first.time() - 1f, new Vector3f(0f, 0f, 1f));
        assertSame(boltPositions, bolt.getMesh().getBuffer(Type.Position).getData());
        assertFalse(Arrays.equals(earlier, texCoords(bolt)), "the bolt steps through its atlas keys");
    }

    private static float[] texCoords(Geometry geometry) {
        FloatBuffer buffer = (FloatBuffer) geometry.getMesh().getBuffer(Type.TexCoord).getData();
        float[] values = new float[buffer.limit()];
        buffer.duplicate().rewind().get(values);
        return values;
    }

    @Test
    void theHazeLiesJustAboveTheLevelZeroFloorsAndPulsesGently() {
        AssetLibrary assets = assets();
        HazeView view = new HazeView(assets);
        Geometry haze = view.geometry();

        assertEquals(new Vector3f(Lightning.CENTRE, 0f, Lightning.CENTRE), haze.getLocalTranslation());
        haze.updateModelBound();
        assertEquals(3.5f / 32f, haze.getModelBound().getCenter().y, 1e-3f, "3.5 units up");
        assertEquals(Bucket.Transparent, haze.getQueueBucket());
        assertNotNull(haze.getMaterial().getTextureParam("ColorMap"));
        assertTrue(HazeFirstComparator.isHaze(haze), "drawn before the other transparent geometries");
        view.update(AmbientTuning.HAZE_PULSE_FRAMES / 4f);
        assertEquals(AmbientTuning.hazeBrightness(AmbientTuning.HAZE_PULSE_FRAMES / 4f), view.brightness(), 0f);
        ColorRGBA colour = (ColorRGBA) haze.getMaterial().getParam("Color").getValue();
        assertEquals(view.brightness(), colour.r, 0f);
        assertEquals(1f, colour.a, 0f);
        view.update(0f);
        assertEquals(1f, view.brightness(), 1e-6f);
        assertSame(colour, haze.getMaterial().getParam("Color").getValue(), "one colour, rewritten");
    }

    @Test
    void theFlashOverlayCoversTheViewOnlyWhileItFlashes() {
        FlashOverlay overlay = new FlashOverlay(GeneratedAssets.assetManager());

        overlay.update(0f, 800, 600);
        assertEquals(CullHint.Always, overlay.geometry().getCullHint());
        overlay.update(0.1f, 800, 600);
        assertEquals(CullHint.Never, overlay.geometry().getCullHint());
        assertEquals(new Vector3f(800f, 600f, 1f), overlay.geometry().getLocalScale());
        ColorRGBA colour = (ColorRGBA) overlay.geometry().getMaterial().getParam("Color").getValue();
        assertEquals(0.1f, colour.a, 0f);
        assertEquals(1f, colour.r, 0f);
        overlay.update(0.05f, 800, 600);
        assertSame(colour, overlay.geometry().getMaterial().getParam("Color").getValue(), "one colour, rewritten");
        assertEquals(0.05f, colour.a, 0f);
    }

    /** Phase 5b: "Ambient effects Off" leaves out the lightning and the meteors; the haze stays, without a pulse. */
    @Test
    void ambientEffectsOffLeaveOutTheLightningAndTheMeteors() throws IOException {
        AssetLibrary assets = assets();
        assumeTrue(TowerAssets.available(), "converted towers not available");
        TowerDefinition tower = TowerAssets.allTowers().stream().filter(t -> t.id().equals("original-034"))
                .findFirst().orElseThrow();
        assertTrue(tower.effects().lightning() && tower.effects().meteor(), "original-034 has both");
        WorldState world = Simulation.start(tower, SimOptions.DEFAULT).world();

        WorldView on = new WorldView(assets, world, TowerColours.of(tower), true);
        WorldView off = new WorldView(assets, world, TowerColours.of(tower), false);

        assertEquals(0, on.lightningStrikes());
        assertEquals(0, on.meteorImpacts());
        assertEquals(-1, off.lightningStrikes(), "no lightning");
        assertEquals(-1, off.meteorImpacts(), "no meteors");
        off.updateEffects(500f, LEFT, UP);
        assertEquals(0f, off.flash(500f), 0f);
        assertEquals(List.of(), off.newStrikes());
    }
}
