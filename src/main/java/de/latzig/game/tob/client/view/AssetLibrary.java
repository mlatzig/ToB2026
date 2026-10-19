package de.latzig.game.tob.client.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.font.BitmapFont;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.texture.Texture;

import de.latzig.game.tob.client.logic.VertexColours;
import de.latzig.game.tob.client.logic.audio.SoundBank;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.core.io.ElementLibraryReader;
import de.latzig.game.tob.core.io.FxAtlasReader;
import de.latzig.game.tob.core.io.GuiAtlasReader;
import de.latzig.game.tob.core.io.MeshReader;
import de.latzig.game.tob.core.io.SoundCatalogReader;
import de.latzig.game.tob.core.model.BaseTexture;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.ElementLibrary;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.GuiAtlas;
import de.latzig.game.tob.core.model.Lighting;
import de.latzig.game.tob.core.model.MeshData;
import de.latzig.game.tob.core.model.MeshGroup;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.RenderType;
import de.latzig.game.tob.core.model.StaticQuadrant;

/**
 * Loads the generated assets below one root directory: {@code elements.json}, {@code .tobmesh} files, textures, the
 * FX atlas, the ToB 2009 bitmap font and GUI atlas, and builds the materials (spec §3):
 * <ul>
 * <li>lit parts on {@code Lighting.j3md} with the normal map (green flipped once by the converter, so jME's default
 * {@code NormalType}), specular highlights only where {@link MeshPart#specular()} (Phase 4b-4: ToB 2005's
 * {@code RenderBS} weight and exponent, {@link MaterialTuning#GLOSS_SPECULAR}, {@link MaterialTuning#GLOSS_SHININESS});
 * </li>
 * <li>glass alpha-blended, both sides, no depth write, for the transparent bucket (back-to-front sorting); lit glass
 * approximates ToB 2005's {@code RenderABS} (Phase 4b-4 decision 5);</li>
 * <li>unlit parts on the project's {@link #UNLIT_FOG} (jME's {@code Unshaded.j3md} with colour-only fog, Phase
 * 4b-4), blended the same way (Phase 1c carry-over 3).</li>
 * </ul>
 * Phase 4b-4 decision 2: every world material, lit or unlit, gets ToB 2005's linear horizon fog with one shared fog
 * colour ({@link #setFogColour}, set per tower) and range ({@link MaterialTuning#FOG_START} to
 * {@link MaterialTuning#FOG_END}); the sky dome, the stars ({@link #spriteMaterial}) and the moon stay unfogged.
 * Textures keep their top row first (Direct3D UV origin, no V flip). Phase 4a: the ToB 2005 texture set: the
 * {@code -2005} variants are used where one exists (STATIC, prism glass, sky, ground). Phase 5a (spec decision 1)
 * removed the developer toggle F10 to ToB 2009's alternative textures; what is drawn is exactly the former default.
 * Every missing or unreadable file throws {@link AssetLoadException} with the file name. Works without a GPU: nothing
 * here touches the renderer.
 *
 * <p>World textures (elements, STATIC and lift, sky, ground, socket, the moon images) are minified
 * trilinearly with mipmaps that jME generates on the GPU, and filtered anisotropically ({@link #ANISOTROPY}): jME's
 * default {@code BilinearNoMipMaps} turned distant floors, socket and walkways into shimmering "pixel salad"
 * (acceptance 2026-10-10). The atlases (GUI and FX) stay without mipmaps, so neighbouring regions never bleed into
 * each other; the font and the noise overlay do not come from here.
 *
 * <p>Phase 4b-1: floors, boxes and lifts use the STATIC atlas split into one repeated texture per quadrant
 * ({@link #staticMaterial}); the walkable tops always show the ToB 2009 slabs with ToB 2005's detail map, the other
 * faces the ToB 2005 set.
 */
public final class AssetLibrary {

