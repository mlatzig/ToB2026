package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

class TowerValidatorTest {

    private static TowerDefinition withGrid(TowerGrid grid) {
        TowerDefinition t = SampleTowers.tower("v-000");
        return new TowerDefinition(t.id(), t.group(), t.index(), t.title(), t.author(), t.conditions(),
                t.behaviour(), t.effects(), t.moon(), t.palette(), t.sounds(), t.lock(), grid, t.remake());
    }

    @Test
    void acceptsTheSampleTower() {
        assertEquals(List.of(), TowerValidator.validate(SampleTowers.tower("v-000")));
    }

    @Test
    void rejectsMoreThanTwentyObjectsWithinTheOriginalArea() {
        TowerGrid.Builder builder = TowerGrid.builder();
        for (int i = 0; i < 21; i++) {
            builder.set(4 + i % 8, 4 + i / 8, 0, new Cell(BaseType.FLOOR, ObjectType.KLD));
        }

        List<String> problems = TowerValidator.validate(withGrid(builder.build()));

        assertEquals(List.of("21 objects, at most 20 allowed"), problems);
    }

    /** Phase 6a spec decision 3: a tower beyond the 8 x 8 x 4 area may hold up to 64 objects. */
    @Test
    void aLargeTowerHoldsUpToSixtyFourObjects() {
        TowerGrid.Builder builder = TowerGrid.builder();
        for (int i = 0; i < 64; i++) {
            builder.set(i % 16, i / 16, 0, new Cell(BaseType.FLOOR, ObjectType.KLD));
        }

        assertEquals(List.of(), TowerValidator.validate(withGrid(builder.build())));

        builder.set(0, 4, 0, new Cell(BaseType.FLOOR, ObjectType.KLD));

        assertEquals(List.of("65 objects, at most 64 allowed"), TowerValidator.validate(withGrid(builder.build())));
    }

    @Test
    void rejectsASpiderPlacedTwice() {
        TowerGrid grid = TowerGrid.builder()
                .set(0, 0, 0, new Cell(BaseType.FLOOR, ObjectType.ZAP))
                .set(1, 0, 0, new Cell(BaseType.FLOOR, ObjectType.ZAP))
                .build();

        List<String> problems = TowerValidator.validate(withGrid(grid));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("ZAP"));
    }
}
