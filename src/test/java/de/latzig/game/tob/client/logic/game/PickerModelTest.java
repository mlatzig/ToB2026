package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.LargeTestTowers;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;

class PickerModelTest {

    @Test
    void listsTheHundredFortyFourTowersInLibraryOrder() throws IOException {
        assumeTrue(TowerAssets.available(), "converted towers not available");

        PickerModel model = PickerModel.load(TowerAssets.directory());

        assertEquals(144, model.entries().size());
        assertTrue(model.entries().get(0).label().startsWith("ORIGINAL 001  "), model.entries().get(0).label());
        assertEquals(117, model.indexOf("tutorial-000"));
        assertTrue(model.entries().get(117).label().startsWith("TUTORIAL 001  Your Spiders and the Lifts"));
        assertTrue(model.entries().get(143).label().startsWith("DEMO     018  "), model.entries().get(143).label());
        assertEquals(-1, model.indexOf("nope"));
        assertEquals(1, model.number("original-000"));
        assertEquals(117, model.number("original-116"));
        assertEquals(1, model.number("tutorial-000"), "numbered within its own library");
        assertEquals(9, model.number("tutorial-008"));
        assertEquals(0, model.number("nope"));
    }

    /** Phase 6a: a developer start's extra library follows the three libraries, labelled with its file name. */
    @Test
    void anExtraLibraryFollowsTheThreeLibraries() throws IOException {
        assumeTrue(TowerAssets.available(), "converted towers not available");

        PickerModel model = PickerModel.load(TowerAssets.directory(), Optional.of(LargeTestTowers.FILE));

        assertEquals(148, model.entries().size());
        assertEquals(144, model.indexOf(LargeTestTowers.FULL));
        assertEquals("LARGE    001  Large Test: Full 16 x 16 x 6", model.entries().get(144).label());
        assertEquals(4, model.number(LargeTestTowers.SOLVABLE), "numbered within its own library");
        assertTrue(model.entries().subList(144, 148).stream().allMatch(TowerEntry::playable),
                "64 objects are allowed beyond the 8 x 8 area");
        assertEquals(144, PickerModel.load(TowerAssets.directory(), Optional.empty()).entries().size());
    }

    @Test
    void aTowerWithProblemsIsNotPlayableButKeepsItsNumber() {
        TowerDefinition tower = TestTowers.tower().level(0, "F").build();
        PickerModel model = new PickerModel(List.of(new TowerEntry("good", 1, Optional.of(tower), List.of()),
                new TowerEntry("bad", 2, Optional.of(tower), List.of("STRUCTURE: 21 objects")),
                new TowerEntry("#2", 3, Optional.empty(), List.of("unreadable"))));

        assertEquals(List.of(true, false, false), model.entries().stream().map(TowerEntry::playable).toList());
        assertEquals(1, model.number(tower.id()), "the first entry with the id");
        assertThrows(IllegalArgumentException.class, () -> new PickerModel(List.of()));
    }
}
