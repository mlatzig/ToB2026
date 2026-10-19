package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer;

import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.MoonHaze;
import de.latzig.game.tob.client.logic.effects.SkyDome;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.effects.StarField;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class SkyViewTest {

    private static final Vector3f TOWARDS_SUN = SkyView.SUN_DIRECTION.negate();
    private static final TowerColours COLOURS = new TowerColours(0x0088CC, 0x102030, 0x005555, 0, 0, 0, 0, 0);
    private static final SkyOptions HALF = new SkyOptions(true, Optional.empty(), Optional.empty());

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    private static String texture(Geometry geometry, String parameter) {
        return geometry.getMaterial().getTextureParam(parameter).getTextureValue().getKey().getName();
    }

    private static List<String> children(SkyView sky) {
        return sky.node().getChildren().stream().map(Spatial::getName).toList();
    }

    /** The first design of {@code type} with {@code look} and {@code size}, searching the seeds from 0. */
    static MoonDesign design(int type, MoonDesign.Look look, MoonDesign.SizeClass size) {
        for (int seed = 0;; seed++) {
            MoonDesign design = MoonDesign.design(type, seed, 0f, 0.2f);
            if (design.look() == look && design.size() == size) {
                return design;
            }
        }
    }

    /** Phase 5b: "Ambient effects Off" holds the clouds still; with the effects on they drift as before. */
    @Test
    void theCloudsStandStillWithoutAmbientEffects() {
        TowerDefinition tower = SampleTowers.tower("sample-000");
        SkyView sky = new SkyView(assets(), tower, TowerColours.of(tower), HALF);
        assertTrue(sky.hasClouds());

        sky.update(Vector3f.ZERO, 0f, true);
        float[] start = sky.clouds().offset().clone();
        sky.update(Vector3f.ZERO, 250f, false);
        assertEquals(start[0], sky.clouds().offset()[0], 0f, "still");
        assertEquals(start[1], sky.clouds().offset()[1], 0f, "still");
        sky.update(Vector3f.ZERO, 250f, true);
        assertNotEquals(start[0], sky.clouds().offset()[0], "drifting");
        sky.dispose();
    }

    @Test
    void aTowerWithStarsMoonAndCloudsShowsThemInTheSkyBucketInThatOrder() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");
        SkyView sky = new SkyView(assets, tower, TowerColours.of(tower), HALF);

        long above = StarField.of(tower).stream().filter(StarField.Star::aboveHorizon).count();
        assertEquals(above, sky.stars());
        assertTrue(above > 200, "density 3: 400 sprites, most above the horizon");
        assertEquals(List.of("sky-dome", "stars", "moon", "clouds"), children(sky), "drawn in this order");
        assertEquals(Bucket.Sky, ((Geometry) sky.node().getChild("stars")).getQueueBucket());
        assertEquals(Bucket.Sky, sky.clouds().geometry().getQueueBucket());
        assertEquals("textures/9100-sky-color-2005.png", texture((Geometry) sky.node().getChild("sky-dome"),
                "ColorMap"), "the ToB 2005 sky texture by default");
        assertEquals(ShadowMode.Off, sky.node().getShadowMode(), "the sky neither casts nor receives");

        sky.update(new Vector3f(1f, 2f, 3f), 0f);
        assertEquals(new Vector3f(1f, 2f, 3f), sky.node().getLocalTranslation());
        sky.node().updateGeometricState();
        float[] d = MoonDesign.of(tower).orElseThrow().direction();
        Vector3f moon = sky.moon().node().getWorldTranslation().subtract(1f, 2f, 3f);
        assertEquals(SkyView.MOON_RADIUS, moon.length(), 1e-3f);
        assertEquals(1f, moon.normalizeLocal().dot(new Vector3f(d[0], d[1], d[2])), 1e-5f, "the design's place");
        sky.dispose();
    }

    /** Decision 5: the moon has its own light from the sun's side at the look's phase and the earthshine. */
    @Test
    void theMoonIsLitOnlyByItsOwnLightsFromTheSunsSide() {
        AssetLibrary assets = assets();
        MoonDesign design = design(1, MoonDesign.Look.THIN_CRESCENT, MoonDesign.SizeClass.NORMAL);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);

        List<Light> lights = List.of(moon.node().getLocalLightList().get(0), moon.node().getLocalLightList().get(1));
        assertTrue(lights.contains(moon.light()) && lights.contains(moon.earthshine()));
        float[] l = design.light(new float[] {TOWARDS_SUN.x, TOWARDS_SUN.y, TOWARDS_SUN.z});
        DirectionalLight light = moon.light();
        assertEquals(0f, light.getDirection().add(l[0], l[1], l[2]).length(), 1e-5f, "shines from the light side");
        AmbientLight earthshine = moon.earthshine();
        assertEquals(design.earthshine(), earthshine.getColor().r, 0f);
        assertTrue(design.earthshine() > 0f, "a thin crescent has earthshine");
        moon.dispose();
    }

    @Test
    void theMoonShowsOnceItsTexturesAreReadyAndTurnsOnSimulationTime() {
        AssetLibrary assets = assets();
        MoonDesign design = design(2, MoonDesign.Look.COMPANION, MoonDesign.SizeClass.NORMAL);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);

        assertEquals(2, moon.spheres().size());
        for (Geometry sphere : moon.spheres()) {
            assertEquals(Bucket.Sky, sphere.getQueueBucket());
            assertEquals("Common/MatDefs/Light/Lighting.j3md", sphere.getMaterial().getMaterialDef().getAssetName());
        }
        moon.awaitTextures();
        moon.update(10f);
        assertTrue(moon.ready());
        for (Geometry sphere : moon.spheres()) {
            assertEquals(CullHint.Never, sphere.getCullHint(), "shown");
            assertNotNull(sphere.getMaterial().getTextureParam("DiffuseMap"));
            assertNotNull(sphere.getMaterial().getTextureParam("NormalMap"));
            assertNull(sphere.getMaterial().getTextureParam("LightMap"), "a normal moon needs no detail texture");
        }
        assertEquals(512, moon.spheres().get(0).getMaterial().getTextureParam("DiffuseMap").getTextureValue()
                .getImage().getWidth(), "half detail: 512 instead of 1024");
        float spin = moon.spin();
        moon.update(10f);
        assertEquals(spin, moon.spin(), 0f, "the same simulation time, the same turn (pause)");
        moon.update(50f);
        assertNotEquals(spin, moon.spin(), "turns as the simulation runs");
        assertEquals(design.spin(50f), moon.spin(), 0f);
    }

    @Test
    void aRingedPlanetDrawsTheRingsFarHalfBeforeAndItsNearHalfAfterTheTurningBody() {
        AssetLibrary assets = assets();
        MoonDesign design = design(3, MoonDesign.Look.GAS_RINGED, MoonDesign.SizeClass.NORMAL);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);

        assertEquals(List.of("moon-ring-far", "moon-body-0", "moon-haze-0", "moon-ring-near"),
                moon.node().getChildren().stream().map(Spatial::getName).toList());
        Geometry near = moon.rings().get(1);
        Geometry far = moon.rings().get(0);
        near.updateModelBound();
        far.updateModelBound();
        assertTrue(near.getModelBound().getCenter().z > 0f, "the near half lies towards the viewer");
        assertTrue(far.getModelBound().getCenter().z < 0f);
        moon.awaitTextures();
        moon.update(0f);
        moon.node().updateGeometricState();
        Quaternion ringBefore = moon.node().getChild("moon-ring-near").getWorldRotation().clone();
        Quaternion pivotBefore = moon.node().getChild("moon-body-0").getWorldRotation().clone();
        moon.update(300f);
        moon.node().updateGeometricState();
        assertEquals(ringBefore, moon.node().getChild("moon-ring-near").getWorldRotation(), "the ring stands still");
        assertNotEquals(pivotBefore, moon.node().getChild("moon-body-0").getWorldRotation(),
                "the body pivot turned");
        assertEquals(CullHint.Never, near.getCullHint());
    }

    /**
     * Acceptance change "moon haze" (Phase 6b-2): after each body a sphere in the sky's colours and sky texture blends
     * the body towards the sky behind it, per vertex by its elevation (a giant moon is hazed more at its foot); it
     * stands still while the body turns and shows with the body.
     */
    @Test
    void eachBodyIsHazedTowardsTheSkyBehindItByElevation() {
        AssetLibrary assets = assets();
        MoonDesign design = design(2, MoonDesign.Look.COMPANION, MoonDesign.SizeClass.NORMAL);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);

        assertEquals(List.of("moon-body-0", "moon-haze-0", "moon-body-1", "moon-haze-1"),
                moon.node().getChildren().stream().map(Spatial::getName).toList(), "each haze right after its body");
        assertEquals(2, moon.hazes().size());
        Geometry haze = moon.hazes().get(0);
        assertEquals(CullHint.Always, haze.getCullHint(), "hidden until the body shows");
        assertEquals(Bucket.Sky, haze.getQueueBucket());
        assertEquals(BlendMode.Alpha, haze.getMaterial().getAdditionalRenderState().getBlendMode());
        assertSame(assets.baseMaterial("sky").getTextureParam("ColorMap").getTextureValue(),
                haze.getMaterial().getTextureParam("ColorMap").getTextureValue(), "the dome's sky texture");
        moon.awaitTextures();
        moon.update(0f);
        moon.node().updateGeometricState();
        Quaternion before = haze.getWorldRotation().clone();
        moon.update(300f);
        moon.node().updateGeometricState();
        assertEquals(before, haze.getWorldRotation(), "the haze stands still");
        assertEquals(CullHint.Never, haze.getCullHint());
        assertEquals(moon.spheres().get(0).getWorldScale(), haze.getWorldScale());
        moon.dispose();
    }

    @Test
    void aGiantMoonIsHazedMoreAtItsFootThanAtItsTop() {
        AssetLibrary assets = assets();
        MoonDesign design = design(0, MoonDesign.Look.GREY, MoonDesign.SizeClass.GIANT);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);
        moon.node().updateGeometricState();

        Geometry haze = moon.hazes().get(0);
        FloatBuffer positions = (FloatBuffer) haze.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
        FloatBuffer colours = (FloatBuffer) haze.getMesh().getBuffer(VertexBuffer.Type.Color).getData();
        float lowest = Float.POSITIVE_INFINITY;
        float highest = Float.NEGATIVE_INFINITY;
        float footStrength = 0f;
        float topStrength = 0f;
        for (int v = 0; v < positions.limit() / 3; v++) {
            Vector3f world = haze.localToWorld(new Vector3f(positions.get(v * 3), positions.get(v * 3 + 1),
                    positions.get(v * 3 + 2)), null);
            float elevation = (float) Math.asin(world.normalize().y);
            float strength = colours.get(v * 4 + 3);
            assertEquals(MoonHaze.strength(elevation), strength, 1e-4f, "vertex " + v);
            if (elevation < lowest) {
                lowest = elevation;
                footStrength = strength;
            }
            if (elevation > highest) {
                highest = elevation;
                topStrength = strength;
            }
        }
        assertTrue(footStrength > topStrength + 0.2f, footStrength + " at the foot, " + topStrength + " at the top");
        moon.dispose();
    }

    /**
     * Task 6 review, Important 1 (moon-haze fix): the Classic sky dome is untextured and its gradient linearised
     * ({@link SkyView#plainSky}); the moon's haze must blend towards that same colour, not the Remake's textured,
     * un-linearised one, mirrored into {@link MoonView} via a classic flag. The Remake path stays unchanged.
     */
    @Test
    void theHazeBlendsTowardsTheClassicSkyColourLinearisedAndTheRemakeColourStaysRaw() {
        AssetLibrary assets = assets();
        MoonDesign design = design(0, MoonDesign.Look.GREY, MoonDesign.SizeClass.GIANT);
        MoonView classicMoon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true, true);
        MoonView remakeMoon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true, false);
        classicMoon.node().updateGeometricState();
        remakeMoon.node().updateGeometricState();

        Geometry classicHaze = classicMoon.hazes().get(0);
        Geometry remakeHaze = remakeMoon.hazes().get(0);
        assertNull(classicHaze.getMaterial().getParam("ColorMap"), "classic: no sky texture, like plainSky()");
        assertEquals(Boolean.TRUE, classicHaze.getMaterial().getParamValue("VertexColor"), "classic: vertex colour");
        assertSame(assets.baseMaterial("sky").getTextureParam("ColorMap").getTextureValue(),
                remakeHaze.getMaterial().getTextureParam("ColorMap").getTextureValue(), "remake: unchanged");

        FloatBuffer positions = (FloatBuffer) classicHaze.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
        FloatBuffer classicColours = (FloatBuffer) classicHaze.getMesh().getBuffer(VertexBuffer.Type.Color).getData();
        FloatBuffer remakeColours = (FloatBuffer) remakeHaze.getMesh().getBuffer(VertexBuffer.Type.Color).getData();
        for (int v = 0; v < positions.limit() / 3; v++) {
            Vector3f world = classicHaze.localToWorld(new Vector3f(positions.get(v * 3), positions.get(v * 3 + 1),
                    positions.get(v * 3 + 2)), null).normalizeLocal();
            float[] look = SkyDome.look(COLOURS, world.x, world.y, world.z);
            assertEquals(SrgbColours.toLinear(look[2]), classicColours.get(v * 4), 1e-6f, "classic r, vertex " + v);
            assertEquals(SrgbColours.toLinear(look[3]), classicColours.get(v * 4 + 1), 1e-6f, "classic g, vertex " + v);
            assertEquals(SrgbColours.toLinear(look[4]), classicColours.get(v * 4 + 2), 1e-6f, "classic b, vertex " + v);
            assertEquals(look[2], remakeColours.get(v * 4), 0f, "remake r raw, vertex " + v);
            assertEquals(look[3], remakeColours.get(v * 4 + 1), 0f, "remake g raw, vertex " + v);
            assertEquals(look[4], remakeColours.get(v * 4 + 2), 0f, "remake b raw, vertex " + v);
            assertEquals(classicColours.get(v * 4 + 3), remakeColours.get(v * 4 + 3), 0f, "alpha unchanged by both");
        }
        classicMoon.dispose();
        remakeMoon.dispose();
    }

    @Test
    void largeAndGiantMoonsGetTheDetailTexture() {
        AssetLibrary assets = assets();
        MoonDesign design = design(0, MoonDesign.Look.GREY, MoonDesign.SizeClass.GIANT);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, true);

        moon.awaitTextures();
        moon.update(0f);
        Geometry sphere = moon.spheres().get(0);
        assertNotNull(sphere.getMaterial().getTextureParam("LightMap"), "the tiled detail texture");
        assertEquals(2048, sphere.getMaterial().getTextureParam("DiffuseMap").getTextureValue().getImage()
                .getWidth(), "giant at half detail: 2048");
        moon.node().updateGeometricState();
        float radius = SkyView.MOON_RADIUS * FastMath.sin(design.size().width() / 2f);
        assertEquals(radius, sphere.getWorldScale().x, 1e-3f, "45 degrees wide");
    }

    /**
     * Final review Minor 4: the spheres and the ring stay hidden until the textures are ready, and {@link
     * MoonView#dispose} cancels a running generation (a full-detail giant ringed planet takes well over 100 ms, so
     * the generation is still running when it is disposed; should it ever have finished first, the moon must still
     * stay hidden until the next update).
     */
    @Test
    void theMoonStaysHiddenUntilItsTexturesAreReadyAndDisposeCancelsTheGeneration() {
        AssetLibrary assets = assets();
        MoonDesign design = design(3, MoonDesign.Look.GAS_RINGED, MoonDesign.SizeClass.GIANT);
        MoonView moon = new MoonView(assets, design, TOWARDS_SUN, COLOURS, false);

        for (Geometry sphere : moon.spheres()) {
            assertEquals(CullHint.Always, sphere.getCullHint(), "hidden before the textures are ready");
            assertNull(sphere.getMaterial().getTextureParam("DiffuseMap"));
        }
        assertEquals(2, moon.rings().size());
        for (Geometry ring : moon.rings()) {
            assertEquals(CullHint.Always, ring.getCullHint(), "the ring shows together with the bodies");
        }
        assertFalse(moon.ready());

        moon.dispose();
        try {
            moon.awaitTextures();
        } catch (CompletionException e) {
            assertInstanceOf(CancellationException.class, e.getCause(), "dispose cancelled the generation");
        }
        assertFalse(moon.ready(), "nothing is shown without an update");
        for (Geometry sphere : moon.spheres()) {
            assertEquals(CullHint.Always, sphere.getCullHint());
        }
    }

    /** Phase 5e (Classic Look, decisions 1 and 2): the plain palette gradient, the stars and the moon, no clouds. */
    @Test
    void theClassicSkyIsThePlainGradientWithStarsAndMoonButNoClouds() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");

        SkyView sky = new SkyView(assets, tower, TowerColours.of(tower), HALF, Optional.empty(), true);

        assertEquals(List.of("sky-dome", "stars", "moon"), children(sky), "no clouds");
        assertNull(sky.clouds());
        assertTrue(sky.stars() > 200, "the stars as in the Remake look");
        Geometry dome = (Geometry) sky.node().getChild("sky-dome");
        assertEquals("Common/MatDefs/Misc/Unshaded.j3md", dome.getMaterial().getMaterialDef().getAssetName());
        assertNull(dome.getMaterial().getParam("ColorMap"), "no sky texture");
        assertEquals(Boolean.TRUE, dome.getMaterial().getParamValue("VertexColor"), "the palette gradient");
        sky.dispose();
    }

    /**
     * Controller gamma rule: the classic dome's vertex colours (no texture) are the palette gradient converted from
     * sRGB to linear; the Remake dome keeps its colours.
     */
    @Test
    void theClassicGradientIsLinearisedAndTheRemakeGradientIsNot() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");
        TowerColours colours = TowerColours.of(tower);
        float[] srgb = SkyDome.arrays(colours, SkyView.DOME_RADIUS).colors();

        float[] classic = domeColours(new SkyView(assets, tower, colours, HALF, Optional.empty(), true));
        float[] remake = domeColours(new SkyView(assets, tower, colours, HALF, Optional.empty(), false));

        assertEquals(srgb.length, classic.length);
        boolean changed = false;
        for (int i = 0; i < srgb.length; i++) {
            float expected = i % 4 == 3 ? srgb[i] : SrgbColours.toLinear(srgb[i]);
            assertEquals(expected, classic[i], 1e-6f, "channel " + i);
            assertEquals(srgb[i], remake[i], 0f, "remake channel " + i);
            changed |= expected != srgb[i];
        }
        assertTrue(changed, "the sample's sky colours are not all 0 or 1");
    }

    private static float[] domeColours(SkyView sky) {
        Geometry dome = (Geometry) sky.node().getChild("sky-dome");
        FloatBuffer buffer = (FloatBuffer) dome.getMesh().getBuffer(VertexBuffer.Type.Color).getData();
        float[] colours = new float[buffer.limit()];
        buffer.rewind();
        buffer.get(colours);
        sky.dispose();
        return colours;
    }

    @Test
    void aTowerWithoutStarsMoonAndCloudsShowsOnlyTheDome() {
        AssetLibrary assets = assets();
        TowerDefinition tower = TestTowers.tower().level(0, "F").build();
        SkyView sky = new SkyView(assets, tower, TowerColours.of(tower));

        assertEquals(0, sky.stars());
        assertNull(sky.moon());
        assertNull(sky.clouds());
        assertEquals(List.of("sky-dome"), children(sky));
        assertTrue(Float.isNaN(sky.moonSpin()));
    }
}
