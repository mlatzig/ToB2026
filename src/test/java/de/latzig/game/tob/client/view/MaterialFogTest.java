package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;

import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Phase 4b-4 spec decision 2: every world material gets ToB 2005's linear horizon fog (one shared colour and range),
 * the unlit and alpha-blended ones through the project's {@link AssetLibrary#UNLIT_FOG} (colour only, the alpha
 * stays); the sky dome, the stars and the moon stay unfogged.
 */
class MaterialFogTest {

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    private static void assertFogged(AssetLibrary assets, Material material, String what) {
        assertEquals(Boolean.TRUE, material.getParamValue("UseFog"), what);
        assertSame(assets.fogColour(), material.getParamValue("FogColor"), what + ": the shared fog colour");
        assertEquals(new Vector2f(MaterialTuning.FOG_START, MaterialTuning.FOG_END),
                material.getParamValue("LinearFog"), what);
    }

    @Test
    void everyWorldMaterialSharesTheFogColourAndRangeAndTheUnlitOnesUseTheColourOnlyFog() {
        AssetLibrary assets = assets();
        assets.setFogColour(0x123456);
        List<Material> world = new ArrayList<>();
        for (ObjectType type : ObjectType.values()) {
            for (MeshGroup group : assets.element(type).groups()) {
                for (MeshPart part : group.parts()) {
                    world.add(assets.material(part, assets.mesh(part.mesh()).hasColors()));
                }
            }
        }
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            world.add(assets.staticMaterial(quadrant));
        }
        world.add(assets.litBaseMaterial("ground"));
        world.add(assets.litBaseMaterial("playground"));
        world.add(assets.terrainMaterial());
        Material fx = assets.fxMaterial();
        Material haze = assets.hazeMaterial();
        Material clouds = assets.unlitFogMaterial();
        world.addAll(List.of(fx, haze, clouds));

        for (Material material : world) {
            assertFogged(assets, material, material.getMaterialDef().getAssetName());
        }
        assertEquals(AssetLibrary.color(0x123456), assets.fogColour());
        for (Material unlit : List.of(fx, haze, clouds)) {
            assertEquals(AssetLibrary.UNLIT_FOG, unlit.getMaterialDef().getAssetName());
        }
        assertEquals(BlendMode.Alpha, fx.getAdditionalRenderState().getBlendMode());
        assertEquals(assets.fxAtlas().image(),
                fx.getTextureParam("ColorMap").getTextureValue().getKey().getName());

        assets.setFogColour(0xFF0000);
        assertEquals(AssetLibrary.color(0xFF0000), world.get(0).getParamValue("FogColor"),
                "a tower start rewrites the colour of every material built so far");
    }

    /** Phase 5e (Classic Look, decision 1): no distance fog; the next scene build may turn it on again. */
    @Test
    void theFogTurnsOffForEveryMaterialAndOnAgain() {
        AssetLibrary assets = assets();
        Material terrain = assets.terrainMaterial();

        assets.setFog(false);

        assertFalse(assets.fog());
        Vector2f range = (Vector2f) terrain.getParamValue("LinearFog");
        assertTrue(range.x > 10f * CameraController.FAR, "beyond every far plane: " + range);
        assertTrue(range.y > range.x);
        assertSame(range, assets.fxMaterial().getParamValue("LinearFog"), "materials built later share it");
        assets.setFog(true);
        assertTrue(assets.fog());
        assertEquals(new Vector2f(MaterialTuning.FOG_START, MaterialTuning.FOG_END), range);
    }

    @Test
    void theSkyDomeStarsAndMoonStayUnfoggedWhileTheCloudsAreFogged() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");
        SkyView sky = new SkyView(assets, tower, TowerColours.of(tower));
        List<String> fogged = new ArrayList<>();
        List<String> unfogged = new ArrayList<>();

        sky.node().depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                boolean fog = Boolean.TRUE.equals(geometry.getMaterial().getParamValue("UseFog"));
                (fog ? fogged : unfogged).add(geometry.getName());
            }
        });
        sky.dispose();

        assertEquals(List.of("clouds"), fogged, "ToB 2005 fogs its clouds (sky.cpp:831)");
        assertTrue(unfogged.containsAll(List.of("sky-dome", "stars")), unfogged.toString());
        assertTrue(unfogged.stream().anyMatch(name -> name.startsWith("moon")), unfogged.toString());
        assertNull(assets.spriteMaterial(assets.fxAtlas().image()).getParamValue("UseFog"), "the stars' material");
        assertNull(assets.baseMaterial("sky").getParamValue("UseFog"), "the dome's material");
    }

    /**
     * jME compiles the shaders only on a GPU; headless, the asset manager still loads the project's material
     * definition and both shader sources from the classpath and resolves their {@code #import}s from the jME jar.
     */
    @Test
    void theUnlitFogMaterialDefinitionAndItsShadersLoadFromTheClasspath() {
        AssetManager manager = GeneratedAssets.assetManager();
        Material material = new Material(manager, AssetLibrary.UNLIT_FOG);

        assertNotNull(material.getMaterialDef().getMaterialParam("FogColor"));
        assertNotNull(material.getMaterialDef().getTechniqueDefs("Default"));
        String vertex = manager.loadAsset(new AssetKey<String>("MatDefs/Tob/UnlitFog.vert"));
        String fragment = manager.loadAsset(new AssetKey<String>("MatDefs/Tob/UnlitFog.frag"));
        assertTrue(vertex.contains("TransformWorld("), "Instancing.glsllib resolved");
        assertTrue(vertex.contains("fogDistance = distance(g_CameraPosition"));
        assertTrue(fragment.contains("getFogLinear("), "MaterialFog.glsllib resolved");
        assertTrue(fragment.contains("color.rgb = MaterialFog_calculateFogColor(color).rgb;"), "the colour only");
    }
}
