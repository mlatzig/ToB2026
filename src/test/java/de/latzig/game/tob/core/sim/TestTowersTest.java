package de.latzig.game.tob.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

class TestTowersTest {

    @Test
    void oracleCoordinatesMapLikeTheConverter() {
        assertEquals(new CellPos(4, 11, 0), TestTowers.pos(0, 0, 0));
        assertEquals(new CellPos(11, 4, 3), TestTowers.pos(7, 7, 3));
    }

    @Test
    void rowsAreWrittenNorthFirstWithTheOracleLegend() {
        TowerDefinition tower = TestTowers.tower()
                .level(0, "F.", ".B")
                .level(1, "UuDd")
                .object(0, 1, 0, ObjectType.ZAP)
                .conditions(5, true, 1, 2)
                .build();
        TowerGrid grid = tower.grid();

        assertEquals(new Cell(BaseType.FLOOR, ObjectType.ZAP), grid.cell(4, 10, 0));
        assertEquals(new Cell(BaseType.BOX, null), grid.cell(5, 11, 0));
        assertEquals(Cell.EMPTY, grid.cell(4, 11, 0));
        assertEquals(Cell.EMPTY, grid.cell(5, 10, 0));
        assertEquals(BaseType.LIFT_UP_START, grid.cell(4, 11, 1).base());
        assertEquals(BaseType.LIFT_UP_STOP, grid.cell(5, 11, 1).base());
        assertEquals(BaseType.LIFT_DOWN_START, grid.cell(6, 11, 1).base());
        assertEquals(BaseType.LIFT_DOWN_STOP, grid.cell(7, 11, 1).base());
        assertEquals(new Conditions(5, true, 1, 2), tower.conditions());
        assertEquals("test-000", tower.id());
    }

    @Test
    void unknownCellCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TestTowers.tower().level(0, "FX"));
    }
}