    public static final String ELEMENTS = "elements.json";
    public static final String FX_ATLAS = "fx/fx-atlas.json";
    public static final String FONT = "gui/tob-font.fnt";
    public static final String GUI_ATLAS = "gui/gui-atlas.json";
    /** Phase 4c: ToB 2005's sounds ({@link #soundBank}). */
    public static final String SOUNDS = "sounds/sounds.json";
    public static final String VARIANT_2005 = "-2005";
    /**
     * Phase 4b-4: the project's unlit material ({@code src/main/resources}, loaded from the classpath): jME 3.9's
     * {@code Unshaded.j3md} with {@code Lighting.j3md}'s linear fog on the colour only, so blended sprites keep their
     * alpha.
     */
    public static final String UNLIT_FOG = "MatDefs/Tob/UnlitFog.j3md";
    /**
     * Phase 5e shadow fix (author decision 2026-10-16): the project's lit material for every lit, shadow-receiving
     * surface, jME 3.9's {@code Lighting.j3md} with the ToB post-shadow shaders (normal-offset lookup by
     * {@link MaterialTuning#SHADOW_NORMAL_OFFSET}, faces turned away from the sun in full shadow); the moon and the
     * glass keep jME's ({@link #JME_LIGHTING}).
     */
    public static final String LIGHTING = "MatDefs/Tob/Lighting.j3md";
    /**
     * jME 3.9's own lit material: the translucent glass parts keep its post-shadow pass (author decision 2026-10-16:
     * translucent materials unaffected by the shadow fix), as does the moon.
     */
    public static final String JME_LIGHTING = "Common/MatDefs/Light/Lighting.j3md";
    /** Phase 4b-4: the terrain material, ToB 2005's {@code Ground} shader on {@code Lighting.j3md} (project). */
    public static final String TERRAIN = "MatDefs/Tob/Terrain.j3md";
    /** ToB 2005's ground detail texture ({@code SKY_TEXID_GROUNDMULTI} 9102), tiled 20 times on the ground's tiles. */
    public static final String GROUND_DETAIL = "textures/9102-groundmulti-color.png";
    /** ToB 2005's mountain texture ({@code SKY_TEXID_GROUND2} 9103). */
    public static final String GROUND_MOUNTAIN = "textures/9103-ground2-color.png";
    /**
     * The anisotropic filtering level of world textures ({@code Texture.setAnisotropicFilter}, passed to OpenGL as
     * {@code GL_TEXTURE_MAX_ANISOTROPY}, which every common GPU supports up to 16). Set per texture rather than via
     * {@code Renderer.setDefaultAnisotropicFilter}, so the library stays renderer-free and the atlases keep none.
     */
    public static final int ANISOTROPY = 8;
    /** Phase 5e: the fog's start without fog, in fields: far beyond every camera's far plane. */
    static final float NO_FOG = 100_000f;

    private record PartKey(MeshPart part, boolean vertexColors) {
    }

    private final AssetManager assets;
    private final Path root;
    private final ElementLibrary elements;
    private final FxAtlas fxAtlas;
    private final Map<String, MeshData> meshes = new HashMap<>();
    private final Map<String, MeshData> objectMeshes = new HashMap<>();
    private final Map<PartKey, Material> materials = new HashMap<>();
    private final Map<String, Material> baseMaterials = new HashMap<>();
    /** The fog colour every fogged material shares (Phase 4b-4); {@link #setFogColour} rewrites it in place. */
    private final ColorRGBA fogColour = new ColorRGBA(0f, 0f, 0f, 1f);
    /** The linear fog range every fogged material shares. */
    private final Vector2f fogRange = new Vector2f(MaterialTuning.FOG_START, MaterialTuning.FOG_END);
    private GuiAtlas guiAtlas;
    private SoundBank soundBank;
    /** Phase 5e: the Classic Look's materials, one set for every Classic scene (the static classic textures). */
    private ClassicMaterials classicMaterials;

