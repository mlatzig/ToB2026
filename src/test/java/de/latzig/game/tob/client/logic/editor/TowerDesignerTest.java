package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Command;
import de.latzig.game.tob.client.logic.editor.TowerDesigner.Menu;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Hit;
import de.latzig.game.tob.client.logic.frontend.GameFlow.Widget;
import de.latzig.game.tob.client.logic.ui.UiKey;
import de.latzig.game.tob.core.io.TowerLibraryWriter;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLibrary;

/** Phase 6b: the designer's menus, dialogs, questions and requests to the flow. */
class TowerDesignerTest {

    private static final Texts TEXTS = Texts.english();

    private static TowerDesigner designer(Path dir) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        return new TowerDesigner(new TowerLibraries(shipped, dir.resolve("user")), TEXTS);
    }

    private static Optional<Hit> hit(Widget widget, int row) {
        return Optional.of(new Hit(widget, row));
    }

    private static Optional<Hit> cell(int x, int y) {
        return hit(Widget.EDITOR_CELL, y * TowerGrid.WIDTH + x);
    }

    @Test
    void theMenusHoldToB2009sEntriesPlusUndoRedoAndFreezeEnemies(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);

        assertEquals(List.of(Command.NEW, Command.OPEN, Command.SAVE_AS, Command.SAVE, Command.EXIT),
                Command.of(Menu.FILE));
        assertEquals(List.of(Command.UNDO, Command.REDO, Command.COPY, Command.PASTE, Command.INSERT,
                Command.REMOVE, Command.DELETE), Command.of(Menu.EDIT));
        assertEquals(List.of(Command.SETTINGS, Command.EFFECTS, Command.PLAY, Command.FREEZE),
                Command.of(Menu.OPTIONS));
        assertEquals(6, Command.of(Menu.TOOLBOX).size());
        assertTrue(designer.enabled(Command.SETTINGS), "Phase 6b-2");
        assertTrue(designer.enabled(Command.EFFECTS), "Phase 6b-2");
        assertFalse(designer.enabled(Command.SAVE), "a new library is saved with Save As");
        assertFalse(designer.enabled(Command.UNDO));
        for (Command command : Command.values()) {
            assertFalse(designer.label(command).isBlank(), command.toString());
        }
        assertEquals("Freeze Enemies: Off", designer.label(Command.FREEZE));
        designer.run(Command.FREEZE);
        assertEquals("Freeze Enemies: On", designer.label(Command.FREEZE));
    }

    @Test
    void aMenuOpensFollowsTheMouseAlongTheBarAndRunsAnEntry(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.press(cell(3, 3), false);
        designer.release();

        designer.press(hit(Widget.EDITOR_MENU, Menu.EDIT.ordinal()), false);
        assertEquals(Optional.of(Menu.EDIT), designer.menu());
        designer.pointer(hit(Widget.EDITOR_MENU, Menu.TOOLBOX.ordinal()), false);
        assertEquals(Optional.of(Menu.TOOLBOX), designer.menu());
        designer.pointer(hit(Widget.EDITOR_ITEM, Command.CLEAR_ALL.ordinal()), false);
        assertEquals(Optional.of(Command.CLEAR_ALL), designer.hovered());

        TowerDesigner.Request request = designer.press(hit(Widget.EDITOR_ITEM, Command.CLEAR_ALL.ordinal()), false);
        assertEquals(new TowerDesigner.Confirm("CLEAR ALL"), request);
        assertEquals(Optional.empty(), designer.menu());
        designer.answer(true);
        assertEquals(TowerGrid.empty(), designer.model().tower().grid());

        designer.press(hit(Widget.EDITOR_MENU, Menu.FILE.ordinal()), false);
        designer.press(cell(0, 0), false);
        assertEquals(Optional.empty(), designer.menu(), "a click elsewhere closes the menu");
        assertEquals(TowerGrid.empty(), designer.model().tower().grid(), "and does nothing else");
    }

    @Test
    void theMouseAndTheKeysEditTheGrid(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.pointer(cell(4, 5), false);
        assertEquals(List.of(4, 5), List.of(designer.model().x(), designer.model().y()), "the cursor follows");
        designer.press(cell(4, 5), false);
        designer.pointer(cell(5, 5), true);
        designer.release();
        designer.press(hit(Widget.EDITOR_PALETTE, 4), false);
        assertEquals(EditorElement.of(ObjectType.ZAP), designer.model().tool());
        designer.key(UiKey.SPACE);

        TowerGrid grid = designer.model().tower().grid();
        assertEquals(BaseType.FLOOR, grid.cell(4, 5, 0).base());
        assertEquals(ObjectType.ZAP, grid.cell(5, 5, 0).object());
        designer.key(UiKey.UNDO);
        designer.key(UiKey.UNDO);
        assertEquals(TowerGrid.empty(), designer.model().tower().grid());
        designer.key(UiKey.REDO);
        designer.key(UiKey.PAGE_UP);
        designer.key(UiKey.LEFT);
        designer.key(UiKey.SPACE);
        assertEquals(ObjectType.ZAP, designer.model().tower().grid().cell(4, 5, 1).object(), "level 2, one left");
        designer.press(hit(Widget.EDITOR_LEVEL, 0), false);
        designer.press(cell(4, 5), true);
        assertEquals(BaseType.EMPTY, designer.model().tower().grid().cell(4, 5, 0).base());
    }

    @Test
    void saveAsWritesAnOwnLibraryAndSaveWritesItAgain(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.press(cell(2, 2), false);
        designer.release();

        designer.run(Command.SAVE_AS);
        assertEquals(LibraryDialog.Mode.SAVE_AS, designer.libraryDialog().orElseThrow().mode());
        for (char c : "My Lib!".toCharArray()) {
            designer.typed(c);
        }
        assertEquals("mylib", designer.libraryDialog().orElseThrow().name(), "letters, digits and _ only");
        designer.key(UiKey.ENTER);

        Path file = dir.resolve("user").resolve("towers").resolve("mylib.json");
        assertTrue(Files.isRegularFile(file));
        assertEquals(Optional.of("mylib"), designer.model().library());
        assertFalse(designer.model().dirty());
        designer.press(cell(3, 2), false);
        designer.release();
        assertTrue(designer.enabled(Command.SAVE));
        assertEquals(TowerDesigner.NONE, designer.run(Command.SAVE));
        assertTrue(Files.readString(file).contains("\"id\" : \"" + designer.model().tower().id() + "\""),
                "Phase 6d decision 7: the tower's own id, not renumbered");
        assertFalse(designer.model().dirty());
    }

    /**
     * Fix wave (Task 1 Minor): a Save As of a shipped library that cannot be written leaves the library as it was -
     * still read-only, its towers with their shipped ids (the new ids are adopted only after a successful write).
     */
    @Test
    void aFailedSaveAsOfAShippedLibraryLeavesItReadOnlyWithItsIds(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        Files.createDirectories(dir.resolve("user"));
        Files.writeString(dir.resolve("user").resolve("towers"), "a file where the folder belongs");
        designer.run(Command.OPEN);
        designer.press(hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK), false);
        assertTrue(designer.model().readOnly());

        designer.run(Command.SAVE_AS);
        for (char c : "mine".toCharArray()) {
            designer.typed(c);
        }
        TowerDesigner.Request request = designer.key(UiKey.ENTER);

        assertInstanceOf(TowerDesigner.Message.class, request, "the write failed");
        assertTrue(designer.model().readOnly(), "still the shipped library");
        assertEquals(Optional.of("original"), designer.model().library());
        assertEquals(List.of("original-000"), designer.model().towers().stream().map(TowerDefinition::id).toList(),
                "no id changed by the failed save");
    }

    /** Final review M2: Save must not silently drop towers the designer could not read on Open. */
    @Test
    void saveAsksBeforeDroppingTowersItCouldNotReadOnOpen(@TempDir Path dir) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        TowerLibraries libraries = new TowerLibraries(shipped, dir.resolve("user"));
        libraries.save("mine", List.of(EditorModelTest.playable("mine-000"), EditorModelTest.playable("mine-001")));
        Path file = libraries.folder().resolve("mine.json");
        Files.writeString(file, Files.readString(file).replaceFirst("\"title\" : \"\"", "\"title\" : 7"));

        TowerDesigner designer = new TowerDesigner(libraries, TEXTS);
        designer.run(Command.OPEN);
        LibraryDialog dialog = designer.libraryDialog().orElseThrow();
        dialog.select(dialog.entries().indexOf(new TowerLibraries.Entry("mine", false)));
        designer.key(UiKey.ENTER);
        assertTrue(designer.model().hadDroppedTowers(), "one tower could not be read");

        assertEquals(new TowerDesigner.Confirm("SOME TOWERS COULD NOT BE READ - OVERWRITE?"),
                designer.run(Command.SAVE));
        assertTrue(designer.model().hadDroppedTowers(), "not yet written: NO would leave the file alone");

        designer.answer(true);
        assertFalse(designer.model().hadDroppedTowers());
        assertEquals(1, libraries.open(new TowerLibraries.Entry("mine", false)).towers().size(),
                "the unreadable tower is gone for good after the confirmed save");
    }

    /**
     * Re-review R1: Save As under the open library's own name (pre-filled, just Enter) overwrites the same file as
     * Save would, so it must ask the same M2 question first rather than silently dropping the unreadable tower.
     */
    @Test
    void saveAsUnderItsOwnNameAsksBeforeDroppingTowersItCouldNotReadOnOpen(@TempDir Path dir) throws IOException {
        Path shipped = Files.createDirectories(dir.resolve("assets-towers"));
        new TowerLibraryWriter().write(new TowerLibrary("original", "Original", "test",
                List.of(EditorModelTest.playable("original-000"))), shipped.resolve("original.json"));
        TowerLibraries libraries = new TowerLibraries(shipped, dir.resolve("user"));
        libraries.save("mine", List.of(EditorModelTest.playable("mine-000"), EditorModelTest.playable("mine-001")));
        Path file = libraries.folder().resolve("mine.json");
        Files.writeString(file, Files.readString(file).replaceFirst("\"title\" : \"\"", "\"title\" : 7"));

        TowerDesigner designer = new TowerDesigner(libraries, TEXTS);
        designer.run(Command.OPEN);
        LibraryDialog dialog = designer.libraryDialog().orElseThrow();
        dialog.select(dialog.entries().indexOf(new TowerLibraries.Entry("mine", false)));
        designer.key(UiKey.ENTER);
        assertTrue(designer.model().hadDroppedTowers(), "one tower could not be read");

        designer.run(Command.SAVE_AS);
        assertEquals("mine", designer.libraryDialog().orElseThrow().name(), "pre-filled with the open library's name");
        TowerDesigner.Request request = designer.key(UiKey.ENTER);
        assertEquals(new TowerDesigner.Confirm("SOME TOWERS COULD NOT BE READ - OVERWRITE?"), request);
        assertTrue(designer.model().hadDroppedTowers(), "not yet written: NO would leave the file alone");

        designer.answer(true);
        assertFalse(designer.model().hadDroppedTowers());
        assertEquals(1, libraries.open(new TowerLibraries.Entry("mine", false)).towers().size(),
                "the unreadable tower is gone for good after the confirmed Save As");
    }

    @Test
    void saveAsRefusesAnInvalidNameAndAsksBeforeOverwriting(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.run(Command.SAVE_AS);
        designer.typed('x');
        designer.key(UiKey.BACKSPACE);
        designer.key(UiKey.ENTER);
        assertEquals("Name: a-z, 0-9 and _ only", designer.libraryDialog().orElseThrow().error());
        for (char c : "original".toCharArray()) {
            designer.typed(c);
        }
        designer.key(UiKey.ENTER);
        assertTrue(designer.libraryDialog().isPresent(), "a shipped library's name is refused");
        // Final review M6: a reserved name (a shipped library's) gets its own text, not the character-set message.
        assertEquals("Name reserved - choose another", designer.libraryDialog().orElseThrow().error());
        for (int i = 0; i < "original".length(); i++) {
            designer.key(UiKey.BACKSPACE);
        }
        for (char c : "con".toCharArray()) {
            designer.typed(c);
        }
        designer.key(UiKey.ENTER);
        // Final review M6 (controller verified on Windows 11): a Windows device name is a valid, unreserved name -
        // "con.json" is an ordinary file (the restriction applies only to the bare name without an extension).
        assertTrue(designer.libraryDialog().isEmpty(), "\"con\" is accepted");
        assertEquals(Optional.of("con"), designer.model().library());
        assertTrue(Files.isRegularFile(dir.resolve("user").resolve("towers").resolve("con.json")));

        new TowerLibraries(dir.resolve("assets-towers"), dir.resolve("user")).save("other",
                List.of(EditorTowers.blank("x-000", 0)));
        designer.run(Command.SAVE_AS);
        List<TowerLibraries.Entry> entries = designer.libraryDialog().orElseThrow().entries();
        designer.libraryDialog().orElseThrow().select(entries.indexOf(new TowerLibraries.Entry("other", false)));
        assertEquals(new TowerDesigner.Confirm("OVERWRITE LIBRARY"), designer.key(UiKey.ENTER));
        designer.answer(true);
        assertEquals(Optional.of("other"), designer.model().library());
    }

    @Test
    void openingAnotherLibraryAsksFirstWhenChangesAreNotSaved(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.press(cell(1, 1), false);
        designer.release();

        designer.run(Command.OPEN);
        LibraryDialog dialog = designer.libraryDialog().orElseThrow();
        assertEquals(List.of(new TowerLibraries.Entry("original", true)), dialog.entries());
        TowerDesigner.Request request = designer.press(hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK), false);
        assertEquals(new TowerDesigner.Confirm("UNSAVED CHANGES"), request);
        assertEquals(TowerDesigner.NONE, designer.answer(true));

        assertEquals(Optional.of("original"), designer.model().library());
        assertTrue(designer.model().readOnly());
        assertFalse(designer.enabled(Command.SAVE));
        assertTrue(designer.enabled(Command.SAVE_AS), "a shipped library can be saved as an own copy");
        assertFalse(designer.enabled(Command.CLEAR_ALL));
    }

    @Test
    void exitAsksOnlyWhenChangesAreNotSaved(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        assertEquals(new TowerDesigner.Exit(), designer.key(UiKey.ESCAPE));

        designer.press(cell(1, 1), false);
        designer.release();
        assertEquals(new TowerDesigner.Confirm("UNSAVED CHANGES"), designer.key(UiKey.ESCAPE));
        assertEquals(TowerDesigner.NONE, designer.answer(false));
        designer.run(Command.EXIT);
        assertEquals(new TowerDesigner.Exit(), designer.answer(true));

        designer.press(hit(Widget.EDITOR_MENU, 0), false);
        assertEquals(TowerDesigner.NONE, designer.key(UiKey.ESCAPE), "Esc closes the menu first");
        assertEquals(Optional.empty(), designer.menu());
    }

    @Test
    void playTowerStartsATestPlayAsksFirstOrRefuses(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.run(Command.OPEN);
        designer.key(UiKey.ENTER);
        designer.run(Command.FREEZE);

        TowerDefinition shown = designer.model().tower();
        assertEquals(new TowerDesigner.Test(shown, 1, true), designer.run(Command.PLAY), "a valid tower plays");

        designer.run(Command.NEW);
        assertEquals(new TowerDesigner.Confirm("PLAY ANYWAY"), designer.run(Command.PLAY), "no spider: can't be won");
        assertEquals(new TowerDesigner.Test(designer.model().tower(), 1, true), designer.answer(true));

        designer.press(hit(Widget.EDITOR_PALETTE, 7), false);
        designer.press(cell(9, 9), false);
        designer.release();
        assertEquals(new TowerDesigner.Message("No floor for object: you can't play this tower!"),
                designer.run(Command.PLAY));
    }

    /** Final review M7: the right button only removes on the grid, unlike the left one (ToB 2009). */
    @Test
    void aRightClickActsOnlyOnTheGridNeverOnAMenuADialogOrThePalette(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.press(cell(2, 2), false);
        designer.release();
        assertEquals(BaseType.FLOOR, designer.model().tower().grid().cell(2, 2, 0).base());

        designer.press(hit(Widget.EDITOR_MENU, Menu.TOOLBOX.ordinal()), false);
        TowerDesigner.Request request = designer.press(hit(Widget.EDITOR_ITEM, Command.CLEAR_ALL.ordinal()), true);
        assertEquals(TowerDesigner.NONE, request, "a right click on a menu entry does nothing");
        assertEquals(Optional.of(Menu.TOOLBOX), designer.menu(), "the menu stays open, nothing ran");
        assertEquals(BaseType.FLOOR, designer.model().tower().grid().cell(2, 2, 0).base(), "Clear All did not run");
        designer.key(UiKey.ESCAPE);

        designer.press(hit(Widget.EDITOR_PALETTE, 4), true);
        assertEquals(EditorElement.FLOOR, designer.model().tool(), "a right click does not select a palette entry");

        designer.run(Command.SCROLL);
        assertEquals(TowerDesigner.NONE, designer.press(hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK), true));
        assertTrue(designer.toolboxDialog().isPresent(), "a right click does not confirm a Toolbox dialog's OK");
        designer.key(UiKey.ESCAPE);

        designer.press(cell(2, 2), true);
        assertEquals(BaseType.EMPTY, designer.model().tower().grid().cell(2, 2, 0).base(), "the grid still removes");
    }

    @Test
    void theToolboxDialogsScrollRotateCopyAndSwap(@TempDir Path dir) throws IOException {
        TowerDesigner designer = designer(dir);
        designer.press(cell(0, 0), false);
        designer.release();

        designer.run(Command.SCROLL);
        designer.press(hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_CHOICE + 1), false);
        designer.press(hit(Widget.EDITOR_DIALOG, TowerDesigner.DIALOG_OK), false);
        assertEquals(BaseType.FLOOR, designer.model().tower().grid().cell(1, 0, 0).base(), "scrolled east");
        assertTrue(designer.toolboxDialog().isEmpty());

        designer.run(Command.COPY_LEVEL);
        assertEquals(0, designer.toolboxDialog().orElseThrow().choice(), "ToB 2009: the current level");
        designer.key(UiKey.ENTER);
        assertTrue(designer.toolboxDialog().isPresent(), "a level onto itself is no copy");
        designer.key(UiKey.RIGHT);
        designer.key(UiKey.RIGHT);
        designer.key(UiKey.ENTER);
        assertEquals(BaseType.FLOOR, designer.model().tower().grid().cell(1, 0, 2).base());

        designer.run(Command.ROTATE);
        designer.key(UiKey.SPACE);
        assertTrue(designer.toolboxDialog().orElseThrow().onlyLevel());
        designer.key(UiKey.ESCAPE);
        assertTrue(designer.toolboxDialog().isEmpty());
        designer.key(UiKey.UNDO);
        assertEquals(BaseType.EMPTY, designer.model().tower().grid().cell(1, 0, 2).base(), "one step each");
    }
}
