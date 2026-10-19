package de.latzig.game.tob.client.view;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;

import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;
import de.latzig.game.tob.core.model.StaticQuadrant;

/**
 * Phase 5e (Classic Look, spec §3): the materials of the Classic scenes, one set per {@link AssetLibrary}
 * ({@link AssetLibrary#classicMaterials()}). A lit object part is drawn with its static classic texture
 * {@code textures/classic/<element code>-<texture file>}, which the converter coloured once in the palette of
 * original tower 001 "The Sacrifice" (rework, author 2026-10-16: no per-tower colouring, no edge lines), sampled
 * without filtering ({@code Nearest}, no mipmaps: no colour bleeds across an area border; sRGB like every colour
 * texture), on {@link AssetLibrary#LIGHTING} (the project's {@code Lighting.j3md}) without normal map, specular
 * highlights and fog, lit by the scene's sun and ambient light (decision 6); glass stays translucent
 * ({@link MaterialTuning#GLASS_ALPHA}, on {@link AssetLibrary#JME_LIGHTING} as in the Remake). Unlit effect parts (the
 * converter beam, the exchanger sparkle) keep the library's material, and so does a part whose classic texture is
 * missing (a warning, once). Task 5: the floors, boxes and lifts are their vertex colours (the tower's palette) on
 * one untextured lit material, and the blue pulse is a translucent single-colour surface
 * ({@link MaterialTuning#CLASSIC_PULSE_COLOUR}, decision 2, converted from sRGB to linear).
 */
public final class ClassicMaterials implements SceneMaterials {

    private static final Logger LOG = LoggerFactory.getLogger(ClassicMaterials.class);
    /** The folder of the classic textures below the assets (written by the converter). */
    public static final String TEXTURES = "textures/classic/";

    private record Key(String texture, boolean glass, boolean vertexColors, int tint) {
    }

    private final AssetLibrary assets;
    private final String directory;
    private final Map<Key, Material> materials = new HashMap<>();
    private Material base;
    private boolean missingTexture;

    public ClassicMaterials(AssetLibrary assets) {
        this(assets, TEXTURES);
    }

    /** With the classic textures below {@code directory} instead of {@link #TEXTURES} (tests). */
    ClassicMaterials(AssetLibrary assets, String directory) {
        this.assets = assets;
        this.directory = directory;
    }

    @Override
    public Material part(ObjectType type, MeshPart part, boolean vertexColors) {
        if (part.lighting() == Lighting.UNLIT) {
            return assets.material(part, vertexColors);
        }
        String texture = directory + type.code().toLowerCase(Locale.ROOT) + "-"
                + part.texture().substring(part.texture().lastIndexOf('/') + 1);
        boolean glass = part.render() == RenderType.GLASS;
        int tint = vertexColors ? 0xFFFFFF : part.tint();
        Key key = new Key(texture, glass, vertexColors, tint);
        Material cached = materials.get(key);
        if (cached != null) {
            return cached;
        }
        if (!Files.isRegularFile(assets.root().resolve(texture))) {
            if (!missingTexture) {
                missingTexture = true;
                LOG.warn("Classic texture {} missing (run the AssetConverter): the Remake look's textures instead",
                        texture);
            }
            return assets.material(part, vertexColors);
        }
        // shadow fix: translucent glass keeps jME's post-shadow pass, as in the Remake look
        Material material = new Material(assets.manager(), glass ? AssetLibrary.JME_LIGHTING : AssetLibrary.LIGHTING);
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White);
        float[] rgb = SrgbColours.rgb(tint, true);
        ColorRGBA diffuse = new ColorRGBA(rgb[0], rgb[1], rgb[2], glass ? MaterialTuning.GLASS_ALPHA : 1f);
        material.setColor("Diffuse", diffuse);
        material.setColor("Specular", ColorRGBA.Black);
        material.setFloat("Shininess", 1f);
        material.setBoolean("UseVertexColor", vertexColors);
        Texture map = assets.manager().loadTexture(new TextureKey(texture, false));
        map.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        map.setMagFilter(Texture.MagFilter.Nearest);
        map.setAnisotropicFilter(1);
        material.setTexture("DiffuseMap", map);
        if (glass) {
            material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
            material.getAdditionalRenderState().setDepthWrite(false);
        }
        materials.put(key, material);
        return material;
    }

    /**
     * One untextured lit material with vertex colours for every quadrant (the quadrant is ignored: Classic floors
     * differ only by their vertex colours); the Classic floors carry no texture.
     */
    @Override
    public Material base(StaticQuadrant quadrant) {
        if (base == null) {
            base = new Material(assets.manager(), AssetLibrary.LIGHTING);
            base.setBoolean("UseMaterialColors", true);
            base.setColor("Ambient", ColorRGBA.White);
            base.setColor("Diffuse", ColorRGBA.White);
            base.setColor("Specular", ColorRGBA.Black);
            base.setFloat("Shininess", 1f);
            base.setBoolean("UseVertexColor", true);
        }
        return base;
    }

    /**
     * A new unshaded, alpha-blended single-colour material, both sides, no depth write, no fog; its colour the
     * sRGB {@link MaterialTuning#CLASSIC_PULSE_COLOUR} converted to linear (the gamma rule).
     */
    @Override
    public Material haze() {
        Material material = new Material(assets.manager(), "Common/MatDefs/Misc/Unshaded.j3md");
        float[] rgb = SrgbColours.rgb(MaterialTuning.CLASSIC_PULSE_COLOUR, true);
        material.setColor("Color", new ColorRGBA(rgb[0], rgb[1], rgb[2], MaterialTuning.CLASSIC_PULSE_ALPHA));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    /** Whether a classic texture was missing (and the warning logged; tests). */
    boolean missingTexture() {
        return missingTexture;
    }
}
