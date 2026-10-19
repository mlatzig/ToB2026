package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.CloudTexture;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.SkyDome;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.client.logic.effects.StarField;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.FxSprite;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * The sky around the camera (Phase 4a spec §2 decisions 4 and 5, Phase 4b-3): the dome with the sky texture
 * ({@code sky}, the ToB 2005 variant by default) in the tower's sky colours ({@link SkyDome}), the stars as sprites of
 * the FX atlas cell {@code star} ({@link StarField}), the 3D moon ({@link MoonView} of the tower's
 * {@link MoonDesign}, hazed towards the sky behind it) and the drifting clouds ({@link CloudView}, only for
 * {@code clouds} or {@code heavyClouds} towers), all in the sky bucket in that order, centred on the camera.
 * The moon has its own lights,
 * so this node must not hang below the scene's sun and ambient light. {@code skyStyle} has no effect: neither ToB 2005
 * nor ToB 2009 defines the original's sky styles (report 09 §6). The view only reads. Phase 5e (Classic Look,
 * decisions 1 and 2): the classic sky is the plain palette gradient (no sky texture) with the stars and the moon,
 * without clouds.
 */
public final class SkyView {

    public static final float DOME_RADIUS = 150f;
    /** Stars lie just inside the dome ({@code sky.cpp}: the sky plane scaled by 0.96). */
    public static final float STAR_RADIUS = 0.96f * DOME_RADIUS;
    /** The moon lies inside the stars ({@code sky.cpp}: 0.92). */
    public static final float MOON_RADIUS = 0.92f * DOME_RADIUS;
    public static final String STAR_SPRITE = "star";
    /** The ToB 2009 sun ({@code SourceTowerConstants.SUN_LIGHT_DIRECTION} (−0.5, −0.6, 0.1)), z mirrored. */
    public static final Vector3f SUN_DIRECTION = new Vector3f(-0.5f, -0.6f, -0.1f).normalizeLocal();

    private final Node node = new Node("sky");
    private final int stars;
    private final MoonView moon;
    private final CloudView clouds;

    /** As {@link #SkyView(AssetLibrary, TowerDefinition, TowerColours, SkyOptions)} with the default options. */
    public SkyView(AssetLibrary assets, TowerDefinition tower, TowerColours colours) {
        this(assets, tower, colours, SkyOptions.DEFAULT);
    }

    /**
     * @param options the moon texture detail (spec decision 12) and the developer overrides of the moon
     * @throws AssetLoadException if the sky texture or the FX atlas is missing
     */
    public SkyView(AssetLibrary assets, TowerDefinition tower, TowerColours colours, SkyOptions options) {
        this(assets, tower, colours, options, Optional.empty());
    }

    /**
     * @param options  the moon texture detail (spec decision 12) and the developer overrides of the moon
     * @param prepared the Briefing's preparation of the moon and cloud textures (Phase 5a decision 14); used if it
     *                 belongs to {@code tower}, else the textures are generated here as before
     * @throws AssetLoadException if the sky texture or the FX atlas is missing
     */
    public SkyView(AssetLibrary assets, TowerDefinition tower, TowerColours colours, SkyOptions options,
            Optional<SkyPreparation> prepared) {
        this(assets, tower, colours, options, prepared, false);
    }

    /**
     * @param classic Phase 5e: the Classic Look's sky (the plain gradient, no clouds)
     * @throws AssetLoadException if the sky texture or the FX atlas is missing
     */
    public SkyView(AssetLibrary assets, TowerDefinition tower, TowerColours colours, SkyOptions options,
            Optional<SkyPreparation> prepared, boolean classic) {
        Optional<SkyPreparation> mine = prepared.filter(preparation -> preparation.towerId().equals(tower.id()));
        MeshArrays gradient = SkyDome.arrays(colours, DOME_RADIUS);
        if (classic) {
            gradient = new MeshArrays(gradient.positions(), gradient.uvs(), SrgbColours.linearRgba(gradient.colors()));
        }
        Geometry dome = new Geometry("sky-dome", MeshBuilder.triangles(gradient));
        Material sky = classic ? plainSky(assets) : assets.baseMaterial("sky");
        sky.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        sky.getAdditionalRenderState().setDepthWrite(false);
        dome.setMaterial(sky);
        sky(dome);
        node.attachChild(dome);

        List<StarField.Star> visible = new ArrayList<>();
        for (StarField.Star star : StarField.of(tower)) {
            if (star.aboveHorizon()) {
                visible.add(star);
            }
        }
        stars = visible.size();
        if (!visible.isEmpty()) {
            FxAtlas atlas = assets.fxAtlas();
            Geometry field = new Geometry("stars", MeshBuilder.triangles(starArrays(visible, atlas)));
            field.setMaterial(assets.spriteMaterial(atlas.image()));
            sky(field);
            node.attachChild(field);
        }

        long seed = tower.id().hashCode();
        Optional<MoonView.Prepared> preparedMoon = mine.flatMap(SkyPreparation::moon);
        moon = MoonDesign.of(tower, options).map(design -> preparedMoon.isPresent()
                ? new MoonView(assets, design, SUN_DIRECTION.negate(), colours, preparedMoon.get(), classic)
                : new MoonView(assets, design, SUN_DIRECTION.negate(), colours, options.halfMoonDetail(), classic))
                .orElse(null);
        if (moon != null) {
            node.attachChild(moon.node());
        }
        OptionalInt cover = classic ? OptionalInt.empty() : CloudTexture.cover(tower);
        Optional<byte[]> preparedClouds = mine.flatMap(SkyPreparation::cloudTexture);
        clouds = cover.isPresent() ? new CloudView(assets, seed, cover.getAsInt(),
                preparedClouds.orElseGet(() -> CloudTexture.generate(seed, cover.getAsInt()))) : null;
        if (clouds != null) {
            node.attachChild(clouds.geometry());
        }
        node.setShadowMode(ShadowMode.Off);
    }

