package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b spec decision 7: undo and redo, 100 steps, a painting stroke is one step. */
class EditHistoryTest {

    private static final TowerDefinition EMPTY = EditorTowers.blank("new-000", 0);

    private static TowerDefinition floor(TowerDefinition tower, int x) {
        return EditorTowers.withGrid(tower, TowerEdits.place(tower.grid(), EditorElement.FLOOR, x, 0, 0).grid());
    }

    @Test
    void undoAndRedoStepThroughTheChanges() {
        EditHistory history = new EditHistory(EMPTY);
        TowerDefinition one = floor(EMPTY, 0);
        TowerDefinition two = floor(one, 1);
        history.apply(one);
        history.apply(two);

        assertEquals(one, history.undo());
        assertEquals(EMPTY, history.undo());
        assertFalse(history.canUndo());
        assertEquals(EMPTY, history.undo(), "nothing left to undo");
        assertEquals(one, history.redo());
        assertEquals(two, history.redo());
        assertFalse(history.canRedo());
    }

    @Test
    void aStrokeIsOneStepAndAnUnchangedTowerNone() {
        EditHistory history = new EditHistory(EMPTY);
        history.beginStroke();
        TowerDefinition tower = EMPTY;
        for (int x = 0; x < 8; x++) {
            tower = floor(tower, x);
            assertTrue(history.apply(tower));
        }
        assertFalse(history.apply(tower), "painting the same cell again changes nothing");
        history.endStroke();

        assertEquals(1, history.undoSteps());
        assertEquals(EMPTY, history.undo());
        assertEquals(tower, history.redo());
    }

    @Test
    void aStrokeWithoutChangeLeavesNoStep() {
        EditHistory history = new EditHistory(EMPTY);
        history.beginStroke();
        history.apply(EMPTY);
        history.endStroke();

        assertFalse(history.canUndo());
    }

    @Test
    void aNewChangeAfterAnUndoDropsTheRedoSteps() {
        EditHistory history = new EditHistory(EMPTY);
        history.apply(floor(EMPTY, 0));
        history.undo();
        history.apply(floor(EMPTY, 5));

        assertFalse(history.canRedo());
        assertEquals(EMPTY, history.undo());
    }

    @Test
    void atMostOneHundredStepsAreKeptTheOldestDroppedFirst() {
        EditHistory history = new EditHistory(EMPTY);
        TowerDefinition tower = EMPTY;
        TowerDefinition afterFirst = null;
        for (int i = 0; i < EditHistory.DEPTH + 5; i++) {
            tower = EditorTowers.withGrid(tower, TowerEdits.place(tower.grid(), EditorElement.FLOOR, i % 16,
                    i / 16, 0).grid());
            history.apply(tower);
            if (i == 4) {
                afterFirst = tower;
            }
        }

        assertEquals(EditHistory.DEPTH, history.undoSteps());
        TowerDefinition oldest = tower;
        while (history.canUndo()) {
            oldest = history.undo();
        }
        assertEquals(afterFirst, oldest, "the first five steps were dropped");
        assertEquals(TowerGrid.empty(), EMPTY.grid());
    }
}
