package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;

import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.core.io.ElementLibraryWriter;
import de.latzig.game.tob.core.io.FxAtlasWriter;
import de.latzig.game.tob.core.model.BaseTexture;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;
import de.latzig.game.tob.core.model.StaticQuadrant;

class AssetLibraryTest {

    private static String textureName(Material material, String parameter) {
        return material.getTextureParam(parameter).getTextureValue().getKey().getName();
    }

    @Test
    void loadsAllThirtySixElementsWithTheirMeshesAndTexturesWithoutAGpu() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        int parts = 0;

        for (ObjectType type : ObjectType.values()) {
            library.preload(type);
            for (MeshGroup group : library.element(type).groups()) {
                for (MeshPart part : group.parts()) {
                    parts++;
                    assertTrue(library.mesh(part.mesh()).keyCount() >= 1, part.mesh());
                    Material material = library.material(part, library.mesh(part.mesh()).hasColors());
                    String definition = material.getMaterialDef().getAssetName();
                    if (part.lighting() == Lighting.UNLIT) {
                        assertEquals(AssetLibrary.UNLIT_FOG, definition, part.mesh());
                    } else {
                        String lit = part.render() == RenderType.GLASS ? AssetLibrary.JME_LIGHTING
                                : AssetLibrary.LIGHTING;
                        assertEquals(lit, definition, part.mesh());
                        assertEquals(library.resolve(part.texture()), textureName(material, "DiffuseMap"));
                    }
                    assertEquals(AssetLibrary.isTransparent(part) ? BlendMode.Alpha : BlendMode.Off,
                            material.getAdditionalRenderState().getBlendMode(), part.mesh());
                }
            }
        }

