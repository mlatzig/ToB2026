package de.latzig.game.tob.client.view;

import java.util.Optional;

import com.jme3.scene.Node;

import de.latzig.game.tob.client.logic.CellColours;
import de.latzig.game.tob.client.logic.ClassicColours;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.Meteors;
import de.latzig.game.tob.client.logic.effects.SkyOptions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Phase 5e (decision 5): the look of one scene build, the Remake look or the Classic Look, taken from the Look option
 * when a scene is built (a tower start, a menu demo or interlude, the designer's preview) and kept for that scene's
 * life; the one place the scene builders ({@code GameState}, {@code MenuBackgroundState}, {@code EditorPreview})
 * switch. The Classic Look (spec §3): the floors, boxes and lifts in the tower's original colours
 * ({@link ClassicColours}), the objects in their static classic textures ({@link ClassicMaterials}, the library's one
 * set), the plain sky gradient without clouds, the flat ground ({@link ClassicGroundView}) on which the meteors
 * strike, no distance fog and a white sun (the ToB 2009 sun colour is a look setting, decision 3). Every scene build
 * sets the shared fog ({@link #applyFog}) in both looks, so a Remake scene built after a Classic one has its fog
 * again.
 */
public final class SceneLook {

    private final boolean classic;
    private final TowerColours colours;
    private final ClassicColours classicColours;
    private final SceneMaterials materials;

    private SceneLook(boolean classic, TowerColours colours, ClassicColours classicColours,
            SceneMaterials materials) {
        this.classic = classic;
        this.colours = colours;
        this.classicColours = classicColours;
        this.materials = materials;
    }

    /** The look of {@code tower}'s scene: the Classic Look if {@code classic}, else the Remake look. */
    public static SceneLook of(AssetLibrary assets, TowerDefinition tower, boolean classic) {
        TowerColours colours = TowerColours.of(tower);
        if (!classic) {
            return new SceneLook(false, colours, null, SceneMaterials.remake(assets));
        }
        return new SceneLook(true, colours, ClassicColours.of(tower), assets.classicMaterials());
    }

    /** The sun's colour of {@code tower}'s scene (0xRRGGBB): the Remake look's, white in the Classic Look. */
    public static int sun(TowerDefinition tower, boolean classic) {
        return classic ? 0xFFFFFF : TowerColours.of(tower).sun();
    }

    public boolean classic() {
        return classic;
    }

    /** The tower's colours: the sky (both looks), the Remake look's ground, floors and sun. */
    public TowerColours colours() {
        return colours;
    }

    /** The base geometry's colours. */
    public CellColours cells() {
        return classic ? classicColours : colours;
    }

    public SceneMaterials materials() {
        return materials;
    }

    /** The sun's colour (0xRRGGBB). */
    public int sun() {
        return classic ? 0xFFFFFF : colours.sun();
    }

    /** Sets the shared fog: the Remake look's towards the horizon colour (Phase 4b-4), none in the Classic Look. */
    public void applyFog(AssetLibrary assets) {
        assets.setFogColour(colours.skyHorizon());
        assets.setFog(!classic);
    }

    /** The world of {@code world} in this look. */
    public WorldView world(AssetLibrary assets, WorldState world, boolean ambientEffects) {
        return new WorldView(assets, world, cells(), ambientEffects, materials,
                classic ? Meteors.FLAT_GROUND : Meteors.ground(world.tower()));
    }

    /** The sky of {@code tower} in this look. */
    public SkyView sky(AssetLibrary assets, TowerDefinition tower, SkyOptions options,
            Optional<SkyPreparation> prepared) {
        return new SkyView(assets, tower, colours, options, prepared, classic);
    }

    /** The ground: the Remake look's terrain and socket, or the Classic Look's flat plane. */
    public Node ground(AssetLibrary assets, TowerDefinition tower) {
        return classic ? new ClassicGroundView(assets, classicColours.ground()).node()
                : new TerrainView(assets, tower, colours).node();
    }
}
