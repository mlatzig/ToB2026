package de.latzig.game.tob.client.view;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.MoonHaze;
import de.latzig.game.tob.client.logic.effects.MoonTexture;
import de.latzig.game.tob.client.logic.effects.SkyTuning;

/**
 * The 3D moon in the sky (Phase 4b-3 spec §2 decisions 5-12): one sphere per body of the {@link MoonDesign} with
 * {@code Lighting.j3md} (the generated albedo, the normal map and, on large and giant moons, the tiled detail texture
 * as light map), lit only by the moon's own lights on its anchor node: a directional light from
 * {@link MoonDesign#light} and the earthshine as ambient light (the sky node hangs outside the scene's lights). A ring
 * stands still in the plane across the turning axis; its far half is drawn before the bodies, its near half after them
 * (the sky bucket draws in scene order and has no depth). The bodies turn about the axis by {@link MoonDesign#spin}
 * of the simulation time. The textures are generated in the background ({@link MoonTexture}, its rows in parallel)
 * when the view is made, including their jME images and direct buffers (plain data until the renderer uploads them),
 * so {@link #apply} on the render thread only sets material parameters; the moon shows once they are ready, the game
 * never waits. {@link #dispose} cancels a running generation. Acceptance change "moon haze" (Phase 6b-2): right
 * after each body a still sphere of the same size in the sky dome's colours and sky texture, with the haze strength
 * of {@link MoonHaze} as vertex alpha, blends the lit body towards the sky behind it per vertex by elevation; the ring
 * takes the haze of the moon's centre.
 */
final class MoonView {

