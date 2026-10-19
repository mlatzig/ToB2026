package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerGrid;

class GridRowCodecTest {

    @Test
    void encodesAndDecodesSingleCells() {
        assertEquals(".", GridRowCodec.encodeCell(Cell.EMPTY));
        assertEquals("FLR:KLD", GridRowCodec.encodeCell(new Cell(BaseType.FLOOR, ObjectType.KLD)));
        assertEquals("LDS:GRB", GridRowCodec.encodeCell(new Cell(BaseType.LIFT_DOWN_START, ObjectType.GRB)));
        assertEquals(".:GRB", GridRowCodec.encodeCell(new Cell(BaseType.EMPTY, ObjectType.GRB)));
        assertEquals("LUE", GridRowCodec.encodeCell(new Cell(BaseType.LIFT_UP_STOP, null)));

        assertEquals(new Cell(BaseType.BOX, null), GridRowCodec.decodeCell("BOX"));
        assertEquals(new Cell(BaseType.LIFT_DOWN_STOP, null), GridRowCodec.decodeCell("LDE"));
        assertEquals(new Cell(BaseType.FLOOR, ObjectType.FZN), GridRowCodec.decodeCell("FLR:FZN"));
        assertEquals(Cell.EMPTY, GridRowCodec.decodeCell("."));
    }

    @Test
    void encodesALevelAsSixteenRowsOfSixteenTokens() {
        List<String> rows = GridRowCodec.encodeLevel(SampleTowers.grid(), 0);

        assertEquals(16, rows.size());
        for (String row : rows) {
            assertEquals(16, row.strip().split("\\s+").length);
        }
        assertEquals("FLR:KLD", rows.get(4).strip().split("\\s+")[6]);
        assertEquals("FLR:ZAP", rows.get(11).strip().split("\\s+")[4]);
        assertTrue(rows.get(0).startsWith("."));
    }

    @Test
    void roundTripsAllLevels() {
        TowerGrid original = SampleTowers.grid();
        TowerGrid.Builder target = TowerGrid.builder();
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            GridRowCodec.decodeLevel(GridRowCodec.encodeLevel(original, level), level, target);
        }
        assertEquals(original, target.build());
    }

    @Test
    void reportsWrongRowCount() {
        List<String> rows = new ArrayList<>(GridRowCodec.encodeLevel(TowerGrid.empty(), 0));
        rows.remove(0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GridRowCodec.decodeLevel(rows, 2, TowerGrid.builder()));
        assertTrue(e.getMessage().contains("level 2"));
    }

    @Test
    void reportsWrongTokenCountAndUnknownTokens() {
        List<String> rows = new ArrayList<>(GridRowCodec.encodeLevel(TowerGrid.empty(), 0));
        rows.set(3, ". . .");
        IllegalArgumentException count = assertThrows(IllegalArgumentException.class,
                () -> GridRowCodec.decodeLevel(rows, 0, TowerGrid.builder()));
        assertTrue(count.getMessage().contains("row 3"));

        assertThrows(IllegalArgumentException.class, () -> GridRowCodec.decodeCell("XXX"));
        assertThrows(IllegalArgumentException.class, () -> GridRowCodec.decodeCell("FLR:XYZ"));
        assertThrows(IllegalArgumentException.class, () -> GridRowCodec.decodeCell("FLR:"));
    }
}
