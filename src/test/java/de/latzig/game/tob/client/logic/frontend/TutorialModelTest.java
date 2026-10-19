package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.Texts;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.TestTowers;

class TutorialModelTest {

    private static final Texts TEXTS = Texts.english();

    /**
     * 9 test towers {@code tutorial-000} … {@code tutorial-008} ("Tutorial 1" …), E without an author; as the real
     * tutorial, only H (1 klondike) and I (4 robots) have a goal.
     */
    static List<TowerDefinition> towers() {
        TowerDefinition base = TestTowers.tower().level(0, "FF").build();
        List<TowerDefinition> towers = new ArrayList<>();
        for (int i = 0; i < TutorialModel.TOWERS; i++) {
            Conditions conditions = i == 7 ? new Conditions(120, false, 1, 0)
                    : i == 8 ? new Conditions(120, false, 0, 4) : base.conditions();
            towers.add(new TowerDefinition(String.format("tutorial-%03d", i), 0, i, "Tutorial " + (i + 1),
                    i == 4 ? "" : "Pete Cooke", conditions, base.behaviour(), base.effects(), base.moon(),
                    base.palette(), base.sounds(), base.lock(), base.grid(), base.remake()));
        }
        return towers;
    }

    @Test
    void everyTowerIsOpenAndTheSolvedOnesAreMarked() {
        List<TowerDefinition> shuffled = new ArrayList<>(towers());
        Collections.reverse(shuffled);
        TutorialModel tutorial = new TutorialModel(shuffled, Set.of("tutorial-007", "original-000"), TEXTS);

        List<OverviewModel.Cell> cells = tutorial.cells();
        assertEquals(new OverviewModel.Cell(0, 'A', 0, 0, OverviewModel.State.OPEN, true), cells.get(0));
        assertEquals(new OverviewModel.Cell(7, 'H', 2, 1, OverviewModel.State.SOLVED, false), cells.get(7));
        assertTrue(cells.stream().noneMatch(cell -> cell.state() == OverviewModel.State.LOCKED), "decision 3");
        assertEquals("Tutorial", tutorial.caption());
        assertEquals("Solved: 1 of 9", tutorial.solvedText(), "only tutorial towers count");
        assertEquals("No profile, no score", tutorial.hintText());
        assertEquals("1) Tutorial 1 - Pete Cooke", tutorial.infoText());
        assertEquals("tutorial-000", tutorial.selectedTower().id(), "sorted by index");
    }

    @Test
    void theArrowKeysMoveWithoutWrappingAndAnEmptyAuthorIsAnonymous() {
        TutorialModel tutorial = new TutorialModel(towers(), Set.of(), TEXTS);

        assertFalse(tutorial.up());
        assertFalse(tutorial.left());
        assertTrue(tutorial.down());
        assertTrue(tutorial.right());
        assertEquals(4, tutorial.selected());
        assertEquals("5) Tutorial 5 - Anonymous", tutorial.infoText());
        assertTrue(tutorial.down());
        assertFalse(tutorial.down());
        assertTrue(tutorial.right());
        assertFalse(tutorial.right());
        assertEquals(8, tutorial.selected());
        assertFalse(tutorial.select(9));
        tutorial.preselect("tutorial-002");
        assertEquals(2, tutorial.selected());
        tutorial.preselect("original-000");
        assertEquals(2, tutorial.selected(), "not a tutorial tower: unchanged");
    }

    @Test
    void theTutorialNeedsNineTowersWithTheIndicesZeroToEight() {
        List<TowerDefinition> eight = towers().subList(0, 8);
        List<TowerDefinition> twice = new ArrayList<>(towers());
        twice.set(8, towers().get(7));

        assertThrows(IllegalArgumentException.class, () -> TutorialModel.sorted(eight));
        assertThrows(IllegalArgumentException.class, () -> TutorialModel.sorted(twice));
        assertEquals(towers(), TutorialModel.sorted(towers()));
    }
}
