package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b: a new tower's interim defaults (until 6b-2's dialogs) and an own library's ids. */
class EditorTowersTest {

    @Test
    void aNewTowerIsEmptyWithOneKlondikeToCollectAndTheTimeLimitOff() {
        TowerDefinition tower = EditorTowers.blank("new-010", 10);

        assertEquals("new-010", tower.id());
        assertEquals(1, tower.group());
        assertEquals(1, tower.index(), "position 10: group 1, index 1 (groups of 9)");
        assertEquals("", tower.title());
        assertEquals(new Conditions(600, false, 1, 0), tower.conditions());
        assertTrue(tower.behaviour().camerasEnabled());
        assertTrue(tower.remake().isPresent(), "original-000's look");
        assertEquals(TowerGrid.empty(), tower.grid());
    }

    /** Phase 6d decision 7: placing a tower in its library keeps its id; only a copy gets another one. */
    @Test
    void placingKeepsTheIdAndWithIdChangesNothingElse() {
        TowerDefinition tower = EditorTowers.blank("new-000", 0);
        TowerGrid grid = TowerEdits.place(TowerGrid.empty(), EditorElement.FLOOR, 1, 2, 3).grid();

        TowerDefinition moved = EditorTowers.placed(EditorTowers.withGrid(tower, grid), 12);
        TowerDefinition renamed = EditorTowers.withId(moved, "copy");

        assertEquals("new-000", moved.id());
        assertEquals(1, moved.group());
        assertEquals(3, moved.index());
        assertEquals(grid, moved.grid());
        assertEquals(tower.conditions(), moved.conditions());
        assertEquals("copy", renamed.id());
        assertEquals(EditorTowers.withId(renamed, "new-000"), moved);
    }
}
