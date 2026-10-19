package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Facing;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b: ToB 2009's Toolbox as pure functions (scroll, rotate, copy, swap, clear). */
class EditorToolboxTest {

    private static final Cell FLOOR = new Cell(BaseType.FLOOR, null);

    private static TowerGrid grid() {
        return TowerGrid.builder().set(0, 0, 0, new Cell(BaseType.FLOOR, ObjectType.FZN)).set(15, 3, 0, FLOOR)
                .set(2, 2, 1, new Cell(BaseType.BOX, null)).build();
    }

    @Test
    void scrollingMovesEveryCellOneStepAndWrapsTheEdge() {
        TowerGrid east = EditorToolbox.scroll(grid(), EditorToolbox.Direction.EAST, OptionalInt.empty());

        assertEquals(ObjectType.FZN, east.cell(1, 0, 0).object());
        assertEquals(FLOOR, east.cell(0, 3, 0), "column 15 wraps to column 0");
        assertEquals(BaseType.BOX, east.cell(3, 2, 1).base());

        TowerGrid north = EditorToolbox.scroll(grid(), EditorToolbox.Direction.NORTH, OptionalInt.of(1));
        assertEquals(BaseType.BOX, north.cell(2, 1, 1).base());
        assertEquals(ObjectType.FZN, north.cell(0, 0, 0).object(), "only level 1 moved");
    }

    @Test
    void rotatingTurnsTheCellsAndTheObjects() {
        TowerGrid clockwise = EditorToolbox.rotate(grid(), true, OptionalInt.empty());

        assertEquals(ObjectType.FZE, clockwise.cell(15, 0, 0).object(), "north-west corner to north-east, N to E");
        assertEquals(FLOOR, clockwise.cell(12, 15, 0));
        assertEquals(grid(), EditorToolbox.rotate(clockwise, false, OptionalInt.empty()), "and back");
        TowerGrid full = grid();
        for (int i = 0; i < 4; i++) {
            full = EditorToolbox.rotate(full, true, OptionalInt.empty());
        }
        assertEquals(grid(), full, "four quarter turns");
    }

    @Test
    void everyObjectTurnsWithTheTower() {
        for (ObjectType object : ObjectType.values()) {
            ObjectType turned = object;
            for (int i = 0; i < 4; i++) {
                turned = EditorToolbox.turn(turned, true);
            }
            assertEquals(object, turned, object.toString());
            assertEquals(object.family(), EditorToolbox.turn(object, true).family());
            assertEquals(object, EditorToolbox.turn(EditorToolbox.turn(object, true), false));
        }
        assertEquals(Facing.EAST_WEST, EditorToolbox.turn(ObjectType.WON, true).facing());
        assertEquals(ObjectType.PSE, EditorToolbox.turn(ObjectType.PNE, true));
    }

    @Test
    void levelsCanBeCopiedSwappedAndCleared() {
        TowerGrid copied = EditorToolbox.copyLevel(grid(), 1, 4);
        assertEquals(BaseType.BOX, copied.cell(2, 2, 4).base());
        assertEquals(BaseType.BOX, copied.cell(2, 2, 1).base());

        TowerGrid swapped = EditorToolbox.swapLevels(grid(), 0, 1);
        assertEquals(BaseType.BOX, swapped.cell(2, 2, 0).base());
        assertEquals(ObjectType.FZN, swapped.cell(0, 0, 1).object());

        assertEquals(Cell.EMPTY, EditorToolbox.clear(grid(), OptionalInt.of(0)).cell(0, 0, 0));
        assertEquals(BaseType.BOX, EditorToolbox.clear(grid(), OptionalInt.of(0)).cell(2, 2, 1).base());
        assertEquals(TowerGrid.empty(), EditorToolbox.clear(grid(), OptionalInt.empty()));
    }
}
