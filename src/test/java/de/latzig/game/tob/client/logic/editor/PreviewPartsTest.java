package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Socket;
import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.WorldState;

/** Phase 6b spec decision 3: the preview rebuilds only the part of the scene an edit changed. */
class PreviewPartsTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDefinition tower() {
        TowerGrid grid = TowerGrid.empty();
        for (int x = 4; x <= 11; x++) {
            grid = TowerEdits.place(grid, EditorElement.FLOOR, x, 4, 0).grid();
            grid = TowerEdits.place(grid, EditorElement.FLOOR, x, 11, 0).grid();
        }
        return EditorTowers.withGrid(EditorTowers.blank("new-000", 0), grid);
    }

    private static TowerDefinition effects(TowerDefinition tower, int field, int value) {
        EffectsDialog dialog = new EffectsDialog(tower, TEXTS);
        dialog.form().field(field).value(value);
        return dialog.apply(tower);
    }

    @Test
    void settingsChangeNothingThatIsShown() {
        TowerDefinition tower = tower();
        SettingsDialog settings = new SettingsDialog(tower, TEXTS);
        settings.form().type('X');
        settings.form().field(SettingsDialog.KLONDIKES).value(5);
        settings.form().field(SettingsDialog.MINES).value(1);

        assertEquals(PreviewParts.of(tower), PreviewParts.of(settings.apply(tower)));
    }

    @Test
    void anEditOfTheGridRebuildsTheWorldAndTheTerrainOnlyIfTheFootprintGrows() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);
        TowerDefinition inside = EditorTowers.withGrid(tower,
                TowerEdits.place(tower.grid(), EditorElement.FLOOR, 7, 7, 1).grid());
        TowerDefinition outside = EditorTowers.withGrid(tower,
                TowerEdits.place(tower.grid(), EditorElement.FLOOR, 14, 7, 0).grid());

        PreviewParts in = PreviewParts.of(inside);
        assertNotEquals(before.world(), in.world());
        assertEquals(before.ground(), in.ground(), "the same footprint: the same socket");
        assertEquals(before.sky(), in.sky());
        assertNotEquals(before.ground(), PreviewParts.of(outside).ground(), "a wider footprint: a wider socket");
    }

    @Test
    void theEffectsRebuildTheirOwnPart() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);

        PreviewParts moon = PreviewParts.of(effects(tower, EffectsDialog.MOON, 0));
        assertEquals(new Moon(4, 0, 8, 28, 2), moon.sky().moon());
        assertNotEquals(before.sky(), moon.sky());
        assertEquals(before.world(), moon.world());
        assertEquals(before.ground(), moon.ground());

        PreviewParts lightning = PreviewParts.of(effects(tower, EffectsDialog.LIGHTNING, 1));
        assertNotEquals(before.world(), lightning.world());
        assertEquals(before.sky(), lightning.sky());
        assertEquals(before.ground(), lightning.ground());

        PreviewParts ground = PreviewParts.of(effects(tower, EffectsDialog.GROUND_HEIGHT, 1));
        assertNotEquals(before.ground(), ground.ground());
        assertNotEquals(before.world(), ground.world(), "meteors strike the relief");
        assertEquals(before.sky(), ground.sky());

        PreviewParts clouds = PreviewParts.of(effects(tower, EffectsDialog.CLOUDS, 1));
        assertNotEquals(before.sky(), clouds.sky());

        PreviewParts sky = PreviewParts.of(effects(tower, EffectsDialog.SKY_2, 0x112233));
        assertNotEquals(before.sky(), sky.sky());
    }

    /**
     * Final review M1: the ground and world keys now carry only the {@link TowerColours} fields the terrain and the
     * world actually read ({@link Terrain}, {@link Socket}'s ground 1/2; {@code BaseGeometry}, {@code BaseView}'s
     * pattern, pattern colours and lift) — not the whole {@code TowerColours}, which also carries the sky and sun
     * colours those parts never use (the sun's colour is set every frame regardless of a rebuild).
     */
    @Test
    void aSkyOrSunColourChangeRebuildsOnlyTheSky() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);

        PreviewParts sky1 = PreviewParts.of(effects(tower, EffectsDialog.SKY_1, 0x112233));
        assertNotEquals(before.sky(), sky1.sky());
        assertEquals(before.ground(), sky1.ground(), "the terrain never uses the sky colour");
        assertEquals(before.world(), sky1.world(), "the world never uses the sky colour");

        PreviewParts sky2 = PreviewParts.of(effects(tower, EffectsDialog.SKY_2, 0x112233));
        assertNotEquals(before.sky(), sky2.sky());
        assertEquals(before.ground(), sky2.ground());
        assertEquals(before.world(), sky2.world());

        PreviewParts sun = PreviewParts.of(effects(tower, EffectsDialog.SUN, 0x445566));
        assertEquals(before.sky(), sun.sky(), "the sun's colour is not part of the sky's key; it is set every frame");
        assertEquals(before.ground(), sun.ground());
        assertEquals(before.world(), sun.world());
    }

    /**
     * Phase 5e: the Classic Look's keys follow the palette, not the ToB 2009 look settings: no clouds, the flat
     * ground (no relief, no socket), the palette as the world's key (so a sky colour edit rebuilds the world too).
     */
    @Test
    void theClassicLooksKeysFollowThePaletteNotTheToB2009LookSettings() {
        TowerDefinition tower = effects(tower(), EffectsDialog.CLOUDS, 1);
        PreviewParts remake = PreviewParts.of(tower);

        PreviewParts classic = PreviewParts.of(tower, true);

        assertTrue(remake.sky().clouds().isPresent());
        assertTrue(classic.sky().clouds().isEmpty(), "no clouds");
        assertEquals(Optional.empty(), classic.ground().socket(), "no socket");
        assertEquals(0, classic.ground().height(), "no relief");
        assertEquals(tower.palette(), classic.world().classicPalette());
        assertNull(remake.world().classicPalette());
        assertEquals(remake, PreviewParts.of(tower, false));
        assertEquals(classic, PreviewParts.of(effects(tower, EffectsDialog.GROUND_1, 0x224466), true),
                "a ToB 2009 ground colour is not shown");
        PreviewParts sky = PreviewParts.of(effects(tower, EffectsDialog.SKY_1, 0x112233), true);
        assertNotEquals(classic.sky(), sky.sky());
        assertNotEquals(classic.world(), sky.world(), "the palette is the world's key");
        assertEquals(classic.ground(), sky.ground());
    }

    @Test
    void aGroundColourChangeRebuildsOnlyTheGround() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);

        PreviewParts ground1 = PreviewParts.of(effects(tower, EffectsDialog.GROUND_1, 0x224466));
        assertNotEquals(before.ground(), ground1.ground());
        assertEquals(before.sky(), ground1.sky());
        assertEquals(before.world(), ground1.world());

        PreviewParts ground2 = PreviewParts.of(effects(tower, EffectsDialog.GROUND_2, 0x335577));
        assertNotEquals(before.ground(), ground2.ground());
        assertEquals(before.sky(), ground2.sky());
        assertEquals(before.world(), ground2.world());
    }

    @Test
    void aPatternOrLiftColourChangeRebuildsOnlyTheWorld() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);

        PreviewParts pattern = PreviewParts.of(effects(tower, EffectsDialog.PATTERN, 1));
        assertNotEquals(before.world(), pattern.world());
        assertEquals(before.sky(), pattern.sky());
        assertEquals(before.ground(), pattern.ground());

        PreviewParts pattern1 = PreviewParts.of(effects(tower, EffectsDialog.PATTERN_1, 0x224466));
        assertNotEquals(before.world(), pattern1.world());
        assertEquals(before.sky(), pattern1.sky());
        assertEquals(before.ground(), pattern1.ground());

        PreviewParts pattern2 = PreviewParts.of(effects(tower, EffectsDialog.PATTERN_2, 0x557799));
        assertNotEquals(before.world(), pattern2.world());
        assertEquals(before.sky(), pattern2.sky());
        assertEquals(before.ground(), pattern2.ground());

        PreviewParts lift = PreviewParts.of(effects(tower, EffectsDialog.LIFT, 0x998877));
        assertNotEquals(before.world(), lift.world());
        assertEquals(before.sky(), lift.sky());
        assertEquals(before.ground(), lift.ground());
    }

    /**
     * Author answer (question 3) / Task 0 result: Moon Height and the now-storable Moon Size are part of the
     * model's {@link Moon}, which the sky's key carries whole ({@link PreviewParts.Sky#moon()}) — so a moon height
     * or moon size edit rebuilds the sky exactly like a moon direction or type edit, and touches no other part.
     */
    @Test
    void theMoonHeightAndTheStoredMoonSizeRebuildOnlyTheSky() {
        TowerDefinition tower = tower();
        PreviewParts before = PreviewParts.of(tower);

        PreviewParts height = PreviewParts.of(effects(tower, EffectsDialog.MOON_HEIGHT, 10));
        assertNotEquals(before.sky(), height.sky());
        assertEquals(before.world(), height.world());
        assertEquals(before.ground(), height.ground());

        EffectsDialog dialog = new EffectsDialog(tower, TEXTS);
        int shown = dialog.form().field(EffectsDialog.MOON_SIZE).value();
        int chosen = (shown + 1) % (Moon.MAX_SIZE + 1);
        dialog.form().field(EffectsDialog.MOON_SIZE).value(chosen);
        TowerDefinition sized = dialog.apply(tower);
        PreviewParts size = PreviewParts.of(sized);
        assertEquals(Optional.of(chosen), size.sky().moon().size(), "the chosen size is stored");
        assertNotEquals(before.sky(), size.sky());
        assertEquals(before.world(), size.world());
        assertEquals(before.ground(), size.ground());
    }

    @Test
    void anotherIdIsAnotherSeedForEveryPart() {
        TowerDefinition tower = tower();
        TowerDefinition renumbered = EditorTowers.withId(tower, "mine-003");

        PreviewParts before = PreviewParts.of(tower);
        PreviewParts after = PreviewParts.of(renumbered);
        assertNotEquals(before.sky(), after.sky());
        assertNotEquals(before.ground(), after.ground());
        assertNotEquals(before.world(), after.world());
    }

    @Test
    void aLiftWithoutItsStopShowsAsAFloorSoTheWorldCanBeBuilt() {
        TowerDefinition tower = tower();
        TowerGrid grid = TowerEdits.place(tower.grid(), EditorElement.LIFT_UP, 5, 4, 5).grid();
        grid = TowerEdits.place(grid, EditorElement.of(ObjectType.KLD), 5, 4, 5).grid();
        grid = TowerEdits.place(grid, EditorElement.LIFT_UP, 6, 4, 0).grid();
        TowerDefinition marked = EditorTowers.withGrid(tower, grid);
        assertEquals(BaseType.LIFT_UP_START, marked.grid().cell(5, 4, 5).base(), "no level above: no stop");

        TowerDefinition viewable = PreviewParts.viewable(marked);

        assertEquals(BaseType.FLOOR, viewable.grid().cell(5, 4, 5).base());
        assertEquals(ObjectType.KLD, viewable.grid().cell(5, 4, 5).object());
        assertEquals(BaseType.LIFT_UP_START, viewable.grid().cell(6, 4, 0).base(), "a whole lift stays");
        WorldState.load(viewable, new SimOptions(true));
        assertSame(tower, PreviewParts.viewable(tower), "nothing to change: the same tower");
    }

    /**
     * Final review I1: {@code EditorToolbox.copyLevel} copies cells verbatim, objects included, so Toolbox → Copy
     * Level can place a spider twice ({@code WorldState.load} then refuses with "ZAP placed twice"); the preview
     * must still be able to build the world.
     */
    @Test
    void aSpiderPlacedTwiceByCopyLevelKeepsOnlyItsFirstCopySoTheWorldCanBeBuilt() {
        TowerDefinition tower = tower();
        TowerGrid grid = TowerEdits.place(tower.grid(), EditorElement.of(ObjectType.ZAP), 5, 4, 0).grid();
        grid = EditorToolbox.copyLevel(grid, 0, 1);
        TowerDefinition duplicated = EditorTowers.withGrid(tower, grid);
        assertEquals(ObjectType.ZAP, duplicated.grid().cell(5, 4, 0).object());
        assertEquals(ObjectType.ZAP, duplicated.grid().cell(5, 4, 1).object());
        assertThrows(IllegalArgumentException.class, () -> WorldState.load(duplicated, new SimOptions(true)),
                "the raw tower is not loadable, as the review found");

        TowerDefinition viewable = PreviewParts.viewable(duplicated);

        assertEquals(ObjectType.ZAP, viewable.grid().cell(5, 4, 0).object(), "the first copy (level 0) stays");
        assertNull(viewable.grid().cell(5, 4, 1).object(), "the later copy (level 1) is dropped");
        WorldState.load(viewable, new SimOptions(true));
        assertSame(tower, PreviewParts.viewable(tower), "nothing to change: the same tower");
    }
}
