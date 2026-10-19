package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Phase 6b spec decision 8: the designer opens Settings and Effects, applies OK as one undo step, drops Cancel. */
class TowerDesignerDialogsTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDesigner designer(Path dir, long seed) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        return new TowerDesigner(new TowerLibraries(shipped, dir.resolve("user")), TEXTS, new Random(seed));
    }

    private static Optional<Hit> field(int index, int part) {
        return Optional.of(new Hit(Widget.EDITOR_FIELD, EditorForm.hitRow(index, part)));
    }

    private static Optional<Hit> button(int row) {
        return Optional.of(new Hit(Widget.EDITOR_DIALOG, row));
    }

    @Test
    void theTitleStripOpensSettingsAndAReadOnlyLibraryGreysBoth(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir, 1L);

        designer.press(Optional.of(Hit.of(Widget.EDITOR_TITLE)), false);
        assertInstanceOf(SettingsDialog.class, designer.towerDialog().orElseThrow());
        designer.key(UiKey.ESCAPE);
        assertEquals(Optional.empty(), designer.towerDialog());

        designer.run(Command.OPEN);
        designer.press(button(TowerDesigner.DIALOG_OK), false);
        assertTrue(designer.model().readOnly());
        assertFalse(designer.enabled(Command.SETTINGS));
        assertFalse(designer.enabled(Command.EFFECTS));
        designer.press(Optional.of(Hit.of(Widget.EDITOR_TITLE)), false);
        assertEquals(Optional.empty(), designer.towerDialog(), "the title strip does nothing in a read-only library");
    }

    @Test
    void settingsOkIsOneUndoStepAndCancelChangesNothing(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir, 1L);
        TowerDefinition before = designer.model().tower();

        designer.run(Command.SETTINGS);
        for (char c : "Babel".toCharArray()) {
            designer.typed(c);
        }
        designer.press(field(SettingsDialog.KLONDIKES, EditorForm.PLUS), false);
        designer.press(Optional.of(new Hit(Widget.EDITOR_CELL, 3)), false);
        designer.press(Optional.of(new Hit(Widget.EDITOR_CELL, 3)), true);
        assertEquals(before, designer.model().tower(), "nothing changes while the dialog is open");
        assertEquals("Babel", designer.shown().title(), "the preview shows the dialog's tower");
        designer.key(UiKey.ENTER);

        assertEquals("Babel", designer.model().tower().title());
        assertEquals(2, designer.model().tower().conditions().klondikes());
        assertEquals(TowerGrid.empty(), designer.model().tower().grid());
        assertTrue(designer.model().dirty());
        designer.key(UiKey.UNDO);
        assertEquals(before, designer.model().tower(), "OK was one undo step");
        designer.key(UiKey.REDO);

        designer.run(Command.SETTINGS);
        designer.typed('!');
        designer.press(button(TowerDesigner.DIALOG_CANCEL), false);
        assertEquals("Babel", designer.model().tower().title());
        assertEquals(designer.model().tower(), designer.shown());
    }

    @Test
    void effectsShowLiveInThePreviewTowerUntilOkOrCancel(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir, 1L);
        TowerDefinition before = designer.model().tower();

        designer.run(Command.EFFECTS);
        designer.press(field(EffectsDialog.LIGHTNING, EditorForm.BODY), false);
        designer.press(field(EffectsDialog.MOON, EditorForm.PLUS), false);
        assertTrue(designer.shown().effects().lightning());
        assertEquals(1, designer.shown().moon().heading(), "North to East");
        assertEquals(before, designer.model().tower());
        designer.key(UiKey.ESCAPE);
        assertEquals(before, designer.shown());

        designer.run(Command.EFFECTS);
        designer.press(field(EffectsDialog.PULSE, EditorForm.BODY), false);
        designer.press(button(TowerDesigner.DIALOG_OK), false);
        assertTrue(designer.model().tower().effects().bluePulse());
        assertEquals(Optional.empty(), designer.towerDialog());
    }

    @Test
    void aColourButtonOpensTheColourDialogWhoseChannelsShowAtOnce(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir, 1L);
        designer.run(Command.EFFECTS);

        designer.press(field(EffectsDialog.SUN, EditorForm.BODY), false);
        ColourDialog colour = designer.colourDialog().orElseThrow();
        assertEquals("Sun Color", TEXTS.get(colour.captionKey()));
        designer.press(field(ColourDialog.GREEN, EditorForm.MINUS), false);
        assertEquals(0xFFEFFF, designer.shown().remake().orElseThrow().sunColor(), "255 - 16, live");
        designer.key(UiKey.ESCAPE);
        assertEquals(Optional.empty(), designer.colourDialog());
        assertEquals(0xFFFFFF, designer.shown().remake().orElseThrow().sunColor(), "Cancel restores it");
        assertTrue(designer.towerDialog().isPresent(), "Esc closes only the colour dialog");

        designer.towerDialog().orElseThrow().form().focus(EffectsDialog.SKY_1);
        designer.key(UiKey.SPACE);
        designer.key(UiKey.RIGHT);
        designer.typed('x');
        designer.press(button(TowerDesigner.DIALOG_OK), false);
        assertEquals(0x1188CC, designer.towerDialog().orElseThrow().form().field(EffectsDialog.SKY_1).value());
        designer.key(UiKey.ENTER);
        assertEquals("18C", designer.model().tower().palette().colours().get(EffectsDialog.SKY_1_ENTRY).hex());
    }

    @Test
    void randomizeColorsUsesTheDesignersRandom(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir, 42L);
        TowerDefinition before = designer.model().tower();
        EffectsDialog expected = new EffectsDialog(before, TEXTS);
        expected.randomize(new Random(42L));

        designer.run(Command.EFFECTS);
        designer.press(button(TowerDesigner.DIALOG_RANDOMIZE), false);
        designer.key(UiKey.ENTER);

        assertEquals(expected.apply(before), designer.model().tower());
        assertNotEquals(before, designer.model().tower());
        designer.run(Command.SETTINGS);
        designer.press(button(TowerDesigner.DIALOG_RANDOMIZE), false);
        assertTrue(designer.towerDialog().isPresent(), "Settings has no Randomize Colors: nothing happens");
    }
}
