package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.TowerColours;
import de.latzig.game.tob.client.logic.effects.MoonDesign;
import de.latzig.game.tob.client.logic.effects.Terrain;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.RemakeVisuals;
import de.latzig.game.tob.core.model.Rgb12;
import de.latzig.game.tob.core.model.TowerDefinition;

/** Phase 6b spec decision 8: ToB 2009's "Effects" dialog with the moon fields instead of Sun Size and Moons. */
class EffectsDialogTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDefinition tower() {
        return EditorTowers.blank("new-000", 0);
    }

    private static TowerDefinition withMoon(TowerDefinition t, Moon moon, Optional<RemakeVisuals> remake) {
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), moon, t.palette(), t.sounds(), t.lock(), t.grid(), remake);
    }

    private static String sizeName(MoonDesign.SizeClass size) {
        return switch (size) {
            case NORMAL -> "Normal";
            case LARGE -> "Large";
            case GIANT -> "Giant";
        };
    }

    @Test
    void theDialogHasToB2009sGroupsAndTheMoonFieldsInsteadOfSunSize() {
        EffectsDialog dialog = new EffectsDialog(tower(), TEXTS);
        EditorForm form = dialog.form();

        assertEquals("Effects", TEXTS.get(dialog.captionKey()));
        assertEquals(List.of("Terrain", "Tower", "Additional FX"), form.groups().stream().map(TEXTS::get).toList());
        assertEquals(List.of("Sky Colors", "Sun Color", "Moon", "Moon Type", "Moon Height", "Moon Size",
                "Ground Colors", "Ground Height", "Stars", "Pattern", "Pattern Colors", "Lift Color", "Clouds"),
                form.fields().stream().map(EditorForm.Field::rowKey).filter(key -> !key.isEmpty()).map(TEXTS::get)
                        .toList());
        assertEquals(List.of("Lightning?", "Meteor?", "Pulse?"), List.of(EffectsDialog.LIGHTNING,
                EffectsDialog.METEOR, EffectsDialog.PULSE).stream().map(i -> TEXTS.get(form.field(i).labelKey()))
                .toList());
        assertTrue(form.fields().stream().noneMatch(f -> TEXTS.get(f.labelKey()).contains("Sun Size")));
        assertTrue(form.fields().stream().noneMatch(f -> TEXTS.get(f.labelKey()).contains("Moon X")));
        assertTrue(dialog.randomizable());
    }

    @Test
    void theFieldsShowTheTowersLookOriginal000s() {
        EditorForm form = new EffectsDialog(tower(), TEXTS).form();

        assertEquals(0x0088CC, form.field(EffectsDialog.SKY_1).value(), "palette entry 8, 08C");
        assertTrue(form.field(EffectsDialog.SKY_1).twelveBit());
        assertEquals(0x000000, form.field(EffectsDialog.SKY_2).value(), "palette entry 7");
        assertEquals(0xFFFFFF, form.field(EffectsDialog.SUN).value());
        assertEquals("North", form.field(EffectsDialog.MOON).display());
        assertEquals("Grey Moon", form.field(EffectsDialog.MOON_TYPE).display());
        assertEquals(EffectsDialog.MAX_MOON_HEIGHT - 28, form.field(EffectsDialog.MOON_HEIGHT).value(), "y 28");
        assertEquals(sizeName(MoonDesign.design(0, "new-000".hashCode(), 0f, 0f).size()),
                form.field(EffectsDialog.MOON_SIZE).display(), "no stored size: the seed's");
        assertEquals(0x004B4B, form.field(EffectsDialog.GROUND_1).value());
        assertEquals(0x006969, form.field(EffectsDialog.GROUND_2).value());
        assertEquals(6, form.field(EffectsDialog.GROUND_HEIGHT).value());
        assertEquals("100", form.field(EffectsDialog.STARS).display(), "density 3: ToB 2009's 100 stars");
        assertEquals("Crossed", form.field(EffectsDialog.PATTERN).display());
        assertEquals(0xA500A5, form.field(EffectsDialog.PATTERN_1).value());
        assertEquals(0xE10000, form.field(EffectsDialog.PATTERN_2).value());
        assertEquals(0xFFFFFF, form.field(EffectsDialog.LIFT).value());
        assertFalse(form.field(EffectsDialog.LIGHTNING).on());
        assertEquals("None", form.field(EffectsDialog.CLOUDS).display());
    }

    @Test
    void theMoonSizeFollowsTheTypeAndTheTowersSeedUntilOneIsChosen() {
        EffectsDialog dialog = new EffectsDialog(tower(), TEXTS);
        EditorForm form = dialog.form();
        long seed = "new-000".hashCode();

        for (int type = 0; type < EffectsDialog.MOON_TYPES; type++) {
            form.field(EffectsDialog.MOON_TYPE).value(type);
            dialog.refresh();
            assertEquals(sizeName(MoonDesign.design(type, seed, 0f, 0f).size()),
                    form.field(EffectsDialog.MOON_SIZE).display(), "type " + type);
        }
        form.field(EffectsDialog.MOON_TYPE).value(0);
        dialog.refresh();
        assertEquals(Optional.empty(), dialog.apply(tower()).moon().size(), "the seed's size is not stored");
        int seeds = form.field(EffectsDialog.MOON_SIZE).value();
        int chosen = (seeds + 1) % (Moon.MAX_SIZE + 1);
        form.field(EffectsDialog.MOON_SIZE).value(chosen);
        for (int type = 0; type < EffectsDialog.MOON_TYPES; type++) {
            form.field(EffectsDialog.MOON_TYPE).value(type);
            dialog.refresh();
            assertEquals(chosen, form.field(EffectsDialog.MOON_SIZE).value(), "a chosen size stays, type " + type);
        }
        assertTrue(form.field(EffectsDialog.MOON_SIZE).focusable());
        assertEquals(Optional.of(chosen), dialog.apply(tower()).moon().size(), "a chosen size is stored");

        form.focus(EffectsDialog.MOON);
        form.key(UiKey.LEFT);
        dialog.refresh();
        assertEquals("None", form.field(EffectsDialog.MOON).display());
    }

    @Test
    void aStoredMoonSizeIsShownKeptAndChanged() {
        TowerDefinition stored = withMoon(tower(), new Moon(1, 2, 8, 28, 2, Optional.of(Moon.MAX_SIZE)),
                tower().remake());
        EffectsDialog dialog = new EffectsDialog(stored, TEXTS);
        EditorForm form = dialog.form();

        assertEquals("Giant", form.field(EffectsDialog.MOON_SIZE).display());
        form.field(EffectsDialog.MOON_TYPE).value(4);
        dialog.refresh();
        assertEquals("Giant", form.field(EffectsDialog.MOON_SIZE).display(), "a stored size does not follow the type");
        assertEquals(new Moon(1, 4, 8, 28, 2, Optional.of(Moon.MAX_SIZE)), dialog.apply(stored).moon());

        form.focus(EffectsDialog.MOON_SIZE);
        form.key(UiKey.LEFT);
        assertEquals("Large", form.field(EffectsDialog.MOON_SIZE).display());
        assertEquals(new Moon(1, 4, 8, 28, 2, Optional.of(1)), dialog.apply(stored).moon());
    }

    @Test
    void okWritesTheChangedFieldsIntoPaletteMoonEffectsAndTheToB2009Look() {
        TowerDefinition tower = tower();
        EffectsDialog dialog = new EffectsDialog(tower, TEXTS);
        EditorForm form = dialog.form();
        form.field(EffectsDialog.SKY_1).value(0x123456);
        form.field(EffectsDialog.MOON).value(3);
        form.field(EffectsDialog.MOON_TYPE).value(3);
        form.field(EffectsDialog.LIGHTNING).value(1);
        form.field(EffectsDialog.STARS).value(1);
        form.field(EffectsDialog.GROUND_HEIGHT).value(2);
        form.field(EffectsDialog.CLOUDS).value(2);
        form.field(EffectsDialog.LIFT).value(0x3F3FFF);

        TowerDefinition set = dialog.apply(tower);

        assertEquals(new Rgb12(1, 3, 5), set.palette().colours().get(EffectsDialog.SKY_1_ENTRY), "12 bits, rounded");
        assertEquals(tower.palette().colours().get(EffectsDialog.SKY_2_ENTRY),
                set.palette().colours().get(EffectsDialog.SKY_2_ENTRY));
        assertEquals(new Moon(2, 3, 8, 28, 2), set.moon(), "South, a gas planet; position, rotation and size kept");
        assertEquals(new Effects(true, false, false, 1, 0, 1), set.effects(), "colour pattern and sky style kept");
        RemakeVisuals remake = set.remake().orElseThrow();
        assertEquals(new RemakeVisuals(0xFFFFFF, 3, 0x004B4B, 0x006969, 2, 0x3F3FFF, false, true, 0, 0xA500A5,
                0xE10000), remake, "sun size kept; heavy clouds");
        assertEquals(tower.grid(), set.grid());
        assertEquals(tower.conditions(), set.conditions());
    }

    @Test
    void unchangedFieldsKeepTheTowersRawData() {
        TowerDefinition plain = withMoon(tower(), new Moon(6, 7, 128, 56, 1), Optional.empty());
        EffectsDialog dialog = new EffectsDialog(plain, TEXTS);
        EditorForm form = dialog.form();
        dialog.refresh();
        assertSame(plain, dialog.apply(plain), "nothing changed");
        assertEquals("None", form.field(EffectsDialog.MOON).display(), "heading 6 is never shown");
        assertEquals(0, form.field(EffectsDialog.MOON_HEIGHT).value(), "y 56 shown as the lowest height");

        form.field(EffectsDialog.MOON_HEIGHT).value(45);
        TowerDefinition moved = dialog.apply(plain);
        assertEquals(new Moon(6, 7, 128, 10, 1), moved.moon(), "only y written (55 - 45); x kept");
        assertEquals(Optional.empty(), moved.remake(), "no ToB 2009 look field changed: none written");

        form.field(EffectsDialog.SUN).value(0xFFEEDD);
        RemakeVisuals written = dialog.apply(plain).remake().orElseThrow();
        TowerColours defaults = TowerColours.of(plain);
        assertEquals(new RemakeVisuals(0xFFEEDD, EffectsDialog.DEFAULT_SUN_SIZE, defaults.ground(),
                defaults.ground2(), Terrain.DEFAULT_GROUND_HEIGHT, TowerColours.DEFAULT_LIFT, false, false,
                TowerColours.CROSSED, defaults.pattern1(), defaults.pattern2()), written,
                "the look the tower showed, with the new sun colour");
    }

    @Test
    void randomizeColorsFollowsToB2009sRulesAndKeepsTheSkyTo12Bits() {
        for (int seed = 0; seed < 200; seed++) {
            EffectsDialog dialog = new EffectsDialog(tower(), TEXTS);
            EditorForm form = dialog.form();
            dialog.randomize(new Random(seed));

            for (int field : List.of(EffectsDialog.SKY_1, EffectsDialog.SKY_2)) {
                int rgb = form.field(field).value();
                assertEquals(EffectsDialog.twelveBit(rgb), rgb, "seed " + seed);
            }
            assertTrue(((form.field(EffectsDialog.SKY_1).value() >> 16) & 0xFF) >= 119, "a light sky colour 1");
            assertChannels(form.field(EffectsDialog.SUN).value(), List.of(191, 255), 0xBFBFBF);
            assertChannels(form.field(EffectsDialog.LIFT).value(), List.of(63, 255), 0x3F3F3F);
            int ground = (form.field(EffectsDialog.GROUND_1).value() >> 8) & 0xFF;
            assertTrue(ground >= 32 && ground < 224, "ground colour 1: 32-223 per channel");
            assertEquals("Grey Moon", form.field(EffectsDialog.MOON_TYPE).display(), "only the colours");
        }
        EffectsDialog one = new EffectsDialog(tower(), TEXTS);
        EffectsDialog two = new EffectsDialog(tower(), TEXTS);
        one.randomize(new Random(7));
        two.randomize(new Random(7));
        assertEquals(one.apply(tower()), two.apply(tower()));
        assertNotEquals(tower(), one.apply(tower()));
    }

    private static void assertChannels(int rgb, List<Integer> allowed, int excluded) {
        assertTrue(allowed.contains((rgb >> 16) & 0xFF) && allowed.contains((rgb >> 8) & 0xFF)
                && allowed.contains(rgb & 0xFF), Integer.toHexString(rgb));
        assertNotEquals(excluded, rgb);
    }
}
