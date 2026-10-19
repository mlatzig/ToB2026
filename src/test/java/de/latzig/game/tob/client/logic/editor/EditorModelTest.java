package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.client.logic.ui.Drawing;
import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.sim.Rules;

/** Phase 6b: editing a library's towers — painting, undo, the list, the clipboard, read-only, the test gate. */
public class EditorModelTest {

    private static final Texts TEXTS = Texts.english();

    /** A tower that can be played: floor 4..7 of row 7, the grabber and the one klondike to collect. */
    public static TowerDefinition playable(String id) {
        TowerGrid grid = TowerGrid.empty();
        for (int x = 4; x <= 7; x++) {
            grid = TowerEdits.place(grid, EditorElement.FLOOR, x, 7, 0).grid();
        }
        grid = TowerEdits.place(grid, EditorElement.of(ObjectType.GRB), 4, 7, 0).grid();
        grid = TowerEdits.place(grid, EditorElement.of(ObjectType.KLD), 7, 7, 0).grid();
        return EditorTowers.withGrid(EditorTowers.blank(id, 0), grid);
    }

    private static EditorModel shipped() {
        EditorModel model = new EditorModel(TEXTS);
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("original", true),
                List.of(playable("original-000"), playable("original-001")), 0));
        return model;
    }

    @Test
    void aNewLibraryHasOneEmptyUnsavedTowerAndARowForTheNext() {
        EditorModel model = new EditorModel(TEXTS);

        assertEquals(Optional.empty(), model.library());
        assertEquals("(NOT SAVED)", model.libraryLabel());
        assertEquals("001: (NO TITLE)", model.title());
        assertEquals(List.of(new EditorModel.Row("  1: ", Drawing.WHITE, true),
                new EditorModel.Row("  2: ", Drawing.GREY, false)), model.rows());
        assertEquals("No spiders placed", model.status());
        assertEquals(EditorFindings.YELLOW, model.statusColour());
        assertEquals("X: 1,Y: 1", model.coordinates());
        assertFalse(model.dirty());
    }

    @Test
    void aPaintingStrokeIsOneUndoStepAndTheRightButtonRemoves() {
        EditorModel model = new EditorModel(TEXTS);
        model.press(0, 0, false);
        model.drag(1, 0);
        model.drag(2, 0);
        model.release();
        TowerGrid painted = model.tower().grid();

        assertEquals(BaseType.FLOOR, painted.cell(2, 0, 0).base());
        assertTrue(model.dirty());
        model.undo();
        assertEquals(TowerGrid.empty(), model.tower().grid(), "the whole stroke");
        model.redo();
        assertEquals(painted, model.tower().grid());

        model.drag(5, 5);
        assertEquals(Cell.EMPTY, model.tower().grid().cell(5, 5, 0), "moving without a button only moves the cursor");
        assertEquals(5, model.x());
        model.press(1, 0, true);
        assertEquals(Cell.EMPTY, model.tower().grid().cell(1, 0, 0));
        model.undo();
        assertEquals(painted, model.tower().grid());
    }

    @Test
    void undoDuringAStrokeEndsItInsteadOfSplittingIt() {
        // Final review M4: Ctrl+Z (or Ctrl+Y) while the button is held ends the running stroke first, so it is
        // undone as the one step it was meant to be, instead of leaving `painting` on and splitting it cell by cell.
        EditorModel model = new EditorModel(TEXTS);
        model.press(0, 0, false);
        model.drag(1, 0);
        model.drag(2, 0);

        model.undo();

        assertEquals(TowerGrid.empty(), model.tower().grid(), "the whole stroke so far undone as one step");
        assertFalse(model.canUndo(), "nothing left: it was exactly one step");
        model.drag(3, 0);
        assertEquals(Cell.EMPTY, model.tower().grid().cell(3, 0, 0), "the stroke ended: dragging on places nothing");
    }

    @Test
    void theKeyboardPlacesAndRemovesAtTheCursorOnTheShownLevel() {
        EditorModel model = new EditorModel(TEXTS);
        model.tool(EditorElement.BOX);
        model.level(2);
        model.moveCursor(3, 4);
        model.placeAtCursor();

        assertEquals(BaseType.BOX, model.tower().grid().cell(3, 4, 2).base());
        assertEquals(BaseType.FLOOR, model.tower().grid().cell(3, 4, 3).base());
        model.level(3);
        model.removeAtCursor();
        assertEquals("Supported block, can't remove", model.status());
        assertEquals(EditorFindings.RED, model.statusColour());
        model.level(99);
        assertEquals(TowerGrid.LEVELS - 1, model.level());
    }

    @Test
    void anotherTowerHasItsOwnHistoryAndTheEmptyRowAddsOne() {
        EditorModel model = new EditorModel(TEXTS);
        model.press(0, 0, false);
        model.release();
        model.select(1);

        assertEquals(2, model.towers().size(), "the empty row appended a tower");
        assertEquals(1, model.selected());
        assertFalse(model.canUndo(), "a new history");
        model.select(0);
        assertFalse(model.canUndo(), "undo is cleared when another tower is opened");
        assertEquals(BaseType.FLOOR, model.tower().grid().cell(0, 0, 0).base());
    }

    @Test
    void aShippedLibraryIsReadOnlyButItsTowersCanBeCopied() {
        EditorModel model = shipped();
        TowerGrid before = model.tower().grid();

        model.press(0, 0, false);
        model.apply(grid -> EditorToolbox.clear(grid, OptionalInt.empty()));
        model.delete();

        assertEquals(before, model.tower().grid());
        assertEquals("Read-only library: copy the tower into your own", model.status());
        assertEquals("original (READ-ONLY)", model.libraryLabel());
        assertEquals(2, model.rows().size(), "no row for a new tower");
        assertFalse(model.canInsert());
        assertFalse(model.dirty());
        model.copy();
        assertFalse(model.canPaste(), "not into a read-only library");

        model.newLibrary();
        model.paste();
        assertEquals(before, model.tower().grid());
        assertFalse(model.tower().id().startsWith("original-"), "Phase 6d decision 7: a pasted copy is a new tower");
        assertEquals(0, model.selected(), "the tower keeps its place");
        model.undo();
        assertEquals(TowerGrid.empty(), model.tower().grid());
    }

    @Test
    void insertRemoveAndDeleteWorkOnTheList() {
        EditorModel model = new EditorModel(TEXTS);
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("mine", false),
                List.of(playable("mine-000"), playable("mine-001")), 0));
        model.select(1);

        model.insert();
        assertEquals(3, model.towers().size());
        assertEquals(1, model.selected());
        assertEquals(TowerGrid.empty(), model.tower().grid());
        model.remove();
        assertEquals(2, model.towers().size());
        assertEquals(playable("mine-001").grid(), model.tower().grid());
        model.delete();
        assertEquals(TowerGrid.empty(), model.tower().grid());
        model.undo();
        assertEquals(playable("mine-001").grid(), model.tower().grid(), "Delete is one undo step");
        model.saved("mine");
        assertFalse(model.dirty());
        assertEquals("Library saved", model.status());
    }

    @Test
    void theTestPlayIsRefusedOnAnErrorAskedWhenItCannotBeWonAndGoesOtherwise() {
        EditorModel model = new EditorModel(TEXTS);
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("mine", false), List.of(playable("mine-000")),
                0));
        assertEquals(EditorModel.Play.GO, model.play());

        model.tool(EditorElement.of(ObjectType.BFL));
        model.level(3);
        model.press(9, 9, false);
        model.release();
        model.level(0);
        model.cursor(0, 0);
        assertEquals(EditorModel.Play.REFUSED, model.play());
        assertEquals(List.of(3, 9, 9), List.of(model.level(), model.x(), model.y()), "the cursor jumps to the error");
        assertEquals("No floor for object: you can't play this tower!", model.status());

        model.undo();
        model.level(0);
        model.press(7, 7, true);
        assertEquals(EditorModel.Play.ASK, model.play(), "no klondike left to collect");
    }

    @Test
    void aNoticeRunsOutAndATestPlayReportsItsResult() {
        EditorModel model = new EditorModel(TEXTS);
        model.testEnded(Rules.EXIT_COMPLETED);
        assertEquals("Test play: Tower Completed", model.status());
        assertEquals(Drawing.WHITE, model.statusColour());

        model.update(EditorModel.NOTICE_SECONDS);
        assertEquals("No spiders placed", model.status());
        model.testEnded(Rules.EXIT_ABANDONED);
        assertEquals("Test play: Attempt Abandoned", model.status());
        assertFalse(model.freezeEnemies());
        model.toggleFreezeEnemies();
        assertTrue(model.freezeEnemies());
    }

    @Test
    void theListShowsToB2009sColoursAndScrollsToTheSelection() {
        EditorModel model = new EditorModel(TEXTS);
        List<TowerDefinition> towers = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            towers.add(i == 0 ? playable("mine-000") : EditorTowers.blank("mine-" + i, i));
        }
        model.load(new TowerLibraries.Opened(new TowerLibraries.Entry("mine", false), towers, 2));

        assertEquals("2 unreadable towers skipped", model.status());
        assertEquals(EditorModel.GREEN, model.rows().get(0).colour());
        assertEquals(Drawing.GREY, model.rows().get(1).colour());
        model.select(29);
        assertEquals(31, model.list().size());
        assertTrue(model.list().first() > 0, "the selection is scrolled into view");
        assertEquals(" 30: ", model.rows().get(29).text());
    }
}
