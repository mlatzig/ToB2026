package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;

/** Phase 6b spec decision 8: ToB 2009's "Settings" dialog over ToB 2026's tower model. */
class SettingsDialogTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDefinition tower() {
        return EditorTowers.blank("new-000", 0);
    }

    @Test
    void theDialogHasToB2009sGroupsAndFieldsWithTheTowersValues() {
        SettingsDialog dialog = new SettingsDialog(tower(), TEXTS);
        EditorForm form = dialog.form();

        assertEquals("Settings", TEXTS.get(dialog.captionKey()));
        assertEquals(List.of("General", "Finishing Conditions", "Behaviour"),
                form.groups().stream().map(TEXTS::get).toList());
        assertEquals(List.of("Title", "Author", "Klondikes", "Robots", "TimeLimit", "External cams enabled?",
                "Mine destroys floor?", "Timebomb Speed"),
                form.fields().stream().map(f -> TEXTS.get(f.labelKey())).toList());
        assertEquals("", form.field(SettingsDialog.TITLE).text());
        assertEquals(1, form.field(SettingsDialog.KLONDIKES).value(), "the new tower's default");
        assertEquals(0, form.field(SettingsDialog.TIME_LIMIT).value(), "no time limit: 0");
        assertTrue(form.field(SettingsDialog.CAMERAS).on());
        assertFalse(form.field(SettingsDialog.MINES).on());
        assertEquals("Medium", form.field(SettingsDialog.TIME_BOMB).display());
        assertEquals(List.of("Slow", "Medium", "Fast", "Faster"), form.field(SettingsDialog.TIME_BOMB).choices());
        assertEquals(SettingsDialog.MAX_COUNT, form.field(SettingsDialog.ROBOTS).max());
    }

    @Test
    void okWritesTheChangedFieldsIntoTheTower() {
        TowerDefinition tower = tower();
        SettingsDialog dialog = new SettingsDialog(tower, TEXTS);
        EditorForm form = dialog.form();
        for (char c : "My Tower  ".toCharArray()) {
            form.type(c);
        }
        form.key(UiKey.TAB);
        for (char c : "Mario".toCharArray()) {
            form.type(c);
        }
        form.field(SettingsDialog.KLONDIKES).value(3);
        form.field(SettingsDialog.ROBOTS).value(2);
        form.click(SettingsDialog.TIME_LIMIT, EditorForm.PLUS);
        form.click(SettingsDialog.TIME_LIMIT, EditorForm.PLUS);
        form.click(SettingsDialog.CAMERAS, EditorForm.BODY);
        form.click(SettingsDialog.MINES, EditorForm.BODY);
        form.click(SettingsDialog.TIME_BOMB, EditorForm.PLUS);

        TowerDefinition set = dialog.apply(tower);

        assertEquals("My Tower", set.title(), "trailing spaces dropped");
        assertEquals("Mario", set.author());
        assertEquals(new Conditions(20, true, 3, 2), set.conditions());
        assertEquals(new Behaviour(false, true, 2), set.behaviour());
        assertEquals(tower.grid(), set.grid());
        assertEquals(tower.effects(), set.effects());
        assertEquals(tower.id(), set.id());
    }

    @Test
    void nothingChangedKeepsTheTowerAndTimeLimitZeroKeepsTheRawSeconds() {
        TowerDefinition limited = tower();
        SettingsDialog unchanged = new SettingsDialog(limited, TEXTS);
        assertSame(limited, unchanged.apply(limited), "OK without a change keeps the tower as it is");

        SettingsDialog on = new SettingsDialog(limited, TEXTS);
        on.form().field(SettingsDialog.TIME_LIMIT).value(250);
        TowerDefinition withLimit = on.apply(limited);
        assertEquals(new Conditions(250, true, 1, 0), withLimit.conditions());

        SettingsDialog off = new SettingsDialog(withLimit, TEXTS);
        assertEquals(250, off.form().field(SettingsDialog.TIME_LIMIT).value());
        off.form().field(SettingsDialog.TIME_LIMIT).value(0);
        assertEquals(new Conditions(250, false, 1, 0), off.apply(withLimit).conditions(),
                "0 switches the limit off; the raw seconds stay, as the model keeps them");
    }
}
