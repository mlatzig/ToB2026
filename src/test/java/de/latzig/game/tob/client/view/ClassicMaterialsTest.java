package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ColorSpace;

import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Phase 5e (Classic Look, spec §3; rework, author 2026-10-16): the Classic materials of the object parts are the
 * static classic textures the converter coloured once (headless, the generated assets).
 */
class ClassicMaterialsTest {

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    private static MeshPart part(AssetLibrary assets, ObjectType type, int index) {
        return assets.element(type).groups().get(0).parts().get(index);
    }

    /** The texel (x, y) of a loaded BGR8 texture (an RGB PNG as jME loads it) as 0xRRGGBB. */
    private static int texel(Image image, int x, int y) {
        ByteBuffer data = image.getData(0);
        int i = (y * image.getWidth() + x) * 3;
        return (data.get(i + 2) & 0xFF) << 16 | (data.get(i + 1) & 0xFF) << 8 | data.get(i) & 0xFF;
    }

    @Test
    void aLitPartIsItsStaticClassicTextureWithoutFilteringNormalMapSpecularOrFog() {
        AssetLibrary assets = assets();
        ClassicMaterials classic = new ClassicMaterials(assets);
        MeshPart body = part(assets, ObjectType.ZAP, 0);

        Material material = classic.part(ObjectType.ZAP, body, true);

        assertSame(material, classic.part(ObjectType.ZAP, body, true), "cached");
        assertEquals(AssetLibrary.LIGHTING, material.getMaterialDef().getAssetName());
        assertNull(material.getParam("NormalMap"));
        assertNull(material.getParam("UseFog"));
        assertEquals(ColorRGBA.Black, material.getParam("Specular").getValue());
        Texture texture = (Texture) material.getParam("DiffuseMap").getValue();
        assertEquals("textures/classic/zap-2000-zapper-color.png", texture.getKey().getName());
        assertEquals(Texture.MagFilter.Nearest, texture.getMagFilter());
        assertEquals(Texture.MinFilter.NearestNoMipMaps, texture.getMinFilter());
        Image image = texture.getImage();
        assertEquals(Image.Format.BGR8, image.getFormat(), "RGB, no alpha");
        assertEquals(ColorSpace.sRGB, image.getColorSpace());
        assertEquals(0x00AAAA, texel(image, 32, 32), "the body in The Sacrifice's colours (row 0 at v = 0)");
        assertEquals(0x00AAAA, texel(image, 127, 32), "no edge line");
        assertFalse(classic.missingTexture(), "no warning");
    }

    /** Rework: a missing classic texture is logged once and the part keeps the Remake look; no crash. */
    @Test
    void aMissingClassicTextureFallsBackToTheRemakeMaterial() {
        AssetLibrary assets = assets();
        ClassicMaterials classic = new ClassicMaterials(assets, "textures/no-such-dir/");
        MeshPart body = part(assets, ObjectType.ZAP, 0);
        MeshPart head = part(assets, ObjectType.FZN, 0);

        assertSame(assets.material(body, true), classic.part(ObjectType.ZAP, body, true));
        assertTrue(classic.missingTexture(), "warned");
        assertSame(assets.material(head, false), classic.part(ObjectType.FZN, head, false));
    }

    /** Final review T5 M1: a part's tint is an sRGB colour, a material colour is linear (the gamma rule). */
    @Test
    void aPartsTintIsConvertedFromSrgbToLinear() {
        AssetLibrary assets = assets();
        ClassicMaterials classic = new ClassicMaterials(assets);
        MeshPart body = part(assets, ObjectType.ZAP, 0);
        MeshPart grey = new MeshPart(body.mesh(), body.texture(), body.normalMap(), body.render(), 0x808080,
                body.specular(), body.lighting());

        ColorRGBA diffuse = (ColorRGBA) classic.part(ObjectType.ZAP, grey, false).getParam("Diffuse").getValue();

        assertEquals(SrgbColours.toLinear(0x80 / 255f), diffuse.r, 1e-6f);
        assertEquals(SrgbColours.toLinear(0x80 / 255f), diffuse.b, 1e-6f);
        assertEquals(1f, diffuse.a, 1e-6f);
    }

    @Test
    void glassStaysTranslucentAndUnlitEffectPartsKeepTheLibrarysMaterial() {
        AssetLibrary assets = assets();
        ClassicMaterials classic = new ClassicMaterials(assets);
        MeshPart glass = part(assets, ObjectType.PNE, 1);
        MeshPart beam = part(assets, ObjectType.CVN, 1);

        Material material = classic.part(ObjectType.PNE, glass, false);

        assertEquals(BlendMode.Alpha, material.getAdditionalRenderState().getBlendMode());
        assertEquals(MaterialTuning.GLASS_ALPHA, ((ColorRGBA) material.getParam("Diffuse").getValue()).a, 1e-6f);
        assertNotSame(assets.material(glass, false), material);
        assertSame(assets.material(beam, false), classic.part(ObjectType.CVN, beam, false), "the beam");
        assertFalse(classic.missingTexture());
    }

    @Test
    void anObjectViewTakesItsMaterialsFromTheScenesMaterials() {
        AssetLibrary assets = assets();
        ClassicMaterials classic = new ClassicMaterials(assets);
        WorldState world = WorldState.load(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.FZN).build(),
                SimOptions.DEFAULT);
        SimObject robot = world.objects().stream().filter(o -> o.type() == ObjectType.FZN).findFirst().orElseThrow();

        ObjectView view = new ObjectView(assets, classic, robot);

        Geometry body = (Geometry) ((Node) view.node().getChild("group-1")).getChild(0);
        assertSame(classic.part(ObjectType.FZN, part(assets, ObjectType.FZN, 0), false), body.getMaterial());
    }
}
