package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;

import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.Lightning;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.Rgb12;

/** Phase 5e (Classic Look, decision 1): the flat single-colour ground instead of the terrain. */
class ClassicGroundViewTest {

    /** The palette of "The Sacrifice" (as ClassicPaletteTest): entry 16 is the yellow ground CA0. */
    private static final Palette SACRIFICE = new Palette(Stream.of("000", "600", "800", "E00", "060", "080", "0E0",
            "000", "000", "0AA", "0EE", "00A", "00E", "777", "BBB", "FFF", "CA0", "860").map(Rgb12::parseHex).toList());

    @Test
    void theGroundIsOneFlatLitPlaneInTheFloorColourAroundTheTowerWithoutFog() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());

        ClassicGroundView ground = new ClassicGroundView(assets, 0xCCAA00);

        Geometry plane = ground.plane();
        ground.node().updateGeometricState();
        BoundingBox bound = (BoundingBox) plane.getWorldBound();
        assertEquals(Lightning.CENTRE, bound.getCenter().x, 1e-3f, "around the grid's centre");
        assertEquals(Socket.TOP, bound.getCenter().y, 1e-6f, "just below the level-0 floors");
        assertEquals(Lightning.CENTRE, bound.getCenter().z, 1e-3f);
        assertEquals(ClassicGroundView.HALF, bound.getXExtent(), 1e-3f);
        assertEquals(ClassicGroundView.HALF, bound.getZExtent(), 1e-3f);
        assertEquals(0f, bound.getYExtent(), 1e-3f, "flat");
        assertEquals(1f, plane.getWorldRotation().mult(Vector3f.UNIT_Z).y, 1e-6f, "facing up");
        assertEquals(linear(0xCCAA00), plane.getMaterial().getParamValue("Diffuse"));
        assertNull(plane.getMaterial().getParam("UseFog"));
        assertEquals(ShadowMode.Receive, plane.getShadowMode());
    }

    /** Controller gamma rule: the plain material colour is the palette's sRGB entry 16 converted to linear. */
    @Test
    void theGroundColourIsTheLinearisedPaletteEntry16() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        int entry16 = new ClassicColours(SACRIFICE, 3).ground();

        Geometry plane = new ClassicGroundView(assets, entry16).plane();

        ColorRGBA diffuse = (ColorRGBA) plane.getMaterial().getParamValue("Diffuse");
        float[] rgb = SrgbColours.rgb(entry16, true);
        assertEquals(rgb[0], diffuse.r, 1e-6f, "red");
        assertEquals(rgb[1], diffuse.g, 1e-6f, "green");
        assertEquals(rgb[2], diffuse.b, 1e-6f, "blue");
        assertEquals(0xCCAA00, entry16, "the yellow ground");
        assertEquals(1f, diffuse.a, 0f);
        assertEquals(diffuse, plane.getMaterial().getParamValue("Ambient"), "ambient as diffuse");
        assertNotEquals(AssetLibrary.color(entry16), diffuse,
                "not the raw sRGB channels of " + Integer.toHexString(entry16));
    }

    private static ColorRGBA linear(int colour) {
        float[] rgb = SrgbColours.rgb(colour, true);
        return new ColorRGBA(rgb[0], rgb[1], rgb[2], 1f);
    }
}
