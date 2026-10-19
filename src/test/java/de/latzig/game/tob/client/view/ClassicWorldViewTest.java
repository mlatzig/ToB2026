package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;

import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.SrgbColours;
import de.latzig.game.tob.client.logic.effects.AmbientTuning;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.core.model.StaticQuadrant;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;

/**
 * Phase 5e (Classic Look, decisions 2, 3 and 4): a whole tower's world in the Classic Look (headless): the floors,
 * boxes and lifts untextured in their vertex colours, every object on its coloured area map, the blue pulse a
 * translucent single-colour surface, the floor debris untextured as well.
 */
class ClassicWorldViewTest {

    private static List<Geometry> geometries(Spatial root) {
        List<Geometry> found = new ArrayList<>();
        root.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                found.add(geometry);
            }
        });
        return found;
    }

    /** Original tower 058 "It Takes Two to Tango", the one original tower with the blue pulse. */
    private static TowerDefinition tango() throws IOException {
        return TowerAssets.allTowers().stream().filter(t -> t.id().equals("original-058")).findFirst()
                .orElseThrow();
    }

    @Test
    void theClassicWorldIsUntexturedFloorsAreaMappedObjectsAndATranslucentBluePulse() throws IOException {
        assumeTrue(GeneratedAssets.available() && TowerAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        TowerDefinition tower = tango();
        assertTrue(tower.effects().bluePulse());
        ClassicColours colours = ClassicColours.of(tower);
        ClassicMaterials classic = new ClassicMaterials(assets);

        WorldView view = new WorldView(assets, Simulation.start(tower, SimOptions.DEFAULT).world(), colours, true,
                classic, Meteors.FLAT_GROUND);

        Node base = (Node) view.node().getChild("base");
        List<Geometry> floors = geometries(base);
        assertTrue(floors.size() > 0);
        for (Geometry floor : floors) {
            assertSame(classic.base(StaticQuadrant.TOP), floor.getMaterial(), floor.getName());
            assertNull(floor.getMaterial().getParam("DiffuseMap"), "untextured");
        }
        int objects = 0;
        for (Spatial child : view.node().getChildren()) {
            if (child.getName().startsWith("object-")) {
                for (Geometry part : geometries(child)) {
                    Material material = part.getMaterial();
                    if (material.getMaterialDef().getAssetName().equals(AssetLibrary.UNLIT_FOG)) {
                        continue;
                    }
                    Texture map = (Texture) material.getParam("DiffuseMap").getValue();
                    assertEquals(Texture.MagFilter.Nearest, map.getMagFilter(), part.getName() + ": an area map");
                    assertNull(material.getParam("NormalMap"), part.getName());
                    objects++;
                }
            }
        }
        assertTrue(objects > 0);
        Geometry haze = (Geometry) view.node().getChild("haze");
        assertEquals("Common/MatDefs/Misc/Unshaded.j3md", haze.getMaterial().getMaterialDef().getAssetName());
        assertEquals(BlendMode.Alpha, haze.getMaterial().getAdditionalRenderState().getBlendMode());
        assertNull(haze.getMaterial().getParam("ColorMap"), "a single colour");
        float now = AmbientTuning.HAZE_PULSE_FRAMES / 4f;
        view.updateEffects(now, Vector3f.UNIT_X, Vector3f.UNIT_Y);
        ColorRGBA colour = (ColorRGBA) haze.getMaterial().getParam("Color").getValue();
        float brightness = AmbientTuning.hazeBrightness(now);
        assertEquals((MaterialTuning.CLASSIC_PULSE_COLOUR & 0xFF) / 255f * brightness, colour.b, 1e-6f, "pulsing");
        assertEquals(MaterialTuning.CLASSIC_PULSE_ALPHA, colour.a, 0f, "translucent");
    }

    @Test
    void aFloorBlastInTheClassicLookIsUntextured() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        ClassicMaterials classic = new ClassicMaterials(assets);
        ExplosionView explosions = new ExplosionView(assets, 1L, classic);

        explosions.explodeFloor(new CellPos(4, 11, 0), 0xEE0000, 0f);

        List<Geometry> debris = geometries(explosions.node());
        assertTrue(debris.size() > 0);
        for (Geometry piece : debris) {
            assertSame(classic.base(StaticQuadrant.TOP), piece.getMaterial());
        }
    }

    /** Controller gamma rule: the pulse's plain material colour is the sRGB 0x2040FF converted to linear. */
    @Test
    void theClassicPulseColourIsTheLinearisedSrgbBlue() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        AssetLibrary assets = new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
        ClassicMaterials classic = new ClassicMaterials(assets);

        ColorRGBA colour = (ColorRGBA) classic.haze().getParam("Color").getValue();

        int rgb = MaterialTuning.CLASSIC_PULSE_COLOUR;
        assertEquals(SrgbColours.toLinear(((rgb >> 16) & 0xFF) / 255f), colour.r, 1e-6f, "red");
        assertEquals(SrgbColours.toLinear(((rgb >> 8) & 0xFF) / 255f), colour.g, 1e-6f, "green");
        assertEquals(SrgbColours.toLinear((rgb & 0xFF) / 255f), colour.b, 1e-6f, "blue");
        assertTrue(colour.r < 0x20 / 255f && colour.g < 0x40 / 255f, "darker than the sRGB values: linear");
        assertEquals(MaterialTuning.CLASSIC_PULSE_ALPHA, colour.a, 0f, "translucent");
        assertTrue(classic.haze() != classic.haze(), "a new material each call");
    }
}