    public AssetLibrary(AssetManager assets, Path root) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new AssetLoadException(root.toString(), null);
        }
        assets.registerLocator(this.root.toString(), FileLocator.class);
        this.elements = read(ELEMENTS, () -> new ElementLibraryReader().read(this.root.resolve(ELEMENTS)));
        this.fxAtlas = read(FX_ATLAS, () -> new FxAtlasReader().read(this.root.resolve(FX_ATLAS)));
    }

    /** Phase 5e: the Classic Look's materials, made at the first use and shared by every Classic scene build. */
    public ClassicMaterials classicMaterials() {
        if (classicMaterials == null) {
            classicMaterials = new ClassicMaterials(this);
        }
        return classicMaterials;
    }

    /** The jME asset manager: materials for textures generated at run time (Phase 4b-3: clouds, moon). */
    public AssetManager manager() {
        return assets;
    }

    public Path root() {
        return root;
    }

    public ElementLibrary elements() {
        return elements;
    }

    public FxAtlas fxAtlas() {
        return fxAtlas;
    }

    /**
     * Phase 4c: every sound the client plays, from {@link #SOUNDS} and the client's own entries ({@link SoundBank});
     * read at the first call, then cached. The WAV files are loaded by {@code SoundView}.
     *
     * @throws AssetLoadException if {@code sounds.json} is missing or unreadable
     */
    public SoundBank soundBank() {
        if (soundBank == null) {
            soundBank = read(SOUNDS, () -> SoundBank.of(new SoundCatalogReader().read(root.resolve(SOUNDS))));
        }
        return soundBank;
    }

    public ElementDefinition element(ObjectType type) {
        ElementDefinition definition = elements.elements().get(type);
        if (definition == null) {
            throw new AssetLoadException(ELEMENTS + " (element " + type.code() + ")", null);
        }
        return definition;
    }

    /** Loads every mesh, texture and material of an element, so a missing file shows up before the game starts. */
    public void preload(ObjectType type) {
        for (MeshGroup group : element(type).groups()) {
            for (MeshPart part : group.parts()) {
                material(part, mesh(part.mesh()).hasColors());
            }
        }
    }

    public MeshData mesh(String path) {
        MeshData cached = meshes.get(path);
        if (cached != null) {
            return cached;
        }
        MeshData data = read(path, () -> MeshReader.read(root.resolve(path)));
        meshes.put(path, data);
        return data;
    }

    /**
     * The mesh {@code path} of an object, its vertex colours averaged with {@code objectColour} (0xRRGGBB): ToB 2005's
     * and ToB 2009's rule (Phase 4b-4 spec decision 4, {@link VertexColours}), computed here at load time; a mesh
     * without colours is {@link #mesh} itself. Cached per mesh and colour.
     */
    public MeshData objectMesh(String path, int objectColour) {
        String key = path + "#" + Integer.toHexString(objectColour);
        MeshData cached = objectMeshes.get(key);
        if (cached != null) {
            return cached;
        }
        MeshData data = VertexColours.average(mesh(path), objectColour);
        objectMeshes.put(key, data);
        return data;
    }

    /**
     * The material of a part; {@code vertexColors} when its mesh has colours. Those are averaged with the part's
     * tint ({@link #objectMesh}), so the material colour is then white: the tint counts once, as in ToB 2005. Cached.
     */
    public Material material(MeshPart part, boolean vertexColors) {
        PartKey key = new PartKey(part, vertexColors);
        Material cached = materials.get(key);
        if (cached != null) {
            return cached;
        }
        int tint = vertexColors ? 0xFFFFFF : part.tint();
        Material material;
        if (part.lighting() == Lighting.UNLIT) {
            material = fogged(new Material(assets, UNLIT_FOG));
            bind(material, "ColorMap", part.texture());
            material.setColor("Color", color(tint));
            material.setBoolean("VertexColor", vertexColors);
        } else {
            boolean glass = part.render() == RenderType.GLASS;
            // shadow fix: translucent glass keeps jME's post-shadow pass (author: translucent parts unaffected)
            material = lit(glass ? JME_LIGHTING : LIGHTING, part.texture(), part.normalMap(), tint, part.specular(),
                    vertexColors);
            if (glass) {
                glass(material);
            }
        }
        if (isTransparent(part)) {
            material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
            material.getAdditionalRenderState().setDepthWrite(false);
        }
        materials.put(key, material);
        return material;
    }

    /**
     * Material of a base texture of {@code elements.json}: {@code static} and {@code lift} are lit with vertex
     * colours (the tower pattern colours); {@code sky} and {@code ground} are unshaded and tinted by vertex colours.
     */
    public Material baseMaterial(String name) {
        Material cached = baseMaterials.get(name);
        if (cached != null) {
            return cached;
        }
        BaseTexture base = elements.base().get(name);
        if (base == null) {
            throw new AssetLoadException(ELEMENTS + " (base " + name + ")", null);
        }
        Material material;
        if (base.normalMap() != null) {
            material = lit(LIGHTING, base.texture(), base.normalMap(), 0xFFFFFF, false, true);
        } else {
            material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            bind(material, "ColorMap", base.texture());
            material.setBoolean("VertexColor", true);
        }
        baseMaterials.put(name, material);
        return material;
    }

    /**
     * A lit material ({@code Lighting.j3md}) on a base texture of {@code elements.json} with vertex colours (and its
     * normal map, where the base has one): the terrain ({@code ground}) and the socket ({@code playground}) take the
     * sun's light and shadows (Phase 4a). Cached; the texture follows the developer toggle like every other one.
     */
    public Material litBaseMaterial(String name) {
        String key = "lit:" + name;
        Material cached = baseMaterials.get(key);
        if (cached != null) {
            return cached;
        }
        BaseTexture base = elements.base().get(name);
        if (base == null) {
            throw new AssetLoadException(ELEMENTS + " (base " + name + ")", null);
        }
        Material material = lit(LIGHTING, base.texture(), base.normalMap(), 0xFFFFFF, false, true);
        baseMaterials.put(key, material);
        return material;
    }

    /**
     * The terrain's material (Phase 4b-4 spec decision 1): {@link #TERRAIN}, ToB 2005's {@code Ground} shader on jME's
     * Phong lighting with vertex colours, the shadow techniques and the shared fog. The ground texture
     * ({@code ground}, its ToB 2005 variant like every other texture) times the detail texture {@link #GROUND_DETAIL}
     * ({@link MaterialTuning#GROUND_DETAIL_TILES} times on top of the ground's tiles), blended into the mountain
     * texture {@link #GROUND_MOUNTAIN} by the weights in the mesh's second texture coordinates. Cached.
     *
     * @throws AssetLoadException if the ground base entry or one of the three textures is missing
     */
    public Material terrainMaterial() {
        Material cached = baseMaterials.get(TERRAIN);
        if (cached != null) {
            return cached;
        }
        BaseTexture base = elements.base().get("ground");
        if (base == null) {
            throw new AssetLoadException(ELEMENTS + " (base ground)", null);
        }
        Material material = new Material(assets, TERRAIN);
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White);
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Specular", ColorRGBA.Black);
        material.setBoolean("UseVertexColor", true);
        bind(material, "DiffuseMap", base.texture());
        material.setTexture("DetailMap", texture(GROUND_DETAIL));
        material.setTexture("MountainMap", texture(GROUND_MOUNTAIN));
        material.setFloat("DetailScale", MaterialTuning.GROUND_DETAIL_TILES);
        fogged(material);
        baseMaterials.put(TERRAIN, material);
        return material;
    }

    /**
     * Phase 4b-1 spec §2 decisions 7 and 8: the lit material (vertex colours) of one STATIC quadrant texture, with the
     * world filters and {@code WrapMode.Repeat}. {@link StaticQuadrant#TOP}, the walkable tops of floors, boxes and
     * lift platforms, always uses the ToB 2009 slabs and their strengthened normal map (never a ToB 2005 variant), and
     * ToB 2005's detail map ({@link StaticQuadrant#DETAIL_BASE}, if converted) as a {@code LightMap} on the second
     * texture coordinates ({@code SeparateTexCoord}), which multiplies the colour as ToB 2005's {@code RenderBM}
     * pixel shader does. The other quadrants are side faces and bottoms and use the ToB 2005 set like every other
     * texture;
     * since Phase 4b-4 (decision 3) they carry the same detail map, as ToB 2005 draws every static face with
     * {@code RenderBM}. Cached.
     *
     * @throws AssetLoadException if a quadrant's base entry or texture is missing
     */
    public Material staticMaterial(StaticQuadrant quadrant) {
        String key = quadrant.baseName();
        Material cached = baseMaterials.get(key);
        if (cached != null) {
            return cached;
        }
        BaseTexture base = elements.base().get(key);
        if (base == null) {
            throw new AssetLoadException(ELEMENTS + " (base " + key + ")", null);
        }
        Material material;
        if (quadrant == StaticQuadrant.TOP) {
            material = litBlank(LIGHTING, 0xFFFFFF, false, true);
            material.setTexture("DiffuseMap", worldTexture(base.texture()));
            if (base.normalMap() != null) {
                material.setTexture("NormalMap", worldTexture(base.normalMap()));
            }
        } else {
            material = lit(LIGHTING, base.texture(), base.normalMap(), 0xFFFFFF, false, true);
        }
        BaseTexture detail = elements.base().get(StaticQuadrant.DETAIL_BASE);
        if (detail != null) {
            material.setTexture("LightMap", worldTexture(detail.texture()));
            material.setBoolean("SeparateTexCoord", true);
        }
        baseMaterials.put(key, material);
        return material;
    }

    /**
     * A new unshaded, alpha-blended sprite material on the image {@code path} (clamped, vertex colours, both sides,
     * no depth write): the stars (Phase 4a), unfogged (Phase 4b-4). Not cached.
     * The FX atlas (the stars) keeps {@code BilinearNoMipMaps} like the GUI atlas; other images get the world filters.
     */
    public Material spriteMaterial(String path) {
        Texture texture;
        if (path.equals(fxAtlas.image())) {
            texture = loadTexture(resolve(path));
            atlasFilters(texture);
        } else {
            texture = texture(path);
        }
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.setBoolean("VertexColor", true);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    /**
     * A new fogged sprite material on the FX atlas for the world's effects (Phase 4b-4 decision 2: lightning bolts
     * and their ground debris, meteor trails, explosion sprites): as {@link #spriteMaterial} on the atlas (clamped,
     * no mipmaps, vertex colours, alpha-blended, both sides, no depth write), but on {@link #UNLIT_FOG} with the
     * shared fog. Not cached.
     */
    public Material fxMaterial() {
        Texture texture = loadTexture(resolve(fxAtlas.image()));
        atlasFilters(texture);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        Material material = fogged(new Material(assets, UNLIT_FOG));
        material.setTexture("ColorMap", texture);
        material.setBoolean("VertexColor", true);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    /** A new fogged {@link #UNLIT_FOG} material without a texture: the clouds (Phase 4b-4, ToB 2005 sky.cpp:831). */
    public Material unlitFogMaterial() {
        return fogged(new Material(assets, UNLIT_FOG));
    }

    /**
     * Sets the fog colour of every fogged material, built so far or later (Phase 4b-4 decision 2: ToB 2005 fogs
     * towards {@code SKY_getFogColor}, the tower's horizon colour): the materials share one colour object, so a tower
     * start only rewrites it.
     */
    public void setFogColour(int rgb) {
        fogColour.set(color(rgb));
    }

    /** The fog colour every fogged material shares (read only). */
    public ColorRGBA fogColour() {
        return fogColour;
    }

    /**
     * Phase 5e (Classic Look, decision 1: no distance fog): turns the shared fog on (the Phase 4b-4 range) or off (a
     * range beyond every camera's far plane) for every fogged material, built so far or later; each scene build sets
     * it, as it sets the fog colour.
     */
    public void setFog(boolean on) {
        fogRange.set(on ? MaterialTuning.FOG_START : NO_FOG, on ? MaterialTuning.FOG_END : 2f * NO_FOG);
    }

    /** Whether the shared fog is on (tests). */
    public boolean fog() {
        return fogRange.y == MaterialTuning.FOG_END;
    }

    /**
     * A new alpha-blended, fogged material on the generated ground haze texture ({@link BaseTexture#HAZE}, Phase
     * 4b-2): world filters, repeated, both sides, no depth write; its {@code Color} (white at first) carries the
     * brightness pulse. Not cached, not switched by the developer toggle (there is no ToB 2009 variant).
     *
     * @throws AssetLoadException if the base entry or its texture is missing
     */
    public Material hazeMaterial() {
        BaseTexture base = elements.base().get(BaseTexture.HAZE);
        if (base == null) {
            throw new AssetLoadException(ELEMENTS + " (base " + BaseTexture.HAZE + ")", null);
        }
        Material material = fogged(new Material(assets, UNLIT_FOG));
        material.setTexture("ColorMap", worldTexture(base.texture()));
        material.setColor("Color", ColorRGBA.White.clone());
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    /** Glass and unlit parts go to the transparent bucket. */
    public static boolean isTransparent(MeshPart part) {
        return part.render() == RenderType.GLASS || part.lighting() == Lighting.UNLIT;
    }

    /** A world texture (trilinear, anisotropic, repeated) for {@code path}, its {@code -2005} variant while on. */
    public Texture texture(String path) {
        return worldTexture(resolve(path));
    }

    private Texture worldTexture(String resolved) {
        Texture texture = loadTexture(resolved);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(ANISOTROPY);
        return texture;
    }

    /** No mipmaps and no anisotropy (level 1; 0 would mean the renderer's default): atlas regions never bleed. */
    private static void atlasFilters(Texture texture) {
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(1);
    }

    private Texture loadTexture(String resolved) {
        if (!Files.isRegularFile(root.resolve(resolved))) {
            throw new AssetLoadException(resolved, null);
        }
        try {
            Texture texture = assets.loadTexture(new TextureKey(resolved, false));
            texture.setWrap(Texture.WrapMode.Repeat);
            return texture;
        } catch (RuntimeException e) {
            throw new AssetLoadException(resolved, e);
        }
    }

    public BitmapFont font() {
        if (!Files.isRegularFile(root.resolve(FONT))) {
            throw new AssetLoadException(FONT, null);
        }
        try {
            return assets.loadFont(FONT);
        } catch (RuntimeException e) {
            throw new AssetLoadException(FONT, e);
        }
    }

    /** The ToB 2009 GUI atlas (panel buttons, menu skin, cursor), read on first use. */
    public GuiAtlas guiAtlas() {
        if (guiAtlas == null) {
            guiAtlas = read(GUI_ATLAS, () -> new GuiAtlasReader().read(root.resolve(GUI_ATLAS)));
        }
        return guiAtlas;
    }

    /**
     * A new unshaded, alpha-blended material on the GUI atlas image for 2D sprites: clamped and without mipmaps, so
     * neighbouring regions never bleed into each other. Its {@code Color} tints the sprite.
     */
    public Material guiMaterial() {
        Texture texture = loadTexture(guiAtlas().image());
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        atlasFilters(texture);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        return material;
    }

    /** The file actually loaded for {@code path}: its {@code -2005} variant if one exists. */
    String resolve(String path) {
        if (!path.endsWith(".png")) {
            return path;
        }
        String variant = path.substring(0, path.length() - ".png".length()) + VARIANT_2005 + ".png";
        return Files.isRegularFile(root.resolve(variant)) ? variant : path;
    }

    private Material lit(String definition, String texture, String normalMap, int tint, boolean specular,
            boolean vertexColors) {
        Material material = litBlank(definition, tint, specular, vertexColors);
        bind(material, "DiffuseMap", texture);
        if (normalMap != null) {
            bind(material, "NormalMap", normalMap);
        }
        return material;
    }

    /**
     * Phase 4b-4 decision 5: ToB 2005's glass shader ({@code RenderABS}: light = (4 (N·H)^16 + N·L) / 2, colour + 0.5,
     * alpha + 0.2) approximated on {@code Lighting.j3md}: the highlight's weight and exponent
     * ({@link MaterialTuning#GLASS_SPECULAR}, {@link MaterialTuning#GLASS_SHININESS}), the brightening as a raised
     * ambient colour ({@link MaterialTuning#GLASS_AMBIENT}) and the alpha as the diffuse colour's alpha
     * ({@link MaterialTuning#GLASS_ALPHA}, which jME multiplies with the texture's alpha).
     */
    private static void glass(Material material) {
        material.setColor("Ambient", grey(MaterialTuning.GLASS_AMBIENT));
        material.setColor("Specular", grey(MaterialTuning.GLASS_SPECULAR));
        material.setFloat("Shininess", MaterialTuning.GLASS_SHININESS);
        ColorRGBA diffuse = ((ColorRGBA) material.getParamValue("Diffuse")).clone();
        diffuse.a = MaterialTuning.GLASS_ALPHA;
        material.setColor("Diffuse", diffuse);
    }

    private static ColorRGBA grey(float value) {
        return new ColorRGBA(value, value, value, 1f);
    }

    /** Turns on the shared linear fog (Phase 4b-4): {@code Lighting.j3md}'s or {@link #UNLIT_FOG}'s parameters. */
    private Material fogged(Material material) {
        material.setBoolean("UseFog", true);
        material.setColor("FogColor", fogColour);
        material.setVector2("LinearFog", fogRange);
        return material;
    }

    /**
     * {@code definition} ({@link #LIGHTING}, {@link #JME_LIGHTING} for glass) with the material colours of every lit
     * part and the fog, still without textures.
     */
    private Material litBlank(String definition, int tint, boolean specular, boolean vertexColors) {
        Material material = new Material(assets, definition);
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White);
        material.setColor("Diffuse", color(tint));
        material.setColor("Specular", specular ? grey(MaterialTuning.GLOSS_SPECULAR) : ColorRGBA.Black);
        material.setFloat("Shininess", specular ? MaterialTuning.GLOSS_SHININESS : 1f);
        material.setBoolean("UseVertexColor", vertexColors);
        return fogged(material);
    }

    private void bind(Material material, String parameter, String path) {
        material.setTexture(parameter, texture(path));
    }

    static ColorRGBA color(int rgb) {
        return new ColorRGBA(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read() throws IOException;
    }

    private <T> T read(String file, Reader<T> reader) {
        if (!Files.isRegularFile(root.resolve(file))) {
            throw new AssetLoadException(file, null);
        }
        try {
            return reader.read();
        } catch (IOException | RuntimeException e) {
            throw new AssetLoadException(file, e);
        }
    }
}
