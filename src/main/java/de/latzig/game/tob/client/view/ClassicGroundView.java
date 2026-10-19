package de.latzig.game.tob.client.view;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.Lightning;

/**
 * Phase 5e (Classic Look, decision 1 and spec §3): the flat ground of the original instead of the terrain and the
 * socket: one square {@value #HALF} fields to every side of the grid's centre (about as far as the Remake's terrain),
 * just below the level-0 floor bottoms ({@link Socket#TOP}), in one colour (the palette's lit floor), lit by the sun
 * and receiving the shadows (decision 6), without fog. The colour is the palette's sRGB colour converted to linear
 * ({@link SrgbColours}, controller gamma rule), as jME takes plain material colours as linear.
 */
public final class ClassicGroundView {

    /** Half the plane's side in fields. */
    public static final float HALF = 200f;

    private final Node node = new Node("classic-ground");
    private final Geometry plane;

    /** @param colour the ground's colour (0xRRGGBB, sRGB) */
    public ClassicGroundView(AssetLibrary assets, int colour) {
        plane = new Geometry("ground", new Quad(2f * HALF, 2f * HALF));
        float[] rgb = SrgbColours.rgb(colour, true);
        ColorRGBA linear = new ColorRGBA(rgb[0], rgb[1], rgb[2], 1f);
        Material material = new Material(assets.manager(), AssetLibrary.LIGHTING);
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", linear);
        material.setColor("Diffuse", linear.clone());
        material.setColor("Specular", ColorRGBA.Black);
        material.setFloat("Shininess", 1f);
        plane.setMaterial(material);
        plane.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        plane.setLocalTranslation(Lightning.CENTRE - HALF, Socket.TOP, Lightning.CENTRE + HALF);
        node.attachChild(plane);
        node.setShadowMode(ShadowMode.Receive);
    }

    public Node node() {
        return node;
    }

    /** The plane (tests). */
    Geometry plane() {
        return plane;
    }
}
