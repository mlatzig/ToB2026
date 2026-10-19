package de.latzig.game.tob.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TowerGridTest {

    @Test
    void emptyGridHasOnlyEmptyCells() {
        TowerGrid grid = TowerGrid.empty();

        assertEquals(Cell.EMPTY, grid.cell(0, 0, 0));
        assertEquals(Cell.EMPTY, grid.cell(15, 15, 5));
        assertEquals(0, grid.objectCount());
        assertTrue(grid.isLevelEmpty(0));
    }

    @Test
    void builderSetsCellsAndGridIsImmutable() {
        TowerGrid.Builder builder = TowerGrid.builder().set(3, 2, 1, new Cell(BaseType.FLOOR, ObjectType.KLD));
        TowerGrid grid = builder.build();
        builder.set(3, 2, 1, Cell.EMPTY);

        assertEquals(new Cell(BaseType.FLOOR, ObjectType.KLD), grid.cell(3, 2, 1));
        assertEquals(1, grid.objectCount());
        assertFalse(grid.isLevelEmpty(1));
        assertTrue(grid.isLevelEmpty(0));
    }

    @Test
    void rejectsCoordinatesOutsideTheGrid() {
        TowerGrid grid = TowerGrid.empty();

        assertThrows(IllegalArgumentException.class, () -> grid.cell(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.cell(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.cell(0, 0, 6));
    }

    @Test
    void equalityIsValueBased() {
        assertEquals(SampleTowers.grid(), SampleTowers.grid());
        assertEquals(SampleTowers.grid().hashCode(), SampleTowers.grid().hashCode());
        assertNotEquals(TowerGrid.empty(), SampleTowers.grid());
        assertEquals(SampleTowers.grid(), SampleTowers.grid().toBuilder().build());
    }

    @Test
    void cellRequiresBase() {
        assertThrows(NullPointerException.class, () -> new Cell(null, null));
        assertFalse(Cell.EMPTY.hasObject());
        assertTrue(new Cell(BaseType.FLOOR, ObjectType.ZAP).hasObject());
    }
}
