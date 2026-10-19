package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.VertexColours;
import de.latzig.game.tob.client.logic.effects.ExplosionSprites;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.RenderType;

/**
 * The meteors of a tower with the meteor flag in the scene (Phase 4b-2 spec §2 decision 3), ToB 2005's
 * {@code twr.cpp:2381-2388} and {@code bpe.cpp:760-790}: four turning rocks ({@code 0920}, lit with ToB 2005's
 * meteor texture and normal map {@code 0910/0911}, no shadows) with their trails of 24 fireball sprites in one
 * batch; every impact breaks the rock apart ({@link ExplosionView#explodeEffect}, sprites 16 units up) on the
 * visible ground around the tower (author decision: meteors never hit the tower, see {@link Meteors}). ToB 2005
 * breaks a rock apart in the explosion colour 16/16/16 ({@code twr.cpp:1030}), averaged with the rock's white vertex
 * colours ({@code dxe.cpp:1124-1126}), so the debris is drawn in {@link #DEBRIS_TINT} 0x878787 (Phase 4b-4 spec
 * decision 4). The flights are {@link Meteors}'; everything is a function of the simulation time {@link #update}
 * receives. The meteors never touch the simulation.
 *
 * <p>Per frame the trails are collected into one reused list and written into the {@link SpriteBatch}'s reused
 * buffers; the rocks are turned by one reused rotation.</p>
 */
public final class MeteorView {

    public static final String MESH = "meshes/0920-meteor.tobmesh";
    public static final String TEXTURE = "textures/0910-meteor-color.png";
    public static final String NORMAL_MAP = "textures/0911-meteor-normal.png";
    /** The debris colour: ToB 2005's explosion colour 16/16/16 averaged with the rock's white. */
    public static final int DEBRIS_TINT = VertexColours.average(0xFFFFFF, 0x101010) & 0xFFFFFF;

    private final Meteors meteors;
    private final ExplosionView explosions;
    private final Material material;
    private final Material debrisMaterial;
    private final Node node = new Node("meteors");
    private final Geometry[] rocks = new Geometry[Meteors.COUNT];
    private final KeyframeMesh[] meshes = new KeyframeMesh[Meteors.COUNT];
    private final SpriteBatch trail;
    /** The trail sprites of the current frame, cleared and refilled every frame. */
    private final List<ExplosionSprites.Sprite> sprites = new ArrayList<>(Meteors.COUNT * Meteors.TRAIL);
    /** A rock's rotation, rewritten per rock (copied by {@code setLocalRotation}). */
    private final Quaternion turn = new Quaternion();
    private float last = Float.NEGATIVE_INFINITY;
    private int impacts;
    /** The impacts of the last {@link #update} (Phase 4c: their sounds). */
    private final List<Meteors.Impact> fresh = new ArrayList<>();
    private int shownSprites;

    /**
     * @param seed   the tower's seed (the same tower, the same meteors)
     * @param ground the visible ground the rocks break apart on ({@link Meteors#ground})
     * @throws AssetLoadException if the meteor mesh or texture or the FX atlas is missing
     */
    public MeteorView(AssetLibrary assets, ExplosionView explosions, long seed, Meteors.Ground ground) {
        this.meteors = new Meteors(seed, ground);
        this.explosions = explosions;
        this.material = assets.material(new MeshPart(MESH, TEXTURE, NORMAL_MAP, RenderType.OPAQUE, 0xFFFFFF, false),
                false);
        this.debrisMaterial = assets.material(new MeshPart(MESH, TEXTURE, NORMAL_MAP, RenderType.OPAQUE, DEBRIS_TINT,
                false), false);
        for (int m = 0; m < Meteors.COUNT; m++) {
            meshes[m] = new KeyframeMesh(assets.mesh(MESH));
            rocks[m] = new Geometry("meteor-" + m, meshes[m].mesh());
            rocks[m].setMaterial(material);
            node.attachChild(rocks[m]);
        }
        trail = new SpriteBatch("meteor-trails", assets.fxAtlas(), assets.fxMaterial(), Meteors.COUNT * Meteors.TRAIL);
        node.attachChild(trail.geometry());
        node.setShadowMode(ShadowMode.Off);
    }

    public Node node() {
        return node;
    }

    /**
     * Shows the meteors at simulation time {@code now} (frames: steps + alpha): impacts since the last call break
     * their rock apart (at their own impact time); the trails face the camera ({@code cameraLeft},
     * {@code cameraUp}). Once per render frame.
     */
    public void update(float now, Vector3f cameraLeft, Vector3f cameraUp) {
        fresh.clear();
        if (now > last) {
            for (Meteors.Impact impact : meteors.impacts(last, now)) {
                explosions.explodeEffect(MESH, debrisMaterial, false, new Pose(impact.x(), impact.y(), impact.z()),
                        impact.yaw(), Meteors.SPRITE_OFFSET, impact.time(), new Random(impact.seed()));
                impacts++;
                fresh.add(impact);
            }
            last = now;
        }
        sprites.clear();
        for (Meteors.Meteor meteor : meteors.at(now)) {
            meshes[meteor.meteor()].setKey(meteor.key());
            rocks[meteor.meteor()].setLocalTranslation(meteor.x(), meteor.y(), meteor.z());
            rocks[meteor.meteor()].setLocalRotation(turn.fromAngleNormalAxis(meteor.yaw(), Vector3f.UNIT_Y));
            sprites.addAll(meteor.trail());
        }
        shownSprites = trail.update(sprites, cameraLeft, cameraUp);
    }

    /** The impacts of the last {@link #update}, by time (Phase 4c: {@code SoundCues.onAmbient}). */
    public List<Meteors.Impact> newImpacts() {
        return Collections.unmodifiableList(fresh);
    }

    /** The impacts so far (tests). */
    public int impacts() {
        return impacts;
    }

    /** The trail sprites drawn by the last {@link #update} (tests). */
    int shownSprites() {
        return shownSprites;
    }

    /** Meteor {@code m}'s rock (tests). */
    Geometry rock(int m) {
        return rocks[m];
    }

    /** The trails' sprite batch geometry (tests). */
    Geometry trailGeometry() {
        return trail.geometry();
    }
}
