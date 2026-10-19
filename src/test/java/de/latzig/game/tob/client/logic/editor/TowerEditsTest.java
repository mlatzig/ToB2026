package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b spec decision 4: the placement rules of the original designer, always placing, follow-ups silent. */
class TowerEditsTest {

    private static TowerGrid place(TowerGrid grid, EditorElement element, int x, int y, int level) {
        return TowerEdits.place(grid, element, x, y, level).grid();
    }

    private static Cell cell(BaseType base, ObjectType object) {
        return new Cell(base, object);
    }

    @Test
    void floorKeepsTheObjectWhileABoxDropsItAndFloorsTheEmptyCellAbove() {
        TowerGrid grid = place(TowerGrid.empty(), EditorElement.of(ObjectType.KLD), 5, 5, 0);
        grid = place(grid, EditorElement.FLOOR, 5, 5, 0);
        assertEquals(cell(BaseType.FLOOR, ObjectType.KLD), grid.cell(5, 5, 0), "the original keeps the object");

        grid = place(grid, EditorElement.BOX, 5, 5, 0);
        assertEquals(cell(BaseType.BOX, null), grid.cell(5, 5, 0));
        assertEquals(cell(BaseType.FLOOR, null), grid.cell(5, 5, 1), "the support's floor above");

        TowerGrid top = place(TowerGrid.empty(), EditorElement.BOX, 1, 1, TowerGrid.LEVELS - 1);
        assertEquals(BaseType.BOX, top.cell(1, 1, TowerGrid.LEVELS - 1).base(), "a box on the top level stays");
    }

    @Test
    void anUpLiftPutsItsStopAboveAndADownLiftItsStopBelow() {
        TowerGrid grid = place(TowerGrid.empty(), EditorElement.of(ObjectType.BFL), 2, 3, 2);
        grid = place(grid, EditorElement.LIFT_UP, 2, 3, 1);
        assertEquals(cell(BaseType.LIFT_UP_START, null), grid.cell(2, 3, 1));
        assertEquals(cell(BaseType.LIFT_UP_STOP, null), grid.cell(2, 3, 2), "the stop's object goes");

        grid = place(grid, EditorElement.of(ObjectType.ZAP), 7, 7, 3);
        grid = place(grid, EditorElement.LIFT_DOWN, 7, 7, 3);
        assertEquals(cell(BaseType.LIFT_DOWN_START, ObjectType.ZAP), grid.cell(7, 7, 3), "the start keeps it");
        assertEquals(cell(BaseType.LIFT_DOWN_STOP, null), grid.cell(7, 7, 2));
    }

    @Test
    void aLiftThatLeavesTheGridStaysAloneForTheRulePassToMark() {
        TowerGrid up = place(TowerGrid.empty(), EditorElement.LIFT_UP, 0, 0, TowerGrid.LEVELS - 1);
        TowerGrid down = place(TowerGrid.empty(), EditorElement.LIFT_DOWN, 0, 0, 0);

        assertEquals(BaseType.LIFT_UP_START, up.cell(0, 0, TowerGrid.LEVELS - 1).base());
        assertEquals(BaseType.LIFT_DOWN_START, down.cell(0, 0, 0).base());
        assertEquals(1, cells(up), "nothing else");
        assertEquals(1, cells(down));
    }

