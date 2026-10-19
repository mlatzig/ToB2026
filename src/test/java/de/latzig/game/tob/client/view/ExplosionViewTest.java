package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.sim.CellPos;

class ExplosionViewTest {

    private static final Vector3f LEFT = new Vector3f(-1f, 0f, 0f);
    private static final Vector3f UP = Vector3f.UNIT_Y;

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    private static float[] floats(Geometry geometry, Type type) {
        FloatBuffer buffer = geometry.getMesh().getFloatBuffer(type);
        float[] out = new float[buffer.limit()];
        buffer.rewind();
        buffer.get(out);
        buffer.rewind();
        return out;
    }

    @Test
    void anEnemyZapperBreaksIntoItsTwoPartsWithTwelveSpritesAndStandsStillWhilePaused() {
        AssetLibrary assets = assets();
        ExplosionView view = new ExplosionView(assets, 1L);

        view.explode(ObjectType.FZN, MeshFrame.of(1, 32f), new Pose(4f, 0f, 7f), 10f);

        assertEquals(1, view.active());
        Node debris = (Node) view.node().getChild("explosion-1");
        assertEquals(2, debris.getQuantity(), "both parts of the enemy zapper break apart");
        assertEquals(new Vector3f(4f, 0f, 7f), debris.getLocalTranslation());
        Geometry part = (Geometry) debris.getChild(0);
        Geometry sprites = (Geometry) view.node().getChild("explosion-1-sprites");
        assertNotNull(sprites);
        view.update(12.5f, LEFT, UP);
        assertEquals(12, view.shownSprites(), "9 sparks, the fireball and 2 smoke puffs");
        float[] paused = floats(part, Type.Position);
        float[] pausedSprites = floats(sprites, Type.Position);

        view.update(12.5f, LEFT, UP);

        assertArrayEquals(paused, floats(part, Type.Position), 0f, "the same simulation time: no motion");
        assertArrayEquals(pausedSprites, floats(sprites, Type.Position), 0f);
        view.update(13f, LEFT, UP);
        assertFalse(Arrays.equals(paused, floats(part, Type.Position)), "it flies on with the simulation");
        view.update(30f, LEFT, UP);
        assertEquals(0, view.shownSprites(),
                "the fireball/smoke end after 10 frames and the sparks (author decision: extended lifetime, up to "
                        + "about 17.8 frames) end well before the explosion's 32 frames");
        assertEquals(1, view.active(), "the debris lasts the destruction's 32 frames");
        view.update(42f, LEFT, UP);
        assertEquals(0, view.active());
        assertEquals(0, view.node().getQuantity(), "removed from the scene");
    }

    @Test
    void aFloorBreaksIntoQuadrantDebrisInItsOwnColourWithoutSprites() {
        AssetLibrary assets = assets();
        ExplosionView view = new ExplosionView(assets, 2L);

        view.explodeFloor(new CellPos(5, 6, 1), 0xFF0000, 3f);
        view.update(4f, LEFT, UP);

        Node debris = (Node) view.node().getChild("explosion-1");
        assertEquals(new Vector3f(5f, 1f, 6f), debris.getLocalTranslation());
        assertEquals(2, debris.getQuantity(), "the floor debris mesh holds a top and a bottom quadrant");
        Geometry top = (Geometry) debris.getChild("explosion-1-" + StaticQuadrant.TOP.baseName());
        assertSame(assets.staticMaterial(StaticQuadrant.TOP), top.getMaterial(), "the walkable slabs");
        assertNotNull(top.getMesh().getBuffer(Type.TexCoord2), "the detail map's coordinates");
        Geometry bottom = (Geometry) debris.getChild("explosion-1-" + StaticQuadrant.BOTTOM.baseName());
        assertNotNull(bottom.getMesh().getBuffer(Type.TexCoord2), "Phase 4b-4: on the bottom quadrant as well");
        float[] colours = floats(top, Type.Color);
        assertEquals(1f, colours[0], 0f);
        assertEquals(0f, colours[1], 0f, "tinted in the floor's colour");
        assertNull(view.node().getChild("explosion-1-sprites"), "ToB 2005: floors explode without sprites");
        assertEquals(0, view.shownSprites());
    }

    /** Phase 5e, controller gamma rule: the Classic Look's floor debris carries the linearised palette colour. */
    @Test
    void aClassicFloorBreaksIntoDebrisInItsLinearisedColour() {
        ExplosionView view = new ExplosionView(assets(), 2L);

        view.explodeFloor(new CellPos(5, 6, 1), 0xCCAA00, true, 3f);

        Geometry top = (Geometry) view.node().getChild("explosion-1-" + StaticQuadrant.TOP.baseName());
        float[] colours = floats(top, Type.Color);
        assertEquals(SrgbColours.toLinear(0xCC / 255f), colours[0], 1e-6f);
        assertEquals(SrgbColours.toLinear(0xAA / 255f), colours[1], 1e-6f);
        assertEquals(0f, colours[2], 0f);
        assertEquals(1f, colours[3], 0f);
    }

    @Test
    void theOldestOfThirtyTwoExplosionsMakesRoom() {
        AssetLibrary assets = assets();
        ExplosionView view = new ExplosionView(assets, 3L);

        for (int i = 0; i < ExplosionView.MAX + 1; i++) {
            view.explodeFloor(new CellPos(5, 6, 0), 0x808080, i * 0.1f);
        }

        assertEquals(ExplosionView.MAX, view.active());
        assertNull(view.node().getChild("explosion-1"), "the first one went");
        assertNotNull(view.node().getChild("explosion-33"));
    }

    @Test
    void anEffectMeshBreaksApartInItsOwnMaterialWithItsOwnRandomNumbers() {
        AssetLibrary assets = assets();
        ExplosionView view = new ExplosionView(assets, 4L);
        ExplosionView other = new ExplosionView(assets, 5L);
        Material material = assets.spriteMaterial(assets.fxAtlas().image());
        String mesh = "meshes/0960-expl_lightning.tobmesh";

        view.explodeEffect(mesh, material, true, new Pose(20f, -0.03f, 30f), 0.5f, 0.25f, 3f, new Random(9L));
        other.explodeEffect(mesh, material, true, new Pose(20f, -0.03f, 30f), 0.5f, 0.25f, 3f, new Random(9L));
        view.update(4f, LEFT, UP);
        other.update(4f, LEFT, UP);

        Node debris = (Node) view.node().getChild("explosion-1");
        assertEquals(new Vector3f(20f, -0.03f, 30f), debris.getLocalTranslation());
        assertEquals(new Quaternion().fromAngleAxis(0.5f, Vector3f.UNIT_Y), debris.getLocalRotation());
        Geometry part = (Geometry) debris.getChild(0);
        assertSame(material, part.getMaterial());
        assertEquals(Bucket.Transparent, part.getQueueBucket(), "alpha-blended debris");
        assertNotNull(part.getMesh().getBuffer(Type.Color), "the mesh's own (black) vertex colours");
        assertEquals(12, view.shownSprites(), "the explosion sprites");
        Geometry otherPart = (Geometry) ((Node) other.node().getChild("explosion-1")).getChild(0);
        assertArrayEquals(floats(part, Type.Position), floats(otherPart, Type.Position), 0f,
                "the effect's random numbers, not the view's: the same strike gives the same debris");
    }
}
