package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jme3.material.Material;
import com.jme3.math.Vector2f;

import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.MaterialTuning;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.core.model.MeshPart;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.Palette;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;

/** Phase 5e (decision 5, spec §3): the one switch of the scene builders. */
class SceneLookTest {

    private static AssetLibrary assets() {
        assumeTrue(GeneratedAssets.available(), "generated assets not available");
        return new AssetLibrary(GeneratedAssets.assetManager(), GeneratedAssets.root());
    }

    @Test
    void theRemakeLookIsTheSceneAsBefore() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");

        SceneLook look = SceneLook.of(assets, tower, false);
        look.applyFog(assets);

        assertFalse(look.classic());
        assertEquals(TowerColours.of(tower), look.cells());
        assertEquals(TowerColours.of(tower).sun(), look.sun());
        assertEquals(TowerColours.of(tower).sun(), SceneLook.sun(tower, false));
        assertTrue(assets.fog(), "the horizon fog");
        assertEquals(AssetLibrary.color(TowerColours.of(tower).skyHorizon()), assets.fogColour());
        assertEquals("terrain", look.ground(assets, tower).getName());
        SkyView sky = look.sky(assets, tower, SkyOptions.DEFAULT, Optional.empty());
        assertTrue(sky.hasClouds(), "sample-000 has clouds");
        sky.dispose();
        MeshPart part = assets.element(ObjectType.ZAP).groups().get(0).parts().get(0);
        assertSame(assets.material(part, true), look.materials().part(ObjectType.ZAP, part, true));
    }

    /** Spec §4: the classic scene has no clouds, no fog and no terrain mesh. */
    @Test
    void theClassicLookHasNoCloudsNoFogNoTerrainAndAWhiteSun() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");

        SceneLook look = SceneLook.of(assets, tower, true);
        look.applyFog(assets);

        assertTrue(look.classic());
        assertEquals(ClassicColours.of(tower), look.cells());
        assertInstanceOf(ClassicMaterials.class, look.materials());
        assertEquals(0xFFFFFF, look.sun());
        assertEquals(0xFFFFFF, SceneLook.sun(tower, true));
        assertFalse(assets.fog(), "no distance fog");
        assertEquals("classic-ground", look.ground(assets, tower).getName());
        SkyView sky = look.sky(assets, tower, SkyOptions.DEFAULT, Optional.empty());
        assertFalse(sky.hasClouds(), "no clouds");
        sky.dispose();
        WorldView world = look.world(assets, Simulation.start(tower, SimOptions.DEFAULT).world(), true);
        assertTrue(world.node().getChild("base") != null, "a lift and a box: the classic base geometry builds");
    }

    /** Task 6 concern: the fog is shared state, so a Remake scene built after a Classic one has its fog again. */
    @Test
    void aRemakeSceneBuiltAfterAClassicOneHasItsFogAgain() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");
        Material terrain = assets.terrainMaterial();
        SceneLook.of(assets, tower, true).applyFog(assets);
        assertFalse(assets.fog(), "the classic scene: no fog");

        SceneLook.of(assets, tower, false).applyFog(assets);

        assertTrue(assets.fog(), "the Remake scene: the horizon fog again");
        assertEquals(new Vector2f(MaterialTuning.FOG_START, MaterialTuning.FOG_END),
                terrain.getParamValue("LinearFog"), "the Phase 4b-4 range on a material built before");
        assertEquals(AssetLibrary.color(TowerColours.of(tower).skyHorizon()), assets.fogColour());
    }

    /**
     * Rework (author, acceptance 2026-10-16): the objects' classic textures are static, coloured once by the
     * converter in one reference palette, so every Classic scene shares the library's one set of materials, whatever
     * the tower's palette; the floors keep the tower's palette.
     */
    @Test
    void everyClassicSceneSharesTheLibrarysClassicMaterials() {
        AssetLibrary assets = assets();
        TowerDefinition tower = SampleTowers.tower("sample-000");
        TowerDefinition other = new TowerDefinition(tower.id(), tower.group(), tower.index(), tower.title(),
                tower.author(), tower.conditions(), tower.behaviour(), tower.effects(), tower.moon(),
                new Palette(Collections.nCopies(Palette.SIZE, Rgb12.parseHex("FFF"))), tower.sounds(), tower.lock(),
                tower.grid(), tower.remake());
        assertNotEquals(tower.palette(), other.palette());
        MeshPart part = assets.element(ObjectType.ZAP).groups().get(0).parts().get(0);

        SceneLook first = SceneLook.of(assets, tower, true);
        SceneLook again = SceneLook.of(assets, other, true);

        assertSame(assets.classicMaterials(), first.materials());
        assertSame(first.materials(), again.materials(), "one set for every palette");
        assertEquals(ClassicColours.of(other), again.cells(), "the floors in the tower's palette");
        assertSame(first.materials().part(ObjectType.ZAP, part, true),
                again.materials().part(ObjectType.ZAP, part, true));
    }
}