    /** One background thread for all moons; the rows of a texture run on the common fork-join pool. */
    private static final ExecutorService GENERATOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tob-moon-textures");
        thread.setDaemon(true);
        return thread;
    });
    static final int DETAIL_SIZE = 256;
    static final int RING_TEXTURE = 128;
    static final int RING_SEGMENTS = 96;

    /** One body's albedo and normal map, built in the background job. */
    private record BodyTextures(Texture2D albedo, Texture2D normal) {
    }

    /** The bodies' textures and the shared detail tile (null on normal moons). */
    private record Generated(List<BodyTextures> bodies, Texture2D detail) {
    }

    private final Node anchor = new Node("moon");
    private final MoonDesign design;
    private final List<Node> pivots = new ArrayList<>();
    private final List<Geometry> spheres = new ArrayList<>();
    private final List<Geometry> rings = new ArrayList<>();
    private final List<Geometry> hazes = new ArrayList<>();
    private final Quaternion axisFrame;
    private final Quaternion spinTurn = new Quaternion();
    private final Quaternion turned = new Quaternion();
    private final AtomicBoolean cancelled;
    /** Not final: dropped once {@link #apply} has set the textures (the heap pixel arrays are already gone then). */
    private CompletableFuture<Generated> textures;
    private final DirectionalLight light;
    private final AmbientLight earthshine;
    private boolean ready;
    private float spin = Float.NaN;

    /**
     * @param towardsSun the unit direction from the camera to the sun
     * @param colours    the tower's colours: the sky gradient the moon is hazed towards
     * @param halfDetail "moon texture detail: half" (decision 12)
     */
    MoonView(AssetLibrary assets, MoonDesign design, Vector3f towardsSun, TowerColours colours, boolean halfDetail) {
        this(assets, design, towardsSun, colours, prepare(design, halfDetail));
    }

    /**
     * As {@link #MoonView(AssetLibrary, MoonDesign, Vector3f, TowerColours, boolean)}; Phase 5e (moon-haze fix):
     * {@code classic} hazes the moon towards the Classic sky's own linearised, untextured colour instead of the
     * Remake's.
     */
    MoonView(AssetLibrary assets, MoonDesign design, Vector3f towardsSun, TowerColours colours, boolean halfDetail,
            boolean classic) {
        this(assets, design, towardsSun, colours, prepare(design, halfDetail), classic);
    }

    /**
     * A moon whose textures {@code prepared} is generating or has generated (Phase 5a: the Briefing starts the job).
     *
     * @param towardsSun the unit direction from the camera to the sun
     * @param colours    the tower's colours: the sky gradient the moon is hazed towards
     */
    MoonView(AssetLibrary assets, MoonDesign design, Vector3f towardsSun, TowerColours colours, Prepared prepared) {
        this(assets, design, towardsSun, colours, prepared, false);
    }

    /**
     * As {@link #MoonView(AssetLibrary, MoonDesign, Vector3f, TowerColours, Prepared)}; Phase 5e (moon-haze fix):
     * {@code classic} hazes the moon towards the Classic sky's own linearised, untextured colour instead of the
     * Remake's (mirrors {@link SkyView#plainSky}).
     */
    MoonView(AssetLibrary assets, MoonDesign design, Vector3f towardsSun, TowerColours colours, Prepared prepared,
            boolean classic) {
        this.design = design;
        this.cancelled = prepared.cancelled;
        this.textures = prepared.textures;
        float[] d = design.direction();
        Vector3f direction = new Vector3f(d[0], d[1], d[2]);
        anchor.setLocalTranslation(direction.mult(SkyView.MOON_RADIUS));
        Quaternion facing = new Quaternion();
        facing.lookAt(direction.negate(), Math.abs(direction.y) > 0.999f ? Vector3f.UNIT_Z : Vector3f.UNIT_Y);
        anchor.setLocalRotation(facing);
        float[] l = design.light(new float[] {towardsSun.x, towardsSun.y, towardsSun.z});
        light = new DirectionalLight(new Vector3f(-l[0], -l[1], -l[2]), ColorRGBA.White.mult(SkyTuning.MOON_LIGHT));
        earthshine = new AmbientLight(new ColorRGBA(design.earthshine(), design.earthshine(), design.earthshine(),
                1f));
        anchor.addLight(light);
        anchor.addLight(earthshine);

        float[] a = design.axis();
        Vector3f up = new Vector3f(a[0], a[1], a[2]);
        Vector3f right = up.cross(Vector3f.UNIT_Z).normalizeLocal();
        Vector3f front = right.cross(up).normalizeLocal();
        axisFrame = new Quaternion().fromAxes(right, up, front);
        float half = SkyView.MOON_RADIUS * FastMath.sin(design.size().width() / 2f);
        boolean detail = design.size() != MoonDesign.SizeClass.NORMAL && SkyTuning.DETAIL_STRENGTH > 0f;
        int segments = switch (design.size()) {
            case NORMAL -> 32;
            case LARGE -> 64;
            case GIANT -> 96;
        };
        Mesh sphere = sphere(segments);
        double elevation = Math.asin(Math.clamp(direction.y, -1f, 1f));
        design.ring().map(ring -> MoonHaze.ring(ring, colours, elevation)).ifPresent(ring -> {
            MoonDesign.Body first = design.bodies().get(0);
            Vector3f centre = new Vector3f(first.offsetX() * half, first.offsetY() * half, 0f);
            float radius = first.scale() * half;
            // One shared texture for both halves (same bytes): avoids uploading the ring band twice.
            Texture2D ringTexture = GeneratedTextures.of("moon-ring", RING_TEXTURE, 1, ring.texture(RING_TEXTURE),
                    false, false, true);
            rings.add(ring(assets, ringTexture, ring, centre, right, front, radius, false));
            rings.add(ring(assets, ringTexture, ring, centre, right, front, radius, true));
            anchor.attachChild(rings.get(0));
        });
        for (int i = 0; i < design.bodies().size(); i++) {
            MoonDesign.Body body = design.bodies().get(i);
            Node pivot = new Node("moon-body-" + i);
            pivot.setLocalTranslation(body.offsetX() * half, body.offsetY() * half, 0f);
            pivot.setLocalRotation(axisFrame);
            Geometry geometry = new Geometry("moon-sphere-" + i, sphere);
            geometry.setLocalScale(body.scale() * half);
            Material material = new Material(assets.manager(), "Common/MatDefs/Light/Lighting.j3md");
            material.setFloat("NormalType", 1f);
            if (detail) {
                material.setBoolean("SeparateTexCoord", true);
            }
            geometry.setMaterial(material);
            geometry.setQueueBucket(Bucket.Sky);
            geometry.setCullHint(CullHint.Always);
            pivot.attachChild(geometry);
            anchor.attachChild(pivot);
            pivots.add(pivot);
            spheres.add(geometry);
            Geometry haze = haze(assets, sphere, colours, i, pivot.getLocalTranslation(), body.scale() * half,
                    facing, classic);
            anchor.attachChild(haze);
            hazes.add(haze);
        }
        if (rings.size() == 2) {
            anchor.attachChild(rings.get(1));
        }
    }

    /**
     * Starts generating the textures of {@code design} in the background (Phase 5a decision 14: the Briefing starts
     * this so its loading bar can show the progress, and the game's moon then takes the finished job). The jME images
     * (direct buffers) are built here too, off the render thread: a full-detail giant copies about 64 MB, which would
     * otherwise be one long frame at the reveal. A Texture2D is plain data until the renderer uploads it on the render
     * thread. {@link MoonTexture#generate} asks its cancel flag once per row in each of its two passes and once after
     * each pass, which counts the work done.
     *
     * @param halfDetail "moon texture detail: half" (decision 12)
     */
    static Prepared prepare(MoonDesign design, boolean halfDetail) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger rows = new AtomicInteger();
        int total = 0;
        for (MoonDesign.Body body : design.bodies()) {
            total += 2 * (design.textureWidth(body, halfDetail) / 2) + 2;
        }
        boolean detail = design.size() != MoonDesign.SizeClass.NORMAL && SkyTuning.DETAIL_STRENGTH > 0f;
        CompletableFuture<Generated> textures = CompletableFuture.supplyAsync(() -> {
            List<BodyTextures> bodies = new ArrayList<>();
            for (int i = 0; i < design.bodies().size(); i++) {
                MoonDesign.Body body = design.bodies().get(i);
                int width = design.textureWidth(body, halfDetail);
                MoonTexture.Maps maps = MoonTexture.generate(body.surface(), width, width / 2, () -> {
                    rows.incrementAndGet();
                    return cancelled.get();
                });
                bodies.add(new BodyTextures(
                        GeneratedTextures.of("moon-albedo-" + i, maps.width(), maps.height(), maps.albedo(), true,
                                false, true),
                        GeneratedTextures.of("moon-normal-" + i, maps.width(), maps.height(), maps.normal(), true,
                                false, false)));
            }
            // One shared tile for every sphere (they are all the same bytes): avoids uploading it twice for a
            // companion/pair moon.
            Texture2D tile = detail ? GeneratedTextures.of("moon-detail", DETAIL_SIZE, DETAIL_SIZE, MoonTexture.detail(
                    DETAIL_SIZE, design.bodies().get(0).surface().seed(), SkyTuning.DETAIL_STRENGTH), true, true, true)
                    : null;
            return new Generated(bodies, tile);
        }, GENERATOR);
        return new Prepared(textures, cancelled, rows, total);
    }

    /** A moon's textures being generated in the background (Phase 5a), with the work done of all work. */
    static final class Prepared {

        private final CompletableFuture<Generated> textures;
        private final AtomicBoolean cancelled;
        private final AtomicInteger rows;
        private final int total;

        private Prepared(CompletableFuture<Generated> textures, AtomicBoolean cancelled, AtomicInteger rows,
                int total) {
            this.textures = textures;
            this.cancelled = cancelled;
            this.rows = rows;
            this.total = total;
        }

        /** 0..1: the rows done; 1 only once the job has finished. */
        float progress() {
            return textures.isDone() ? 1f : Math.min(0.99f, (float) rows.get() / total);
        }

        boolean isDone() {
            return textures.isDone();
        }

        /** Whether the job ended without textures (cancelled, or failed). */
        boolean isCancelled() {
            return textures.isCompletedExceptionally();
        }

        void cancel() {
            cancelled.set(true);
        }
    }

    Node node() {
        return anchor;
    }

    /** Shows the moon once its textures are ready and turns the bodies to simulation time {@code now}. */
    void update(float now) {
        if (!ready && textures.isDone()) {
            apply();
        }
        float turn = design.spin(now);
        if (turn != spin) {
            spin = turn;
            spinTurn.fromAngleAxis(turn, Vector3f.UNIT_Y);
            axisFrame.mult(spinTurn, turned);
            for (Node pivot : pivots) {
                pivot.setLocalRotation(turned);
            }
        }
    }

    /** Stops a running texture generation (the game state is left). */
    void dispose() {
        cancelled.set(true);
    }

    /** Waits for the textures (tests only; the game never waits). */
    void awaitTextures() {
        if (textures != null) {
            textures.join();
        }
    }

    boolean ready() {
        return ready;
    }

    /** The bodies' turn of the last update in radians, NaN before the first (tests). */
    float spin() {
        return spin;
    }

    MoonDesign design() {
        return design;
    }

    List<Geometry> spheres() {
        return spheres;
    }

    /** The bodies' haze spheres, one after each body (tests). */
    List<Geometry> hazes() {
        return hazes;
    }

    /** The ring's far and near halves, or none (tests). */
    List<Geometry> rings() {
        return rings;
    }

    DirectionalLight light() {
        return light;
    }

    AmbientLight earthshine() {
        return earthshine;
    }

    private void apply() {
        Generated generated;
        try {
            generated = textures.join();
        } catch (CompletionException e) {
            throw new IllegalStateException("moon textures failed: " + e.getCause(), e.getCause());
        }
        for (int i = 0; i < spheres.size(); i++) {
            BodyTextures body = generated.bodies().get(i);
            Material material = spheres.get(i).getMaterial();
            material.setTexture("DiffuseMap", body.albedo());
            material.setTexture("NormalMap", body.normal());
            if (generated.detail() != null) {
                material.setTexture("LightMap", generated.detail());
            }
            spheres.get(i).setCullHint(CullHint.Never);
            hazes.get(i).setCullHint(CullHint.Never);
        }
        for (Geometry ring : rings) {
            ring.setCullHint(CullHint.Never);
        }
        ready = true;
        // Drop the future: the textures now live in the materials (the renderer uploads them in the next frame).
        textures = null;
    }

    /**
     * The haze over body {@code index}: the body's sphere mesh at {@code centre} (in the anchor) and {@code scale},
     * not turning, with the sky texture spot, sky colour and haze strength of each vertex's direction from the
     * camera ({@link MoonHaze#vertices}), alpha-blended in the dome's own material (sky texture times vertex colour).
     * Phase 5e (moon-haze fix): with {@code classic} the material and colours mirror the Classic dome
     * ({@link SkyView#plainSky}: unshaded, untextured, vertex colour only) with the same channels linearised
     * ({@link SrgbColours#linearRgba}, alpha untouched); the Remake path (textured material, raw sRGB colours) is
     * unchanged.
     */
    private Geometry haze(AssetLibrary assets, Mesh sphere, TowerColours colours, int index, Vector3f centre,
            float scale, Quaternion facing, boolean classic) {
        FloatBuffer local = (FloatBuffer) sphere.getBuffer(Type.Position).getData();
        float[] positions = new float[local.limit()];
        Vector3f vertex = new Vector3f();
        for (int v = 0; v < positions.length / 3; v++) {
            vertex.set(local.get(v * 3), local.get(v * 3 + 1), local.get(v * 3 + 2)).multLocal(scale).addLocal(centre);
            facing.multLocal(vertex).addLocal(anchor.getLocalTranslation());
            positions[v * 3] = vertex.x;
            positions[v * 3 + 1] = vertex.y;
            positions[v * 3 + 2] = vertex.z;
        }
        MoonHaze.Vertices haze = MoonHaze.vertices(positions, colours);
        float[] colors = classic ? SrgbColours.linearRgba(haze.colors()) : haze.colors();
        Mesh mesh = new Mesh();
        mesh.setBuffer(sphere.getBuffer(Type.Position));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(haze.uvs()));
        mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.setBuffer(sphere.getBuffer(Type.Index));
        mesh.updateBound();
        Geometry geometry = new Geometry("moon-haze-" + index, mesh);
        geometry.setLocalTranslation(centre);
        geometry.setLocalScale(scale);
        Material material = classic ? SkyView.plainSky(assets) : assets.baseMaterial("sky").clone();
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Back);
        material.getAdditionalRenderState().setDepthWrite(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Sky);
        geometry.setCullHint(CullHint.Always);
        return geometry;
    }

    /**
     * A unit sphere about local +y with equirectangular texture coordinates matching {@link MoonTexture#direction}:
     * u = longitude / 2π from +z towards +x, v = (latitude + 90°) / 180°; tangent east (w = +1), a seam column
     * doubled, {@code segments / 2} latitude bands; the second texture coordinates repeat the detail texture.
     */
    static Mesh sphere(int segments) {
        int bands = segments / 2;
        int columns = segments + 1;
        int vertices = columns * (bands + 1);
        float[] positions = new float[vertices * 3];
        float[] normals = new float[vertices * 3];
        float[] tangents = new float[vertices * 4];
        float[] uvs = new float[vertices * 2];
        float[] detail = new float[vertices * 2];
        for (int band = 0; band <= bands; band++) {
            float v = (float) band / bands;
            double latitude = Math.PI * v - Math.PI / 2;
            for (int column = 0; column < columns; column++) {
                float u = (float) column / segments;
                double longitude = 2 * Math.PI * u;
                int i = band * columns + column;
                float x = (float) (Math.cos(latitude) * Math.sin(longitude));
                float y = (float) Math.sin(latitude);
                float z = (float) (Math.cos(latitude) * Math.cos(longitude));
                positions[i * 3] = x;
                positions[i * 3 + 1] = y;
                positions[i * 3 + 2] = z;
                normals[i * 3] = x;
                normals[i * 3 + 1] = y;
                normals[i * 3 + 2] = z;
                tangents[i * 4] = (float) Math.cos(longitude);
                tangents[i * 4 + 1] = 0f;
                tangents[i * 4 + 2] = (float) -Math.sin(longitude);
                tangents[i * 4 + 3] = 1f;
                uvs[i * 2] = u;
                uvs[i * 2 + 1] = v;
                detail[i * 2] = u * SkyTuning.DETAIL_TILES;
                detail[i * 2 + 1] = v * SkyTuning.DETAIL_TILES / 2f;
            }
        }
        int[] indices = new int[segments * bands * 6];
        int k = 0;
        for (int band = 0; band < bands; band++) {
            for (int column = 0; column < segments; column++) {
                int a = band * columns + column;
                int b = a + 1;
                int c = a + columns;
                int e = c + 1;
                indices[k++] = a;
                indices[k++] = b;
                indices[k++] = e;
                indices[k++] = a;
                indices[k++] = e;
                indices[k++] = c;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(Type.Tangent, 4, BufferUtils.createFloatBuffer(tangents));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs));
        mesh.setBuffer(Type.TexCoord2, 2, BufferUtils.createFloatBuffer(detail));
        mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    /**
     * One half of the ring: the annulus about {@code centre} in the plane spanned by {@code right} and {@code front}
     * (across the turning axis), its segments whose middle lies nearer to the viewer (+z) than the centre when
     * {@code near}, else the others; u runs from the inner to the outer edge.
     */
    private static Geometry ring(AssetLibrary assets, Texture2D texture, MoonDesign.Ring ring, Vector3f centre,
            Vector3f right, Vector3f front, float radius, boolean near) {
        List<float[]> corners = new ArrayList<>();
        for (int s = 0; s < RING_SEGMENTS; s++) {
            double a0 = 2 * Math.PI * s / RING_SEGMENTS;
            double a1 = 2 * Math.PI * (s + 1) / RING_SEGMENTS;
            double middle = (a0 + a1) / 2;
            float depth = (float) (Math.cos(middle) * right.z + Math.sin(middle) * front.z);
            if (depth > 0f != near) {
                continue;
            }
            float[][] quad = {point(centre, right, front, radius * ring.inner(), a0, 0f),
                    point(centre, right, front, radius * ring.outer(), a0, 1f),
                    point(centre, right, front, radius * ring.outer(), a1, 1f),
                    point(centre, right, front, radius * ring.inner(), a1, 0f)};
            for (int c : new int[] {0, 1, 2, 0, 2, 3}) {
                corners.add(quad[c]);
            }
        }
        float[] positions = new float[corners.size() * 3];
        float[] uvs = new float[corners.size() * 2];
        for (int i = 0; i < corners.size(); i++) {
            float[] corner = corners.get(i);
            positions[i * 3] = corner[0];
            positions[i * 3 + 1] = corner[1];
            positions[i * 3 + 2] = corner[2];
            uvs[i * 2] = corner[3];
            uvs[i * 2 + 1] = 0.5f;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs));
        mesh.updateBound();
        Geometry geometry = new Geometry(near ? "moon-ring-near" : "moon-ring-far", mesh);
        Material material = new Material(assets.manager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Sky);
        geometry.setCullHint(CullHint.Always);
        return geometry;
    }

    private static float[] point(Vector3f centre, Vector3f right, Vector3f front, float radius, double angle,
            float u) {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;
        return new float[] {centre.x + c * right.x + s * front.x, centre.y + c * right.y + s * front.y,
                centre.z + c * right.z + s * front.z, u};
    }
}
