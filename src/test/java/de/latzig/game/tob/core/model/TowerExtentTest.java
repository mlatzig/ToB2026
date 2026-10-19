package de.latzig.game.tob.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.sim.TowerAssets;

class TowerExtentTest {

    @Test
    void theExtentIsTheBoundingBoxOfEveryNonEmptyCellOverAllLevels() {
        TowerGrid grid = TowerGrid.builder()
                .set(5, 6, 0, new Cell(BaseType.FLOOR, null))
                .set(9, 4, 2, new Cell(BaseType.BOX, null))
                .set(4, 11, 1, new Cell(BaseType.FLOOR, ObjectType.KLD))
                .build();

        TowerExtent extent = TowerExtent.of(grid).orElseThrow();

        assertEquals(new TowerExtent(4, 4, 9, 11, 2), extent);
        assertEquals(6, extent.width());
        assertEquals(8, extent.depth());
        assertEquals(3, extent.levels());
        assertTrue(extent.withinOriginalFootprint());
        assertTrue(extent.withinOriginalVolume());
        assertEquals(Optional.empty(), TowerExtent.of(TowerGrid.empty()));
        assertTrue(TowerExtent.isOriginalSize(TowerGrid.empty()), "an empty grid counts as original size");
    }

    @Test
    void aCellOutsideColumnsAndRowsFourToElevenOrAboveLevelThreeMakesATowerLarge() {
        Cell floor = new Cell(BaseType.FLOOR, null);
        TowerGrid west = TowerGrid.builder().set(3, 7, 0, floor).set(11, 7, 0, floor).build();
        TowerGrid south = TowerGrid.builder().set(7, 12, 0, floor).build();
        TowerGrid high = TowerGrid.builder().set(7, 7, 4, floor).build();
        TowerGrid full = TowerGrid.builder().set(0, 0, 0, floor).set(15, 15, 5, floor).build();

        assertFalse(TowerExtent.of(west).orElseThrow().withinOriginalFootprint());
        assertFalse(TowerExtent.of(south).orElseThrow().withinOriginalFootprint());
        assertTrue(TowerExtent.of(high).orElseThrow().withinOriginalFootprint(), "level 4 keeps the footprint");
        assertFalse(TowerExtent.isOriginalSize(high), "but not the volume");
        assertEquals(new TowerExtent(0, 0, 15, 15, 5), TowerExtent.of(full).orElseThrow());
        assertFalse(TowerExtent.isOriginalSize(full));
    }

    @Test
    void theObjectLimitIsTwentyWithinTheOriginalAreaAndSixtyFourBeyondIt() {
        Cell floor = new Cell(BaseType.FLOOR, null);

        assertEquals(20, TowerLimits.maxObjects(TowerGrid.builder().set(4, 4, 0, floor).build()));
        assertEquals(20, TowerLimits.maxObjects(TowerGrid.empty()));
        assertEquals(64, TowerLimits.maxObjects(TowerGrid.builder().set(12, 4, 0, floor).build()));
        assertEquals(64, TowerLimits.maxObjects(TowerGrid.builder().set(4, 4, 4, floor).build()));
    }

    @Test
    void everyConvertedTowerStaysWithinTheOriginalArea() throws IOException {
        assumeTrue(TowerAssets.available(), "tower assets not generated");
        List<TowerDefinition> towers = TowerAssets.allTowers();

        assertEquals(144, towers.size());
        for (TowerDefinition tower : towers) {
            assertTrue(TowerExtent.isOriginalSize(tower.grid()), tower.id());
            assertEquals(TowerLimits.MAX_OBJECTS, TowerLimits.maxObjects(tower.grid()), tower.id());
        }
    }
}