        assertEquals(36, library.elements().elements().size());
        assertEquals(84, parts);
        assertEquals(16, library.fxAtlas().sprites().size());
        assertNotNull(library.font());
    }

    @Test
    void theToB2005TexturesAreTheDefaultSet() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());

        assertEquals("textures/1000-static-color-2005.png",
                textureName(library.baseMaterial("static"), "DiffuseMap"));
        assertEquals("textures/9100-sky-color-2005.png", textureName(library.baseMaterial("sky"), "ColorMap"));
        assertEquals("textures/9101-ground-color-2005.png",
                textureName(library.baseMaterial("ground"), "ColorMap"));
    }

    /** Phase 4c: ToB 2005's 24 sounds, the pushed elements' push and the 4 generated sounds; every file exists. */
    @Test
    void theSoundBankHoldsEverySoundAndEveryFileExists() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());

        SoundBank bank = library.soundBank();

        assertEquals(29, bank.sounds().size());
        assertSame(bank, library.soundBank(), "cached");
        for (SoundBank.Sound sound : bank.sounds().values()) {
            assertTrue(Files.isRegularFile(GeneratedAssets.root().resolve(sound.file())), sound.file());
        }
        assertEquals(1f, bank.sound(SoundBank.EXPLOSION).refDistance(), "32 ToB 2005 units");
        assertEquals(256f, bank.sound(SoundBank.EXPLOSION).maxDistance(), "8192 ToB 2005 units");
    }

    private static Texture texture(Material material, String parameter) {
        return (Texture) material.getTextureParam(parameter).getTextureValue();
    }

    private static void assertWorldFilters(Texture texture) {
        String name = texture.getKey().getName();
        assertEquals(Texture.MinFilter.Trilinear, texture.getMinFilter(), name);
        assertEquals(Texture.MagFilter.Bilinear, texture.getMagFilter(), name);
        assertEquals(AssetLibrary.ANISOTROPY, texture.getAnisotropicFilter(), name);
    }

    /**
     * Phase 5a (spec decision 1): the F10 toggle is gone, and what is rendered is exactly the former default (the
     * ToB 2005 set): every element texture and normal map and every base texture resolves to its {@code -2005}
     * variant if that file exists, else to itself.
     */
    @Test
    void everyTextureResolvesToItsToB2005VariantWhereOneExists() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        List<String> paths = new ArrayList<>();
        for (ObjectType type : ObjectType.values()) {
            for (MeshGroup group : library.element(type).groups()) {
                for (MeshPart part : group.parts()) {
                    paths.add(part.texture());
                    if (part.normalMap() != null) {
                        paths.add(part.normalMap());
                    }
                }
            }
        }
        for (BaseTexture base : library.elements().base().values()) {
            paths.add(base.texture());
            if (base.normalMap() != null) {
                paths.add(base.normalMap());
            }
        }
        int variants = 0;

        for (String path : paths) {
            String variant = path.substring(0, path.length() - ".png".length()) + "-2005.png";
            boolean exists = Files.isRegularFile(GeneratedAssets.root().resolve(variant));
            variants += exists ? 1 : 0;
            assertEquals(exists ? variant : path, library.resolve(path), path);
        }
        assertTrue(variants >= 5, "STATIC, prism glass, sky and ground have ToB 2005 variants: " + variants);
    }

    /**
     * Acceptance 2026-10-10: distant floors, socket and walkways shimmered ("pixel salad") because jME minifies
     * without mipmaps by default; every world texture is trilinear and anisotropic, while the GUI and FX atlases stay
     * without mipmaps so neighbouring regions never bleed.
     */
    @Test
    void worldTexturesAreMipmappedAndAnisotropicButTheGuiAtlasIsNot() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        Material floor = library.baseMaterial("static");
        MeshPart zapper = library.element(ObjectType.ZAP).groups().get(0).parts().get(0);

        assertWorldFilters(texture(floor, "DiffuseMap"));
        assertWorldFilters(texture(floor, "NormalMap"));
        assertWorldFilters(texture(library.baseMaterial("sky"), "ColorMap"));
        assertWorldFilters(texture(library.litBaseMaterial("ground"), "DiffuseMap"));
        assertWorldFilters(texture(library.litBaseMaterial("playground"), "DiffuseMap"));
        assertWorldFilters(texture(library.material(zapper, false), "DiffuseMap"));
        assertWorldFilters(texture(library.spriteMaterial("textures/1000-static-color.png"), "ColorMap"));
        assertEquals("textures/1000-static-color-2005.png", textureName(floor, "DiffuseMap"));

        Texture gui = texture(library.guiMaterial(), "ColorMap");
        assertEquals(Texture.MinFilter.BilinearNoMipMaps, gui.getMinFilter());
        assertEquals(Texture.WrapMode.EdgeClamp, gui.getWrap(Texture.WrapAxis.S));
        assertTrue(gui.getAnisotropicFilter() <= 1, "no anisotropic filtering on the GUI atlas");
        Texture stars = texture(library.spriteMaterial(library.fxAtlas().image()), "ColorMap");
        assertEquals(Texture.MinFilter.BilinearNoMipMaps, stars.getMinFilter(), "the FX atlas (stars) as well");
        assertTrue(stars.getAnisotropicFilter() <= 1);
    }

    /**
     * Phase 4b-1 spec §2 decisions 7 and 8: the walkable tops always use the ToB 2009 slabs (strengthened normals,
     * ToB 2005's detail map as a light map on the second texture coordinates); the sides use the ToB 2005 set. Every
     * quadrant texture repeats and has the world filters.
     */
    @Test
    void theWalkableTopsKeepTheToB2009SlabsWithTheDetailMapWhileTheSidesUseToB2005() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        Material top = library.staticMaterial(StaticQuadrant.TOP);
        Material side = library.staticMaterial(StaticQuadrant.SIDE_A);

        assertEquals("textures/1000-static-top-color.png", textureName(top, "DiffuseMap"));
        assertEquals("textures/1001-static-top-normal.png", textureName(top, "NormalMap"));
        assertEquals("textures/1002-static-detail.png", textureName(top, "LightMap"));
        assertEquals(Boolean.TRUE, top.getParam("SeparateTexCoord").getValue());
        assertEquals("textures/1000-static-side-a-color-2005.png", textureName(side, "DiffuseMap"));
        assertEquals("textures/1001-static-side-a-normal-2005.png", textureName(side, "NormalMap"));
        for (Texture texture : List.of(texture(top, "DiffuseMap"), texture(top, "NormalMap"),
                texture(top, "LightMap"), texture(side, "DiffuseMap"), texture(side, "NormalMap"))) {
            assertWorldFilters(texture);
            assertEquals(Texture.WrapMode.Repeat, texture.getWrap(Texture.WrapAxis.S), texture.getKey().getName());
            assertEquals(Texture.WrapMode.Repeat, texture.getWrap(Texture.WrapAxis.T), texture.getKey().getName());
        }
        assertSame(top, library.staticMaterial(StaticQuadrant.TOP), "cached");
        assertEquals(512, texture(top, "DiffuseMap").getImage().getWidth(), "a quadrant of the 1024 atlas");
    }

    /**
     * Phase 4b-4 decision 3, ToB 2005's {@code RenderBM}: every static face, not only the walkable tops, multiplies
     * its colour by the STATIC detail map ({@code 1002}) on the second texture coordinates.
     */
    @Test
    void theSidesAndBottomsCarryTheStaticDetailMapLikeTheTops() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());

        for (StaticQuadrant quadrant : StaticQuadrant.values()) {
            Material material = library.staticMaterial(quadrant);
            assertEquals(StaticQuadrant.DETAIL_TEXTURE, textureName(material, "LightMap"), quadrant.name());
            assertEquals(Boolean.TRUE, material.getParamValue("SeparateTexCoord"), quadrant.name());
            assertWorldFilters(texture(material, "LightMap"));
            assertEquals(Texture.WrapMode.Repeat, texture(material, "LightMap").getWrap(Texture.WrapAxis.S));
        }
        assertEquals("textures/1000-static-side-b-color-2005.png",
                textureName(library.staticMaterial(StaticQuadrant.SIDE_B), "DiffuseMap"));
    }

    /**
     * Phase 4b-4 decision 5: glossy parts (ToB 2005 FX 3, {@code RenderBS}) get a half-weight highlight with the
     * exponent 16; lit glass (FX 4, {@code RenderABS}) a double-weight highlight, a raised ambient colour for ToB
     * 2005's brightening and a scaled alpha; matte parts and the unlit glass effects keep their look.
     */
    @Test
    void glossAndGlassFollowToB2005sSpecularShaders() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        Material zapper = library.material(library.element(ObjectType.ZAP).groups().get(0).parts().get(0), true);
        Material enemy = library.material(library.element(ObjectType.FZN).groups().get(0).parts().get(0), false);
        MeshPart prismGlass = library.element(ObjectType.PNE).groups().get(0).parts().get(1);
        Material glass = library.material(prismGlass, false);
        Material beam = library.material(library.element(ObjectType.CVN).groups().get(0).parts().get(1), false);

        assertEquals(new ColorRGBA(0.5f, 0.5f, 0.5f, 1f), zapper.getParamValue("Specular"), "half weight");
        assertEquals(16f, (Float) zapper.getParamValue("Shininess"), 0f, "(N·H)^16");
        assertEquals(ColorRGBA.Black, enemy.getParamValue("Specular"), "FX 2: no highlight");
        assertEquals(RenderType.GLASS, prismGlass.render());
        assertEquals(new ColorRGBA(2f, 2f, 2f, 1f), glass.getParamValue("Specular"));
        assertEquals(16f, (Float) glass.getParamValue("Shininess"), 0f);
        assertEquals(new ColorRGBA(1.25f, 1.25f, 1.25f, 1f), glass.getParamValue("Ambient"), "+0.5 over 0.4 ambient");
        assertEquals(0.7f, ((ColorRGBA) glass.getParamValue("Diffuse")).a, 0f, "the alpha factor");
        assertEquals(1f, ((ColorRGBA) glass.getParamValue("Diffuse")).r, 0f, "the colour stays white");
        assertEquals(BlendMode.Alpha, glass.getAdditionalRenderState().getBlendMode());
        assertEquals(AssetLibrary.UNLIT_FOG, beam.getMaterialDef().getAssetName(), "the unlit beam is no lit glass");
        assertNull(beam.getParamValue("Ambient"));
    }

    @Test
    void theToB2005VariantsAreUsedWhereTheyExist() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        Material floor = library.baseMaterial("static");
        MeshPart zapper = library.element(ObjectType.ZAP).groups().get(0).parts().get(0);
        Material zap = library.material(zapper, false);

        assertEquals("textures/1000-static-color-2005.png", textureName(floor, "DiffuseMap"));
        assertEquals("textures/1001-static-normal-2005.png", textureName(floor, "NormalMap"));
        assertEquals("textures/2000-zapper-color.png", textureName(zap, "DiffuseMap"), "no 2005 variant");
        assertEquals("textures/9100-sky-color-2005.png", textureName(library.baseMaterial("sky"), "ColorMap"));
    }

    @Test
    void aMissingFileThrowsWithItsName(@TempDir Path root) throws IOException {
        assertEquals("elements.json", assertThrows(AssetLoadException.class,
                () -> new AssetLibrary(GeneratedAssets.assetManager(), root)).file());
        MeshPart missing = new MeshPart("meshes/missing.tobmesh", "textures/missing.png", null, RenderType.OPAQUE,
                0xFFFFFF);
        new ElementLibraryWriter().write(new ElementLibrary(Map.of(ObjectType.ZAP, new ElementDefinition(
                ObjectType.ZAP, List.of(new MeshGroup(1, List.of(missing))), List.of(), "test")), Map.of()),
                root.resolve("elements.json"));
        new FxAtlasWriter().write(new FxAtlas("textures/9995-fx-color.png", 1024, 1024, Map.of(), Map.of(), "test"),
                root.resolve("fx/fx-atlas.json"));
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), root);

        AssetLoadException e = assertThrows(AssetLoadException.class, () -> library.preload(ObjectType.ZAP));

        assertEquals("meshes/missing.tobmesh", e.file());
        assertTrue(e.getMessage().contains("meshes/missing.tobmesh"), e.getMessage());
        assertEquals("elements.json (element PSH)",
                assertThrows(AssetLoadException.class, () -> library.preload(ObjectType.PSH)).file());
    }

    /** An unreadable {@code -2005} variant is a missing asset with its name (spec §6), not a silent fallback. */
    @Test
    void anUnreadableToB2005VariantThrowsWithItsName(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("textures"));
        write(rgb(0x112233), root.resolve("textures/a-color.png"));
        write(rgb(0x445566), root.resolve("textures/a-color-2005.png"));
        write(rgb(0x778899), root.resolve("textures/b-color.png"));
        Files.writeString(root.resolve("textures/b-color-2005.png"), "not a png", StandardCharsets.US_ASCII);
        new ElementLibraryWriter().write(new ElementLibrary(Map.of(),
                Map.of("a", new BaseTexture("textures/a-color.png", null),
                        "b", new BaseTexture("textures/b-color.png", null))),
                root.resolve("elements.json"));
        new FxAtlasWriter().write(new FxAtlas("textures/9995-fx-color.png", 1024, 1024, Map.of(), Map.of(), "test"),
                root.resolve("fx/fx-atlas.json"));
        AssetLibrary library = new AssetLibrary(GeneratedAssets.assetManager(), root);

        assertEquals("textures/a-color-2005.png", textureName(library.baseMaterial("a"), "ColorMap"));
        assertEquals("textures/b-color-2005.png",
                assertThrows(AssetLoadException.class, () -> library.baseMaterial("b")).file());
    }

    private static void write(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }

    private static BufferedImage rgb(int rgb) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, rgb);
        return image;
    }
}
