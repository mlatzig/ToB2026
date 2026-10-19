package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.shader.Shader;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.TestTowers;

class TerrainViewTest {

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    @Test
    void theTerrainIsLitFoggedAndReceivesShadowsAndTheSocketSitsUnderTheTower() {
        AssetLibrary assets = assets();
        TowerDefinition tower = TestTowers.tower().level(0, "FFF", "F.F").build();
        TowerColours colours = TowerColours.of(tower);
        TerrainView view = new TerrainView(assets, tower, colours);

        assertEquals(AssetLibrary.TERRAIN, view.ground().getMaterial().getMaterialDef().getAssetName());
        assertEquals(AssetLibrary.GROUND_DETAIL, view.ground().getMaterial().getTextureParam("DetailMap")
                .getTextureValue().getKey().getName());
        assertEquals(AssetLibrary.GROUND_MOUNTAIN, view.ground().getMaterial().getTextureParam("MountainMap")
                .getTextureValue().getKey().getName());
        assertEquals(20f, (Float) view.ground().getMaterial().getParamValue("DetailScale"), 0f,
                "SKY_TEXTILES_GROUNDMUL");
        assertEquals(2 * (Terrain.DIVISIONS + 1) * (Terrain.DIVISIONS + 1),
                view.ground().getMesh().getFloatBuffer(Type.TexCoord2).limit(), "the ground and mountain weights");
        assertSame(assets.fogColour(), view.fogColour(), "the library's shared fog (GameState sets the horizon)");
        assertEquals("textures/9101-ground-color-2005.png", view.ground().getMaterial().getTextureParam("DiffuseMap")
                .getTextureValue().getKey().getName());
        assertEquals((Terrain.DIVISIONS + 1) * (Terrain.DIVISIONS + 1), view.ground().getMesh().getVertexCount());
        assertEquals(ShadowMode.Receive, view.node().getShadowMode());

        assertNotNull(view.socket());
        BoundingBox box = (BoundingBox) view.socket().getModelBound();
        Socket socket = Socket.of(tower.grid()).orElseThrow();
        assertEquals((socket.x0() + socket.x1()) / 2f, box.getCenter().x, 1e-4f);
        assertEquals(Socket.TOP, box.getCenter().y + box.getYExtent(), 1e-4f, "just below the floor bottoms");
        assertTrue(view.socket().getMaterial().getTextureParam("DiffuseMap").getTextureValue().getKey().getName()
                .contains("playground"));
    }

    /**
     * jME compiles the shaders only on a GPU; headless, the project's terrain material definition loads with jME's
     * multi-pass Phong technique and the shadow techniques, and its fragment shader resolves its {@code #import}s.
     */
    @Test
    void theTerrainMaterialDefinitionHasThePhongAndShadowTechniquesAndItsShaderLoads() {
        AssetManager manager = GeneratedAssets.assetManager();
        Material material = new Material(manager, AssetLibrary.TERRAIN);

        TechniqueDef phong = material.getMaterialDef().getTechniqueDefs("Default").get(0);
        assertEquals(TechniqueDef.LightMode.MultiPass, phong.getLightMode());
        assertEquals("Common/MatDefs/Light/Lighting.vert", phong.getShaderProgramNames().get(Shader.ShaderType.Vertex));
        assertNotNull(material.getMaterialDef().getTechniqueDefs("PostShadow"), "receives the sun's shadows");
        assertNotNull(material.getMaterialDef().getTechniqueDefs("PreShadow"));
        for (String parameter : List.of("ShadowMap0", "ShadowIntensity", "FadeInfo", "Splits", "UseFog", "FogColor",
                "LinearFog", "DetailMap", "MountainMap")) {
            assertNotNull(material.getMaterialDef().getMaterialParam(parameter), parameter);
        }
        String fragment = manager.loadAsset(new AssetKey<String>("MatDefs/Tob/Terrain.frag"));
        assertTrue(fragment.contains("vec2 computeLighting("), "BlinnPhongLighting.glsllib resolved");
        assertTrue(fragment.contains("getFogLinear("), "MaterialFog.glsllib resolved");
        assertTrue(fragment.contains("ground * texCoord2.x + texture2D(m_MountainMap, texCoord) * texCoord2.y"));
    }

    @Test
    void anEmptyTowerHasNoSocket() {
        AssetLibrary assets = assets();
        TowerDefinition empty = TestTowers.tower().build();
        assertTrue(empty.grid().equals(TowerGrid.empty()));

        assertNull(new TerrainView(assets, empty, TowerColours.of(empty)).socket());
    }
}