    public Node node() {
        return node;
    }

    /**
     * Keeps the sky centred on the camera; the clouds drift and the moon turns to simulation time {@code now}
     * (frames), and the moon shows once its textures are ready.
     */
    public void update(Vector3f cameraLocation, float now) {
        update(cameraLocation, now, true);
    }

    /** As {@link #update(Vector3f, float)}; without {@code drift} (Phase 5b: Ambient effects off) the clouds stand. */
    public void update(Vector3f cameraLocation, float now, boolean drift) {
        node.setLocalTranslation(cameraLocation);
        if (moon != null) {
            moon.update(now);
        }
        if (clouds != null) {
            clouds.update(drift ? now : 0f);
        }
    }

    /** Stops the moon's texture generation if it still runs (the game state is left). */
    public void dispose() {
        if (moon != null) {
            moon.dispose();
        }
    }

    /** The number of star sprites above the horizon (tests). */
    int stars() {
        return stars;
    }

    /** The moon, or null without a moon (tests). */
    MoonView moon() {
        return moon;
    }

    /** The clouds, or null without clouds (tests). */
    CloudView clouds() {
        return clouds;
    }

    /** The moon's turn of the last update in radians, NaN without a moon or before the first update (tests). */
    public float moonSpin() {
        return moon == null ? Float.NaN : moon.spin();
    }

    /** Whether the moon's textures are shown (tests). */
    public boolean moonReady() {
        return moon != null && moon.ready();
    }

    /** The moon's size class, or null without a moon (tests). */
    public MoonDesign.SizeClass moonSize() {
        return moon == null ? null : moon.design().size();
    }

    /** Whether the tower has clouds (tests). */
    public boolean hasClouds() {
        return clouds != null;
    }

    /**
     * Phase 5e: the dome's vertex colours alone, unshaded and unfogged; they are converted to linear (controller gamma
     * rule), as no sRGB texture carries them. Also used by {@link MoonView}'s classic haze (moon-haze fix): same
     * material shape, its own linearised colours.
     */
    static Material plainSky(AssetLibrary assets) {
        Material material = new Material(assets.manager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        return material;
    }

    private static void sky(Geometry geometry) {
        geometry.setQueueBucket(Bucket.Sky);
        geometry.setCullHint(CullHint.Never);
    }

    /** One camera-facing quad per star on the sphere of {@link #STAR_RADIUS}, in the star's colour. */
    static MeshArrays starArrays(List<StarField.Star> stars, FxAtlas atlas) {
        FxSprite cell = atlas.sprites().get(STAR_SPRITE);
        if (cell == null) {
            throw new AssetLoadException(AssetLibrary.FX_ATLAS + " (sprite " + STAR_SPRITE + ")", null);
        }
        float u0 = (float) cell.x() / atlas.imageWidth();
        float u1 = (float) (cell.x() + cell.width()) / atlas.imageWidth();
        float top = (float) cell.y() / atlas.imageHeight();
        float bottom = (float) (cell.y() + cell.height()) / atlas.imageHeight();
        float[] positions = new float[stars.size() * 18];
        float[] uvs = new float[stars.size() * 12];
        float[] colors = new float[stars.size() * 24];
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int s = 0; s < stars.size(); s++) {
            StarField.Star star = stars.get(s);
            Vector3f out = new Vector3f(star.x(), star.y(), star.z());
            Vector3f centre = out.mult(STAR_RADIUS);
            Vector3f right = Vector3f.UNIT_Y.cross(out);
            right = right.lengthSquared() < 1e-8f ? Vector3f.UNIT_X.clone() : right.normalizeLocal();
            Vector3f up = out.cross(right).normalizeLocal();
            float half = STAR_RADIUS * FastMath.tan(star.size() / 2f);
            Vector3f[] corners = {centre.subtract(right.mult(half)).subtractLocal(up.mult(half)),
                    centre.add(right.mult(half)).subtractLocal(up.mult(half)),
                    centre.add(right.mult(half)).addLocal(up.mult(half)),
                    centre.subtract(right.mult(half)).addLocal(up.mult(half))};
            float[][] cornerUv = {{u0, bottom}, {u1, bottom}, {u1, top}, {u0, top}};
            float r = ((star.colour() >> 16) & 0xFF) / 255f;
            float g = ((star.colour() >> 8) & 0xFF) / 255f;
            float b = (star.colour() & 0xFF) / 255f;
            for (int c = 0; c < 6; c++) {
                Vector3f p = corners[order[c]];
                int k = s * 18 + c * 3;
                positions[k] = p.x;
                positions[k + 1] = p.y;
                positions[k + 2] = p.z;
                uvs[s * 12 + c * 2] = cornerUv[order[c]][0];
                uvs[s * 12 + c * 2 + 1] = cornerUv[order[c]][1];
                int q = s * 24 + c * 4;
                colors[q] = r;
                colors[q + 1] = g;
                colors[q + 2] = b;
                colors[q + 3] = 1f;
            }
        }
        return new MeshArrays(positions, uvs, colors);
    }
}
