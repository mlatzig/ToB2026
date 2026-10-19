package de.latzig.game.tob.client.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import de.latzig.game.tob.client.logic.FaceFrames;
import de.latzig.game.tob.client.logic.KeyframeSampler;
import de.latzig.game.tob.client.logic.MeshArrays;
import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.ExplosionDebris;
import de.latzig.game.tob.client.logic.effects.ExplosionParts;
import de.latzig.game.tob.client.logic.effects.ExplosionSprites;
import de.latzig.game.tob.client.logic.effects.StaticAtlas;
import de.latzig.game.tob.core.model.ElementDefinition;
import de.latzig.game.tob.core.model.FxAtlas;
import de.latzig.game.tob.core.model.MeshData;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.sim.CellPos;

/**
 * Explosions and debris in the scene (Phase 4b-1 spec §2 decisions 1–4), ToB 2005's explosion queue
 * ({@code twr.cpp:2329-2344}, {@code 2449-2533}). An element breaks into flying triangles ({@link ExplosionDebris})
 * of the pieces {@link ExplosionParts} names, in their own materials and vertex colours, plus 12 camera-facing FX atlas
 * sprites ({@link ExplosionSprites}) from its sprite offset; a floor breaks into ToB 2005's floor debris mesh on the
 * STATIC quadrant materials, tinted in its colour, without sprites. Everything is a function of the simulation time
 * passed to {@link #update} (steps + alpha): explosions stand still while paused or in a menu and keep pace with
 * fast-forward. An explosion ends after the destruction's {@link ExplosionDebris#DURATION} frames; at most
 * {@link #MAX} run at once, the oldest making room. Explosions neither cast nor receive shadows. Phase 4b-2: the
 * ambient effects break their own meshes apart the same way ({@link #explodeEffect}: lightning ground debris, meteor
 * impacts).
 */
public final class ExplosionView {

    /** ToB 2005's {@code TWR_EXPLOSION_MAX}. */
    public static final int MAX = 32;

    /** One flying mesh: its physics and the jME buffers rewritten every frame. */
    private static final class Debris {

        private final ExplosionDebris physics;
        private final Geometry geometry;
        private final float[] positions;
        private final float[] uvs;
        private final float[] normals;
        private final float[] tangents;

