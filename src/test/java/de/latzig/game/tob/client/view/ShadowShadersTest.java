package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.TechniqueDef;
import com.jme3.scene.Geometry;

import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;
import de.latzig.game.tob.core.model.StaticQuadrant;

/**
 * Phase 5e shadow fix (author decision 2026-10-16): every lit, shadow-receiving material of the project receives the
 * sun's shadows through the ToB post-shadow shaders ({@code MatDefs/Tob/PostShadow.vert/.frag}: normal-offset lookup
 * by {@link MaterialTuning#SHADOW_NORMAL_OFFSET}, faces turned away from the sun in full shadow, a constant depth
 * offset); {@code MatDefs/Tob/Lighting.j3md} is jME 3.9's {@code Lighting.j3md} otherwise. Translucent glass keeps
 * jME's definition (headless).
 */
class ShadowShadersTest {

    private static final String JME_LIGHTING = "Common/MatDefs/Light/Lighting.j3md";
    private static final String VERT = "MatDefs/Tob/PostShadow.vert";
    private static final String FRAG = "MatDefs/Tob/PostShadow.frag";

    private static MaterialDef definition(AssetManager manager, String name) {
        return new Material(manager, name).getMaterialDef();
    }

    private static void assertTobPostShadow(MaterialDef definition) {
        List<TechniqueDef> post = definition.getTechniqueDefs("PostShadow");
        assertFalse(post.isEmpty(), definition.getAssetName());
        for (TechniqueDef technique : post) {
            assertEquals(VERT, technique.getVertexShaderName(), definition.getAssetName());
            assertEquals(FRAG, technique.getFragmentShaderName(), definition.getAssetName());
            assertEquals(0f, technique.getForcedRenderState().getPolyOffsetFactor(), "no slope offset: no dots");
            assertEquals(-1f, technique.getForcedRenderState().getPolyOffsetUnits());
        }
        MatParam offset = definition.getMaterialParam("ShadowNormalOffset");
        assertEquals(MaterialTuning.SHADOW_NORMAL_OFFSET, offset.getValue(), definition.getAssetName());
    }

    private static List<String> names(List<TechniqueDef> techniques) {
        List<String> names = new ArrayList<>();
        for (TechniqueDef technique : techniques) {
            names.add(technique.getVertexShaderName() + " " + technique.getFragmentShaderName() + " "
                    + technique.getShaderProgramLanguages());
        }
        return names;
    }

    @Test
    void theProjectLightingIsJmesLightingWithTheTobPostShadowShaders() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetManager manager = GeneratedAssets.assetManager();
        MaterialDef tob = definition(manager, AssetLibrary.LIGHTING);
        MaterialDef jme = definition(manager, JME_LIGHTING);

        assertTobPostShadow(tob);
        assertEquals(jme.getTechniqueDefsNames(), tob.getTechniqueDefsNames());
        for (String technique : jme.getTechniqueDefsNames()) {
            if (!technique.equals("PostShadow")) {
                assertEquals(names(jme.getTechniqueDefs(technique)), names(tob.getTechniqueDefs(technique)),
                        technique);
            }
        }
        List<String> jmeParameters = jme.getMaterialParams().stream().map(MatParam::getName).sorted().toList();
        List<String> tobParameters = tob.getMaterialParams().stream().map(MatParam::getName)
                .filter(name -> !name.equals("ShadowNormalOffset")).sorted().toList();
        assertEquals(jmeParameters, tobParameters, "every parameter of jME's definition");
        TechniqueDef jmePost = jme.getTechniqueDefs("PostShadow").get(0);
        TechniqueDef tobPost = tob.getTechniqueDefs("PostShadow").get(0);
        assertEquals(jmePost.getDefineNames().length, tobPost.getDefineNames().length, "the same defines");
        assertTobPostShadow(definition(manager, AssetLibrary.TERRAIN));
    }

    @Test
    void theShadersLookUpAlongTheNormalAndShadowFacesAwayFromTheSun() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetManager manager = GeneratedAssets.assetManager();
        String vert = (String) manager.loadAsset(VERT);
        String frag = ((String) manager.loadAsset(FRAG)).replaceAll("\\s+", " ");

        assertTrue(vert.contains("worldPos.xyz + normal * m_ShadowNormalOffset"), "the normal-offset lookup");
        assertTrue(vert.contains("m_LightViewProjectionMatrix0 * lookupPos"));
        assertTrue(frag.contains("if(nDotL > 0.0){ shadow = 0.0; }"), "away from the sun: full shadow");
        assertTrue(frag.contains("#ifdef DISCARD_ALPHA"), "alpha-tested parts as in jME");
    }

    @Test
    void everyOpaqueLitMaterialOfBothLooksIsOnTheProjectLightingAndTheGlassOnJmes() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        ClassicMaterials classic = new ClassicMaterials(library);
        List<Material> lit = new ArrayList<>();
        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            lit.add(library.staticMaterial(quadrant));
            lit.add(classic.base(quadrant));
        }
        lit.add(library.litBaseMaterial("ground"));
        lit.add(library.litBaseMaterial("playground"));
        int glass = 0;
        for (ObjectType type : ObjectType.values()) {
            for (MeshGroup group : library.element(type).groups()) {
                for (MeshPart part : group.parts()) {
                    if (part.lighting() == Lighting.UNLIT) {
                        continue;
                    }
                    if (part.render() == RenderType.GLASS) {
                        // translucent glass keeps jME's post-shadow pass in both looks
                        glass++;
                        assertEquals(AssetLibrary.JME_LIGHTING, library.material(part, false).getMaterialDef()
                                .getAssetName(), part.mesh());
                        assertEquals(AssetLibrary.JME_LIGHTING, classic.part(type, part, false).getMaterialDef()
                                .getAssetName(), part.mesh());
                    } else {
                        lit.add(library.material(part, false));
                        lit.add(classic.part(type, part, false));
                    }
                }
            }
        }
        assertTrue(glass > 0, "the prism's glass");
        lit.add(((Geometry) new ClassicGroundView(library, 0x808080).node().getChild("ground")).getMaterial());

        for (Material material : lit) {
            assertEquals(AssetLibrary.LIGHTING, material.getMaterialDef().getAssetName(), material.toString());
            assertEquals(MaterialTuning.SHADOW_NORMAL_OFFSET, material.getParam("ShadowNormalOffset").getValue());
        }
    }
}