    @Test
    void overwritingOrRemovingEitherCellOfALiftRemovesTheWholeLift() {
        TowerGrid lift = place(TowerGrid.empty(), EditorElement.LIFT_DOWN, 4, 4, 2);
        lift = place(lift, EditorElement.of(ObjectType.GRB), 4, 4, 2);

        TowerGrid floorOnStop = place(lift, EditorElement.FLOOR, 4, 4, 1);
        assertEquals(cell(BaseType.FLOOR, null), floorOnStop.cell(4, 4, 1));
        assertEquals(cell(BaseType.FLOOR, ObjectType.GRB), floorOnStop.cell(4, 4, 2), "the start becomes floor");

        TowerGrid boxOnStart = place(lift, EditorElement.BOX, 4, 4, 2);
        assertEquals(cell(BaseType.EMPTY, null), boxOnStart.cell(4, 4, 1), "the stop becomes empty");

        TowerGrid removedStop = TowerEdits.remove(lift, 4, 4, 1).grid();
        assertEquals(Cell.EMPTY, removedStop.cell(4, 4, 1));
        assertEquals(cell(BaseType.FLOOR, ObjectType.GRB), removedStop.cell(4, 4, 2));

        TowerGrid upOverUp = place(place(TowerGrid.empty(), EditorElement.LIFT_UP, 1, 1, 0), EditorElement.LIFT_UP,
                1, 1, 1);
        assertEquals(List.of(BaseType.FLOOR, BaseType.LIFT_UP_START, BaseType.LIFT_UP_STOP),
                List.of(upOverUp.cell(1, 1, 0).base(), upOverUp.cell(1, 1, 1).base(), upOverUp.cell(1, 1, 2).base()),
                "a lift placed on another's stop: the old start becomes floor");
    }

    @Test
    void objectsGoAnywhereAndASpiderPlacedAgainMoves() {
        TowerGrid grid = place(TowerGrid.empty(), EditorElement.of(ObjectType.PSH), 3, 3, 0);
        assertEquals(cell(BaseType.EMPTY, ObjectType.PSH), grid.cell(3, 3, 0), "placed, the rule pass marks it");

        grid = place(grid, EditorElement.of(ObjectType.PSH), 9, 9, 1);
        assertEquals(Cell.EMPTY, grid.cell(3, 3, 0));
        assertEquals(ObjectType.PSH, grid.cell(9, 9, 1).object());

        grid = place(grid, EditorElement.of(ObjectType.KLD), 2, 2, 0);
        grid = place(grid, EditorElement.of(ObjectType.KLD), 3, 2, 0);
        assertEquals(3, grid.objectCount(), "only spiders are unique");
    }

    @Test
    void theRightButtonTakesTheObjectFirstThenTheBaseButNotAFloorOnABox() {
        TowerGrid grid = place(place(TowerGrid.empty(), EditorElement.FLOOR, 6, 6, 0), EditorElement.of(
                ObjectType.WAT), 6, 6, 0);
        grid = TowerEdits.remove(grid, 6, 6, 0).grid();
        assertEquals(cell(BaseType.FLOOR, null), grid.cell(6, 6, 0));
        grid = TowerEdits.remove(grid, 6, 6, 0).grid();
        assertEquals(Cell.EMPTY, grid.cell(6, 6, 0));

        TowerGrid box = place(TowerGrid.empty(), EditorElement.BOX, 6, 6, 0);
        TowerEdits.Result refused = TowerEdits.remove(box, 6, 6, 1);
        assertSame(box, refused.grid());
        assertEquals(Optional.of(TowerEdits.SUPPORTED), refused.notice());
        assertEquals(Cell.EMPTY, TowerEdits.remove(box, 6, 6, 0).grid().cell(6, 6, 0), "the box itself goes");
    }

    @Test
    void aRepeatedPlacementReturnsTheSameGrid() {
        TowerGrid grid = place(TowerGrid.empty(), EditorElement.FLOOR, 0, 0, 0);

        assertSame(grid, place(grid, EditorElement.FLOOR, 0, 0, 0));
        assertSame(grid, TowerEdits.remove(grid, 1, 1, 0).grid(), "removing nothing");
        assertTrue(TowerEdits.place(grid, EditorElement.FLOOR, 0, 0, 0).notice().isEmpty());
    }

    private static int cells(TowerGrid grid) {
        int count = 0;
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    count += Cell.EMPTY.equals(grid.cell(x, y, level)) ? 0 : 1;
                }
            }
        }
        return count;
    }
}
