package de.latzig.game.tob.client.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.client.logic.effects.Terrain;

/**
 * The lightning of a tower with the lightning flag in the scene (Phase 4b-2 spec §2 decisions 1 and 2), ToB 2005's
 * {@code twr.cpp:2373-2380}: up to three bolts ({@code 0910}, a 32-field billboard on the FX atlas, unlit and
 * alpha-blended, turned about y only to face the camera) over the last 4 simulation frames before their strike,
 * their keys played backwards; every strike breaks ToB 2005's black ground debris ({@code 0960}) apart on the flat
 * ground with the explosion sprites 8 units up ({@link ExplosionView#explodeEffect}). The schedule is
 * {@link Lightning}'s; {@link #flash} gives the flash curve for the scene light and the overlay
 * ({@code GameState}). Everything is a function of the simulation time {@link #update} receives. Per frame the bolts
 * are written into their own {@link KeyframeMesh} buffers and turned by one reused rotation (no allocation per
 * bolt).
 */
public final class LightningView {

    public static final String BOLT_MESH = "meshes/0910-lightning.tobmesh";
    public static final String DEBRIS_MESH = "meshes/0960-expl_lightning.tobmesh";
    /** ToB 2005 starts the ground debris' sprites 8 units above the strike. */
    public static final float SPRITE_OFFSET = 8f / 32f;

    private final Lightning lightning;
    private final ExplosionView explosions;
    private final Material debrisMaterial;
    private final Node node = new Node("lightning");
    private final Geometry[] bolts = new Geometry[Lightning.BOLTS];
    private final KeyframeMesh[] meshes = new KeyframeMesh[Lightning.BOLTS];
    /** The bolts' camera-facing rotation, rewritten every frame (copied by {@code setLocalRotation}). */
    private final Quaternion facing = new Quaternion();
    private float last = Float.NEGATIVE_INFINITY;
    /** The strikes of the last {@link #update} (Phase 4c: their sounds). */
    private final List<Lightning.Strike> fresh = new ArrayList<>();
    private int strikes;
    private int shown;

    /**
     * @param seed the tower's seed (the same tower, the same lightning)
     * @throws AssetLoadException if the bolt or debris mesh or the FX atlas image is missing
     */
    public LightningView(AssetLibrary assets, ExplosionView explosions, long seed) {
        this.lightning = new Lightning(seed);
        this.explosions = explosions;
        assets.mesh(DEBRIS_MESH);
        Material boltMaterial = assets.fxMaterial();
        boltMaterial.setBoolean("VertexColor", false);
        this.debrisMaterial = assets.fxMaterial();
        for (int b = 0; b < Lightning.BOLTS; b++) {
            meshes[b] = new KeyframeMesh(assets.mesh(BOLT_MESH));
            bolts[b] = new Geometry("lightning-bolt-" + b, meshes[b].mesh());
            bolts[b].setMaterial(boltMaterial);
            bolts[b].setQueueBucket(Bucket.Transparent);
            bolts[b].setCullHint(CullHint.Always);
            node.attachChild(bolts[b]);
        }
        node.setShadowMode(ShadowMode.Off);
    }

    public Node node() {
        return node;
    }

    /**
     * Shows the lightning at simulation time {@code now} (frames: steps + alpha): strikes since the last call break
     * their debris apart (at their own strike time), the bolts face the camera ({@code cameraLeft}).
     */
    public void update(float now, Vector3f cameraLeft) {
        fresh.clear();
        if (now > last) {
            for (Lightning.Strike strike : lightning.strikes(last, now)) {
                explosions.explodeEffect(DEBRIS_MESH, debrisMaterial, true, new Pose(strike.x(), Terrain.FLAT_Y,
                        strike.z()), 0f, SPRITE_OFFSET, strike.time(), new Random(strike.seed()));
                strikes++;
                fresh.add(strike);
            }
            last = now;
        }
        float yaw = (float) Math.atan2(cameraLeft.z, -cameraLeft.x);
        facing.fromAngleNormalAxis(yaw, Vector3f.UNIT_Y);
        for (Geometry bolt : bolts) {
            bolt.setCullHint(CullHint.Always);
        }
        List<Lightning.Bolt> visible = lightning.bolts(now);
        for (Lightning.Bolt bolt : visible) {
            meshes[bolt.bolt()].setKey(bolt.key());
            Geometry geometry = bolts[bolt.bolt()];
            geometry.setLocalTranslation(bolt.x(), Lightning.BOLT_Y, bolt.z());
            geometry.setLocalRotation(facing);
            geometry.setCullHint(CullHint.Inherit);
        }
        shown = visible.size();
    }

    /** The flash curve at simulation time {@code now} (0..1). */
    public float flash(float now) {
        return lightning.flash(now);
    }

    /** The strikes of the last {@link #update}, by time (Phase 4c: {@code SoundCues.onAmbient}). */
    public List<Lightning.Strike> newStrikes() {
        return Collections.unmodifiableList(fresh);
    }

    /** The strikes so far (tests). */
    public int strikes() {
        return strikes;
    }

    /** The bolts shown by the last {@link #update} (tests). */
    int shownBolts() {
        return shown;
    }

    /** Bolt {@code b}'s geometry (tests). */
    Geometry bolt(int b) {
        return bolts[b];
    }
}