        Debris(String name, ExplosionDebris physics, float[] uvs, float[] colors, float detailRepeat) {
            this.physics = physics;
            this.uvs = uvs;
            int corners = physics.triangles() * 3;
            positions = new float[corners * 3];
            normals = new float[corners * 3];
            tangents = new float[corners * 4];
            Mesh mesh = new Mesh();
            mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(corners * 3));
            mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(corners * 3));
            mesh.setBuffer(Type.Tangent, 4, BufferUtils.createFloatBuffer(corners * 4));
            mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs));
            if (detailRepeat > 0f) {
                float[] detail = uvs.clone();
                for (int i = 0; i < detail.length; i++) {
                    detail[i] *= detailRepeat;
                }
                mesh.setBuffer(Type.TexCoord2, 2, BufferUtils.createFloatBuffer(detail));
            }
            if (colors != null) {
                mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
            }
            mesh.setStreamed();
            geometry = new Geometry(name, mesh);
            update(0f);
        }

        void update(float frames) {
            physics.positionsAt(frames, positions);
            FaceFrames.compute(positions, uvs, normals, tangents);
            Mesh mesh = geometry.getMesh();
            upload(mesh, Type.Position, positions);
            upload(mesh, Type.Normal, normals);
            upload(mesh, Type.Tangent, tangents);
            mesh.updateBound();
            geometry.updateModelBound();
        }
    }

    private record Active(float start, Node debris, List<Debris> pieces, ExplosionSprites sprites,
            SpriteBatch spriteBatch) {

        Geometry spriteGeometry() {
            return spriteBatch == null ? null : spriteBatch.geometry();
        }
    }

    private final AssetLibrary assets;
    /** Phase 5e: the scene's materials of the object and floor debris. */
    private final SceneMaterials materials;
    private final FxAtlas atlas;
    private final Material spriteMaterial;
    private final Random random;
    private final Node node = new Node("explosions");
    private final ArrayDeque<Active> active = new ArrayDeque<>();
    private int started;
    private int shownSprites;

    /**
     * @param seed the random numbers of the debris and sprites (the tower id's hash: the same events give the same
     *             explosions)
     * @throws AssetLoadException if the FX atlas lacks a sprite sequence
     */
    public ExplosionView(AssetLibrary assets, long seed) {
        this(assets, seed, SceneMaterials.remake(assets));
    }

    /**
     * Phase 5e: the explosions with the debris on the scene's {@code materials} (the Remake or the Classic Look).
     *
     * @throws AssetLoadException if the FX atlas lacks a sprite sequence
     */
    public ExplosionView(AssetLibrary assets, long seed, SceneMaterials materials) {
        this.assets = assets;
        this.materials = materials;
        this.atlas = assets.fxAtlas();
        for (String sequence : List.of(ExplosionSprites.SPARKS, ExplosionSprites.FIREBALL, ExplosionSprites.SMOKE)) {
            if (atlas.sequences().get(sequence) == null) {
                throw new AssetLoadException(AssetLibrary.FX_ATLAS + " (sequence " + sequence + ")", null);
            }
        }
        this.spriteMaterial = assets.fxMaterial();
        this.random = new Random(seed);
        node.setShadowMode(ShadowMode.Off);
    }

    public Node node() {
        return node;
    }

    /** The explosions running. */
    public int active() {
        return active.size();
    }

    /** The sprites drawn by the last {@link #update} (tests). */
    int shownSprites() {
        return shownSprites;
    }

    /**
     * {@code type}, last shown at {@code frame}, breaks apart at {@code pose}, starting at simulation time
     * {@code start}.
     *
     * @throws AssetLoadException if a debris mesh or texture is missing
     */
    public void explode(ObjectType type, MeshFrame frame, Pose pose, float start) {
        ElementDefinition element = assets.element(type);
        Node debris = new Node("explosion-" + (++started));
        debris.setLocalTranslation(pose.x(), pose.y(), pose.z());
        debris.setLocalRotation(new Quaternion().fromAngleAxis(-frame.heading(), Vector3f.UNIT_Y));
        List<Debris> pieces = new ArrayList<>();
        for (ExplosionParts.Piece piece : ExplosionParts.of(element, frame)) {
            MeshData data = assets.objectMesh(piece.mesh(), piece.part().tint());
            int key = KeyframeSampler.sample(data.keys(), piece.key()).from();
            Debris d = new Debris(debris.getName() + "-" + pieces.size(), ExplosionDebris.of(data.positions(key),
                    ExplosionDebris.OBJECT_MIN_HEIGHT, random), data.uvs(key),
                    data.hasColors() ? rgba(data.colors(key)) : null, 0f);
            d.geometry.setMaterial(materials.part(type, piece.part(), data.hasColors()));
            if (AssetLibrary.isTransparent(piece.part())) {
                d.geometry.setQueueBucket(Bucket.Transparent);
            }
            debris.attachChild(d.geometry);
            pieces.add(d);
        }
        float offset = element.explosion().spriteOffset() / 32f;
        ExplosionSprites sprites = ExplosionSprites.start(pose.x(), pose.y() + offset, pose.z(), random);
        add(new Active(start, debris, pieces, sprites, new SpriteBatch(debris.getName() + "-sprites", atlas,
                spriteMaterial, ExplosionSprites.COUNT)));
    }

    /**
     * An ambient effect's mesh breaks apart at {@code pose}, turned by {@code yaw} about y, starting at simulation
     * time {@code start} (Phase 4b-2: ToB 2005's {@code TWR_addExplosionToQueue} for the lightning's ground debris
     * {@code 0960} and a meteor's rock {@code 0920}): the mesh's first key flies apart like an element's, with the 12
     * sprites {@code spriteOffset} fields above {@code pose}. The effect's own {@code random} (seeded per strike or
     * impact) keeps its debris independent of when the effect is noticed.
     *
     * @param transparent whether the debris goes to the transparent bucket (an alpha-blended material)
     * @throws AssetLoadException if the mesh is missing
     */
    public void explodeEffect(String mesh, Material material, boolean transparent, Pose pose, float yaw,
            float spriteOffset, float start, Random random) {
        MeshData data = assets.mesh(mesh);
        Node debris = new Node("explosion-" + (++started));
        debris.setLocalTranslation(pose.x(), pose.y(), pose.z());
        debris.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        Debris d = new Debris(debris.getName() + "-0", ExplosionDebris.of(data.positions(0),
                ExplosionDebris.OBJECT_MIN_HEIGHT, random), data.uvs(0),
                data.hasColors() ? rgba(data.colors(0)) : null, 0f);
        d.geometry.setMaterial(material);
        if (transparent) {
            d.geometry.setQueueBucket(Bucket.Transparent);
        }
        debris.attachChild(d.geometry);
        ExplosionSprites sprites = ExplosionSprites.start(pose.x(), pose.y() + spriteOffset, pose.z(), random);
        add(new Active(start, debris, List.of(d), sprites, new SpriteBatch(debris.getName() + "-sprites", atlas,
                spriteMaterial, ExplosionSprites.COUNT)));
    }

    /**
     * The floor of {@code cell} breaks apart (ToB 2005 {@code TWR_destroyFloor}: debris mesh 1550, the floor's
     * colour, one level of fall, no sprites), starting at simulation time {@code start}.
     *
     * @throws AssetLoadException if the floor debris mesh is missing
     */
    public void explodeFloor(CellPos cell, int colour, float start) {
        explodeFloor(cell, colour, false, start);
    }

    /**
     * Phase 5e: the floor of {@code cell} breaks apart in {@code colour}, its vertex colour converted from sRGB to
     * linear if {@code linear} (the Classic Look's palette colours, controller gamma rule).
     *
     * @throws AssetLoadException if the floor debris mesh is missing
     */
    public void explodeFloor(CellPos cell, int colour, boolean linear, float start) {
        MeshData data = assets.mesh(ExplosionParts.FLOOR_DEBRIS);
        float[] rgb = SrgbColours.rgb(colour, linear);
        float[] colours = new float[data.triangleCount() * 12];
        for (int i = 0; i < colours.length; i += 4) {
            colours[i] = rgb[0];
            colours[i + 1] = rgb[1];
            colours[i + 2] = rgb[2];
            colours[i + 3] = 1f;
        }
        Node debris = new Node("explosion-" + (++started));
        debris.setLocalTranslation(cell.x(), cell.level(), cell.y());
        List<Debris> pieces = new ArrayList<>();
        Map<StaticQuadrant, MeshArrays> parts = StaticAtlas.split(new MeshArrays(data.positions(0), data.uvs(0),
                colours));
        for (Map.Entry<StaticQuadrant, MeshArrays> part : parts.entrySet()) {
            StaticQuadrant quadrant = part.getKey();
            Debris d = new Debris(debris.getName() + "-" + quadrant.baseName(),
                    ExplosionDebris.of(part.getValue().positions(), ExplosionDebris.FLOOR_MIN_HEIGHT, random),
                    part.getValue().uvs(), part.getValue().colors(),
                    StaticAtlas.DETAIL_REPEAT);
            d.geometry.setMaterial(materials.base(quadrant));
            debris.attachChild(d.geometry);
            pieces.add(d);
        }
        add(new Active(start, debris, pieces, null, null));
    }

    /**
     * Shows every explosion at simulation time {@code now} (frames: steps + alpha); ended ones leave the scene.
     * The sprites face the camera ({@code cameraLeft}, {@code cameraUp}).
     */
    public void update(float now, Vector3f cameraLeft, Vector3f cameraUp) {
        shownSprites = 0;
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active explosion = it.next();
            float frames = now - explosion.start();
            if (frames >= ExplosionDebris.DURATION) {
                remove(explosion);
                it.remove();
                continue;
            }
            for (Debris d : explosion.pieces()) {
                d.update(frames);
            }
            if (explosion.sprites() != null) {
                shownSprites += explosion.spriteBatch().update(explosion.sprites().at(frames), cameraLeft, cameraUp);
            }
        }
    }

    private void add(Active explosion) {
        if (active.size() == MAX) {
            remove(active.pollFirst());
        }
        node.attachChild(explosion.debris());
        if (explosion.spriteGeometry() != null) {
            node.attachChild(explosion.spriteGeometry());
        }
        active.addLast(explosion);
    }

    private static void remove(Active explosion) {
        explosion.debris().removeFromParent();
        if (explosion.spriteGeometry() != null) {
            explosion.spriteGeometry().removeFromParent();
        }
    }

    private static void upload(Mesh mesh, Type type, float[] data) {
        SpriteBatch.upload(mesh, type, data);
    }

    private static float[] rgba(int[] argb) {
        float[] out = new float[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            out[i * 4] = ((argb[i] >> 16) & 0xFF) / 255f;
            out[i * 4 + 1] = ((argb[i] >> 8) & 0xFF) / 255f;
            out[i * 4 + 2] = (argb[i] & 0xFF) / 255f;
            out[i * 4 + 3] = ((argb[i] >>> 24) & 0xFF) / 255f;
        }
        return out;
    }
}
